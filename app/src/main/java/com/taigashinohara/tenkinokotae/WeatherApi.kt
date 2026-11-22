package com.taigashinohara.tenkinokotae

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One hour of weather, local time. precipProb is null when the API has no value. */
data class HourEntry(
    val hour: Int,
    val temp: Double,
    val apparent: Double,
    val precipProb: Int?,
    val precip: Double,
    val weatherCode: Int,
    val pressure: Double,
    val wind: Double,
    val humidity: Int,
    val uv: Double
)

data class DayForecast(
    val date: String, // yyyy-MM-dd, local
    val hours: List<HourEntry>,
    val tempMax: Double,
    val tempMin: Double,
    val apparentMax: Double,
    val uvMax: Double,
    val sunrise: String,
    val sunset: String
) {
    fun hour(h: Int): HourEntry? = hours.firstOrNull { it.hour == h }
}

data class Forecast(val days: List<DayForecast>) {
    fun day(date: String): DayForecast? = days.firstOrNull { it.date == date }
}

data class City(val name: String, val lat: Double, val lon: Double)

object Cities {
    val list = listOf(
        City("大阪", 34.6937, 135.5023),
        City("東京", 35.6762, 139.6503),
        City("名古屋", 35.1815, 136.9066),
        City("仙台", 38.2682, 140.8694),
        City("札幌", 43.0618, 141.3545),
        City("福岡", 33.5904, 130.4017),
        City("広島", 34.3853, 132.4553),
        City("那覇", 26.2124, 127.6809)
    )
    fun byName(name: String): City = list.firstOrNull { it.name == name } ?: list[0]
}

/** Identifies the app to data providers (MET Norway requires a UA with contact info). */
const val APP_USER_AGENT = "TenkiNoKotae/0.1.3 (github.com/TaigaShinohara)"

/**
 * HTTP error carrying the status code, so retry logic can tell a
 * deterministic rejection (most 4xx — retrying will not change it; fall
 * back to the other source at once) from a transient failure (408, 429
 * and 5xx — worth another attempt inside the budget). The message keeps
 * the provider's human-readable reason for the error screen, and
 * [retryAfterMs] carries the provider's Retry-After hint when sent.
 */
class HttpStatusException(
    val code: Int,
    message: String,
    val retryAfterMs: Long? = null
) : RuntimeException(message)

/**
 * The load that owned this fetch was superseded by a newer generation
 * (city switch / fresh load). Not a failure: the caller discards the
 * whole load silently instead of falling back or showing an error.
 */
class FetchCancelledException : RuntimeException("取得が新しい読み込みに置き換えられました")

/** A response body grew past the hard size cap while being read. */
class ResponseTooLargeException(val limitBytes: Int) :
    IOException("応答が上限（$limitBytes バイト）を超えました")

/** Hard cap for a decoded response body (FETCH_REVIEW #1). */
internal const val MAX_BODY_BYTES = 10 * 1024 * 1024

/** Error bodies are only read far enough to extract a short reason. */
internal const val MAX_ERROR_BODY_BYTES = 8 * 1024

/** Retry-After is honored, but never beyond this wait (FETCH_REVIEW #5). */
internal const val RETRY_AFTER_CAP_MS = 10_000L

/** Wait between attempts when the provider gave no Retry-After hint. */
internal const val DEFAULT_BACKOFF_MS = 1_000L

/**
 * Reads a stream fully as UTF-8 text, but refuses to grow past
 * [maxBytes] (FETCH_REVIEW #1): an abnormal or hostile response —
 * including a gzip decompression bomb, since this runs *after* the
 * gzip wrapper — fails with [ResponseTooLargeException] instead of
 * eating the heap until the process dies. Pure helper over
 * [InputStream] so it is unit-testable without a network.
 */
internal fun readLimitedText(stream: InputStream, maxBytes: Int): String {
    val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val n = stream.read(buf)
        if (n < 0) break
        total += n
        if (total > maxBytes) throw ResponseTooLargeException(maxBytes)
        out.write(buf, 0, n)
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}

