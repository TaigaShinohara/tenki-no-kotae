package com.taigashinohara.tenkinokotae

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp

/**
 * MET Norway Locationforecast 2.0 (complete) — the backup data source.
 * Used only when Open-Meteo fails completely: some home/carrier networks
 * are rejected by the free Open-Meteo endpoint (HTTP 403) while this
 * provider answers normally.
 *
 * Differences from Open-Meteo that this adapter absorbs:
 * - Timeseries is in UTC -> converted to Asia/Tokyo days/hours.
 * - No past data (forecast only) -> no "yesterday"; Rules has a separate
 *   conclusion path for that, and the app may borrow yesterday from a
 *   cached Open-Meteo response.
 * - No precipitation probability for this region -> precipProb is null
 *   and Rules.umbrella falls back to amount-only judgement.
 * - No sunrise/sunset -> empty strings (the detail screen guards them).
 * - No apparent temperature -> computed with the BOM/Steadman formula.
 * - Beyond ~2.5 days the series is 6-hourly; next_6_hours precipitation
 *   is a 6-hour total, stored prorated per hour (only today/tomorrow —
 *   always hourly — feed the umbrella rule).
 *
 * Weather data: MET Norway (CC BY 4.0). A User-Agent identifying the app
 * is required by their terms of service.
 */
object MetApi {

    private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    fun fetch(city: City): Forecast = parse(JSONObject(fetchText(city)))

    fun fetchText(
        city: City,
        isCancelled: () -> Boolean = { false },
        registerConnection: (java.net.HttpURLConnection) -> Unit = {}
    ): String {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/complete" +
            "?lat=${city.lat}&lon=${city.lon}"
        // Same deadline discipline as the primary source (fetchWithBudget):
        // ~20 s total for this source, a deterministic 4xx ends retrying
        // at once (408/429 stay retryable, FETCH_REVIEW #5).
        return fetchWithBudget(
            budgetMs = 20_000L, maxAttempts = 2,
            isCancelled = isCancelled, registerConnection = registerConnection
        ) { c, r, reg ->
            httpGetText(url, APP_USER_AGENT, c, r, reg)
        }
    }

    /** BOM (Australian Bureau of Meteorology) apparent temperature, Steadman formula. */
    fun apparentTemperature(tempC: Double, humidityPct: Double, windSpeed: Double): Double {
        val e = (humidityPct / 100.0) * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return tempC + 0.33 * e - 0.70 * windSpeed - 4.00
    }

    /**
     * Maps a MET symbol_code ("rain", "clearsky_day", "heavyrainshowers_night", …)
     * onto the WMO code space Open-Meteo uses, so the rain / snow / thunder /
     * clear / cloudy classification is preserved. Sleet (rain+snow mix) has no
     * exact WMO counterpart and is approximated with 67.
     */
    fun symbolToWeatherCode(symbol: String): Int {
        val base = symbol
            .substringBefore("_day")
            .substringBefore("_night")
            .substringBefore("_polartwilight")
        if (base.contains("thunder")) return if (base.contains("heavy")) 96 else 95
        return when (base) {
            "clearsky" -> 0
            "fair" -> 1
            "partlycloudy" -> 2
            "cloudy" -> 3
            "fog" -> 45
            "lightrainshowers" -> 80
            "rainshowers" -> 81
            "heavyrainshowers" -> 82
            "lightsnowshowers" -> 85
            "snowshowers", "heavysnowshowers" -> 86
            "lightrain" -> 61
            "rain" -> 63
            "heavyrain" -> 65
            "lightsleet", "sleet", "heavysleet",
            "lightsleetshowers", "sleetshowers", "heavysleetshowers" -> 67
            "lightsnow" -> 71
            "snow" -> 73
            "heavysnow" -> 75
            else -> 3
        }
    }

    fun parse(root: JSONObject): Forecast {
        val ts = root.getJSONObject("properties").getJSONArray("timeseries")
        val hoursByDate = linkedMapOf<String, MutableList<HourEntry>>()
        for (i in 0 until ts.length()) {
            val item = ts.getJSONObject(i)
            val zdt = Instant.parse(item.getString("time")).atZone(TOKYO)
            val data = item.getJSONObject("data")
            val det = data.getJSONObject("instant").getJSONObject("details")

            val temp = det.optDouble("air_temperature", 0.0)
            val humidity = det.optDouble("relative_humidity", 50.0)
            val wind = det.optDouble("wind_speed", 0.0)

            val n1 = data.optJSONObject("next_1_hours")
            val n6 = data.optJSONObject("next_6_hours")
            val n12 = data.optJSONObject("next_12_hours")
            val precip = when {
                n1 != null ->
                    n1.optJSONObject("details")?.optDouble("precipitation_amount", 0.0) ?: 0.0
                n6 != null ->
                    (n6.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                        ?: 0.0) / 6.0
                else -> 0.0
            }
            val symbol = n1?.optJSONObject("summary")?.optString("symbol_code")
                ?: n6?.optJSONObject("summary")?.optString("symbol_code")
                ?: n12?.optJSONObject("summary")?.optString("symbol_code")
                ?: ""

            val entry = HourEntry(
                hour = zdt.hour,
                temp = temp,
                apparent = apparentTemperature(temp, humidity, wind),
                precipProb = null,
                precip = precip,
                weatherCode = symbolToWeatherCode(symbol),
                pressure = det.optDouble("air_pressure_at_sea_level", 1013.0),
                wind = wind,
                humidity = humidity.toInt(),
                uv = det.optDouble("ultraviolet_index_clear_sky", 0.0)
            )
            hoursByDate.getOrPut(zdt.toLocalDate().toString()) { mutableListOf() }.add(entry)
        }

        val days = hoursByDate.map { (date, hours) ->
            val sorted = hours.sortedBy { it.hour }
            DayForecast(
                date = date,
                hours = sorted,
                tempMax = sorted.maxOfOrNull { it.temp } ?: 0.0,
                tempMin = sorted.minOfOrNull { it.temp } ?: 0.0,
                apparentMax = sorted.maxOfOrNull { it.apparent } ?: 0.0,
                uvMax = sorted.maxOfOrNull { it.uv } ?: 0.0,
                sunrise = "",
                sunset = ""
            )
        }
        return Forecast(days)
    }
}