/**
 * Splits what is left of a source's budget into one attempt's
 * (connect, read) timeouts (FETCH_REVIEW #4): the pair's *sum* never
 * exceeds the remaining budget, so an attempt can no longer spend the
 * remainder twice (once connecting, once reading). Each side keeps its
 * historical per-phase cap (10 s / 25 s) and a 1 s floor; a remainder
 * under 2 s is coerced up to 2 s so a first attempt always gets a real
 * chance. Pure function of the remaining time — unit-tested.
 */
internal fun splitTimeouts(remainingMs: Long): Pair<Int, Int> {
    val rem = remainingMs.coerceAtLeast(2_000L)
    val connect = minOf(10_000L, rem - 1_000L)
    val read = minOf(25_000L, rem - connect)
    return connect.toInt() to read.toInt()
}

/**
 * True when an HTTP status is a rejection that retrying cannot change
 * (FETCH_REVIEW #5): every 4xx *except* 408 (request timeout) and 429
 * (rate limit), which are transient by definition. Non-4xx statuses
 * (5xx, stray 3xx) are retryable.
 */
internal fun isDeterministicFailure(code: Int): Boolean =
    code in 400..499 && code != 408 && code != 429

/**
 * Parses a Retry-After header value into milliseconds from now
 * (FETCH_REVIEW #5). Both standard forms are supported: delta-seconds
 * ("120") and an HTTP-date. A date in the past means "retry now" (0).
 * Unparseable or negative values yield null (no hint). Pure — the
 * caller passes the current time.
 */
internal fun parseRetryAfterMs(header: String?, nowMs: Long): Long? {
    val h = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    h.toLongOrNull()?.let { secs -> return if (secs >= 0) secs * 1_000L else null }
    return try {
        val dateMs = java.time.ZonedDateTime
            .parse(h, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli()
        (dateMs - nowMs).coerceAtLeast(0L)
    } catch (e: Exception) {
        null
    }
}

/**
 * How long to wait before the next attempt, or null to stop retrying
 * (FETCH_REVIEW #5): the provider's Retry-After hint wins and is
 * capped at [RETRY_AFTER_CAP_MS]; without a hint the short default
 * backoff applies. A wait that would consume the whole remaining
 * budget leaves nothing to retry *with*, so it ends the loop instead
 * (null) — the other source gets its turn sooner.
 */
internal fun retryWaitMs(retryAfterMs: Long?, remainingMs: Long): Long? {
    val wait = if (retryAfterMs != null) {
        minOf(retryAfterMs, RETRY_AFTER_CAP_MS)
    } else {
        DEFAULT_BACKOFF_MS
    }
    return if (wait < remainingMs) wait else null
}

/**
 * Shared plain-HTTP GET: gzip, caller-bounded timeouts, and — on HTTP
 * errors — the provider's reason (from the error body) folded into the
 * exception message so the error screen can show *why* it failed, not
 * just the code. Both the success body and the error body are read
 * through [readLimitedText] (FETCH_REVIEW #1): the decoded body is
 * capped at [MAX_BODY_BYTES] and the error body at
 * [MAX_ERROR_BODY_BYTES], so a broken or hostile response fails the
 * fetch instead of exhausting the heap. [onConnection] (when given)
 * receives the live connection before any I/O, so the caller can
 * disconnect it from another thread to abort this fetch
 * (FETCH_REVIEW #2).
 */
internal fun httpGetText(
    url: String,
    userAgent: String,
    connectTimeoutMs: Int = 10_000,
    readTimeoutMs: Int = 25_000,
    onConnection: ((HttpURLConnection) -> Unit)? = null
): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    onConnection?.invoke(conn)
    conn.connectTimeout = connectTimeoutMs
    conn.readTimeout = readTimeoutMs
    conn.requestMethod = "GET"
    conn.setRequestProperty("Accept-Encoding", "gzip")
    conn.setRequestProperty("User-Agent", userAgent)
    try {
        val code = conn.responseCode
        if (code != 200) {
            val retryAfterMs = parseRetryAfterMs(
                conn.getHeaderField("Retry-After"), System.currentTimeMillis()
            )
            val body = try {
                val es = conn.errorStream
                if (es == null) null else {
                    val stream = if (conn.contentEncoding?.contains("gzip") == true) {
                        java.util.zip.GZIPInputStream(es)
                    } else es
                    readLimitedText(stream, MAX_ERROR_BODY_BYTES)
                }
            } catch (e: Exception) {
                null
            }
            val reason = WeatherApi.extractErrorReason(body)
            throw HttpStatusException(
                code,
                if (reason != null) "HTTP $code: $reason" else "HTTP $code",
                retryAfterMs
            )
        }
        val raw = if (conn.contentEncoding?.contains("gzip") == true) {
            java.util.zip.GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }
        return readLimitedText(raw, MAX_BODY_BYTES)
    } finally {
        conn.disconnect()
    }
}

/**
 * Retry-with-deadline fetch shared by both sources (BUG_REVIEW #7,
 * hardened by FETCH_REVIEW #4/#5/#2): each source gets a wall-clock
 * budget and every attempt's (connect + read) timeouts are split from
 * what is left of it by [splitTimeouts], so one attempt can no longer
 * spend the remainder twice — primary ~35 s + backup ~20 s keeps the
 * all-sources-failed path at ~55 s of network time plus parsing.
 * A deterministic HTTP failure ([isDeterministicFailure]) ends the
 * loop at once (retrying a rejection changes nothing; the other
 * source should get its turn), while 408/429 stay retryable and a
 * Retry-After hint is honored via [retryWaitMs] — every wait is
 * charged to the same budget. [isCancelled] is polled between
 * attempts (and the caller aborts the in-flight attempt itself by
 * disconnecting its connection, see [httpGetText]); a cancelled fetch
 * surfaces as [FetchCancelledException], never as a source failure.
 * [registerConnection] is handed to each attempt so the caller can
 * track the live connection per load generation.
 */
internal fun fetchWithBudget(
    budgetMs: Long,
    maxAttempts: Int,
    isCancelled: () -> Boolean = { false },
    registerConnection: (HttpURLConnection) -> Unit = {},
    attempt: (connectMs: Int, readMs: Int, register: (HttpURLConnection) -> Unit) -> String
): String {
    val start = System.currentTimeMillis()
    fun elapsed() = System.currentTimeMillis() - start
    var lastError: Exception? = null
    var n = 0
    while (n < maxAttempts) {
        if (isCancelled()) throw FetchCancelledException()
        n++
        val remaining = budgetMs - elapsed()
        if (n > 1 && remaining <= 0) break
        val (connectMs, readMs) = splitTimeouts(remaining)
        try {
            return attempt(connectMs, readMs, registerConnection)
        } catch (e: FetchCancelledException) {
            throw e
        } catch (e: HttpStatusException) {
            lastError = e
            if (isCancelled()) throw FetchCancelledException()
            if (isDeterministicFailure(e.code)) break
            if (n >= maxAttempts) break
            val wait = retryWaitMs(e.retryAfterMs, budgetMs - elapsed()) ?: break
            if (wait > 0) Thread.sleep(wait)
        } catch (e: Exception) {
            lastError = e
            if (isCancelled()) throw FetchCancelledException()
            if (n >= maxAttempts) break
            val wait = retryWaitMs(null, budgetMs - elapsed()) ?: break
            if (wait > 0) Thread.sleep(wait)
        }
    }
    if (isCancelled()) throw FetchCancelledException()
    throw lastError ?: RuntimeException("取得に失敗しました")
}

/** Open-Meteo Forecast API access (no key needed). Plain HttpURLConnection + org.json. */
object WeatherApi {

    fun fetch(city: City): Forecast = parse(JSONObject(fetchText(city)))

    /**
     * Pulls the human-readable reason out of an error body.
     * Open-Meteo returns {"error":true,"reason":"..."}; anything else JSON
     * yields null, a short plain-text body is used as-is.
     */
    fun extractErrorReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val r = JSONObject(body).optString("reason", "")
            if (r.isNotEmpty()) r else null
        } catch (e: Exception) {
            val s = body.trim()
            if (s.length in 1..120 && !s.contains("<")) s else null
        }
    }

    /**
     * Raw JSON fetch with retry under a wall-clock budget (see
     * [fetchWithBudget]): the free Open-Meteo endpoint is sometimes slow
     * on a cold (uncached) request — v0.1.0 timed out at 15 s on a real
     * device — so attempts keep generous timeouts, but the *total* wait
     * for this source is capped at ~35 s and a deterministic 4xx (the
     * 403 this app actually met) stops retrying at once so the backup
     * source gets its turn.
     */
    fun fetchText(
        city: City,
        isCancelled: () -> Boolean = { false },
        registerConnection: (HttpURLConnection) -> Unit = {}
    ): String {
        val tz = URLEncoder.encode("Asia/Tokyo", "UTF-8")
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${city.lat}&longitude=${city.lon}" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability," +
            "precipitation,weather_code,pressure_msl,wind_speed_10m," +
            "relative_humidity_2m,uv_index" +
            "&daily=temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
            "uv_index_max,sunrise,sunset" +
            "&past_days=1&forecast_days=3&timezone=$tz"
        return fetchWithBudget(
            budgetMs = 35_000L, maxAttempts = 3,
            isCancelled = isCancelled, registerConnection = registerConnection
        ) { c, r, reg ->
            httpGetText(url, APP_USER_AGENT, c, r, reg)
        }
    }

    fun parse(root: JSONObject): Forecast {
        val hourly = root.getJSONObject("hourly")
        val hTime = hourly.getJSONArray("time")
        val hTemp = hourly.getJSONArray("temperature_2m")
        val hApp = hourly.getJSONArray("apparent_temperature")
        val hProb = hourly.getJSONArray("precipitation_probability")
        val hPrec = hourly.getJSONArray("precipitation")
        val hCode = hourly.getJSONArray("weather_code")
        val hPres = hourly.getJSONArray("pressure_msl")
        val hWind = hourly.getJSONArray("wind_speed_10m")
        val hHum = hourly.getJSONArray("relative_humidity_2m")
        val hUv = hourly.getJSONArray("uv_index")

        val hoursByDate = linkedMapOf<String, MutableList<HourEntry>>()
        for (i in 0 until hTime.length()) {
            val t = hTime.getString(i) // "2026-11-18T09:00"
            val date = t.substring(0, 10)
            val hour = t.substring(11, 13).toInt()
            val entry = HourEntry(
                hour = hour,
                temp = hTemp.optDouble(i, 0.0),
                apparent = hApp.optDouble(i, 0.0),
                precipProb = if (hProb.isNull(i)) null else hProb.optDouble(i, 0.0).toInt(),
                precip = hPrec.optDouble(i, 0.0),
                weatherCode = hCode.optInt(i, 0),
                pressure = hPres.optDouble(i, 1013.0),
                wind = hWind.optDouble(i, 0.0),
                humidity = hHum.optDouble(i, 0.0).toInt(),
                uv = hUv.optDouble(i, 0.0)
            )
            hoursByDate.getOrPut(date) { mutableListOf() }.add(entry)
        }

        val daily = root.getJSONObject("daily")
        val dTime = daily.getJSONArray("time")
        val dTMax = daily.getJSONArray("temperature_2m_max")
        val dTMin = daily.getJSONArray("temperature_2m_min")
        val dAMax = daily.getJSONArray("apparent_temperature_max")
        val dUvMax = daily.getJSONArray("uv_index_max")
        val dSunrise = daily.getJSONArray("sunrise")
        val dSunset = daily.getJSONArray("sunset")

        val days = mutableListOf<DayForecast>()
        for (i in 0 until dTime.length()) {
            val date = dTime.getString(i)
            days.add(
                DayForecast(
                    date = date,
                    hours = hoursByDate[date] ?: emptyList(),
                    tempMax = dTMax.optDouble(i, 0.0),
                    tempMin = dTMin.optDouble(i, 0.0),
                    apparentMax = dAMax.optDouble(i, 0.0),
                    uvMax = dUvMax.optDouble(i, 0.0),
                    sunrise = dSunrise.optString(i, ""),
                    sunset = dSunset.optString(i, "")
                )
            )
        }
        return Forecast(days)
    }
}
