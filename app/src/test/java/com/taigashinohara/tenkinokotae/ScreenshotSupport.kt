package com.taigashinohara.tenkinokotae

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.sin

/**
 * Shared support for the Robolectric + Roborazzi screen tests (the
 * no-emulator pre-delivery verification standard, 2026-11-23 —
 * docs/VERIFICATION_SETUP.md).
 *
 * The app is driven deterministically: "now" is a fixed calendar
 * instant (see BASE_DATE / fixedNowMillis) fed to the app through
 * its MainActivity.nowMillis seam — Robolectric's own clock controls
 * (SystemClock, the scheduler) do not reach the app's
 * System.currentTimeMillis / LocalDate.now reads in this
 * environment (measured; docs/FIX_TEST_CLOCK.md) — and
 * SharedPreferences is seeded with a fresh Open-Meteo cache entry,
 * so onCreate shows the home screen from cache at once — no
 * network, no loading spinner, no intro staging.
 */
object ScreenshotSupport {

    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    /**
     * The one fixed calendar date the whole suite lives on. A fixed
     * date (not "today") is what makes the suite date-independent:
     * the seeded cache, the app's today/tomorrow selection and the
     * dated 「明日」 label in the night golden all derive from it.
     */
    val BASE_DATE: LocalDate = LocalDate.of(2026, 11, 25)

    /** The suite's "today": always [BASE_DATE]. */
    fun today(): LocalDate = BASE_DATE

    private var pinCache: Long = 0L

    /**
     * The frozen "now" for renders: [BASE_DATE] at 10:00 JST. Fed to
     * the app through the MainActivity.nowMillis seam (see
     * repinClock); the legacy SystemClock pin is still issued for
     * the sandbox scheduler's sake but is a no-op for a past instant
     * and no longer carries any assertion. Computed once per JVM so
     * every test pins to the same value.
     */
    fun fixedNowMillis(): Long {
        if (pinCache == 0L) {
            pinCache = BASE_DATE.atTime(10, 0).atZone(TOKYO).toInstant().toEpochMilli()
        }
        return pinCache
    }

    /**
     * A 3-day (yesterday / today / tomorrow) Open-Meteo-shaped response:
     * a hot, clear, high-UV day. Today peaks at UV 9.2 with no rain, so
     * the home figure holds a parasol (the v0.1.33/34 rebuilt area) and
     * the conclusion leads with the parasol/sunscreen answers.
     */
    fun sunnyCacheJson(): String = weatherCacheJson(
        code = 0, prob = 0, prec = 0.0,
        base = 27.5, amp = 3.5, uvPeak = 9.2,
        dailyMax = listOf(30.0, 32.0, 30.0), dailyMin = listOf(22.0, 24.0, 22.0),
        dailyUv = listOf(8.5, 9.2, 8.5)
    )

    /** Overcast all day (code 3), mild — the v0.1.38 cloudy sky cast. */
    fun cloudyCacheJson(): String = weatherCacheJson(
        code = 3, prob = 20, prec = 0.0,
        base = 19.0, amp = 3.0, uvPeak = 3.0,
        dailyMax = listOf(21.0, 22.0, 21.0), dailyMin = listOf(15.0, 16.0, 15.0),
        dailyUv = listOf(2.6, 3.0, 2.6)
    )

    /** Steady rain all day (code 63) — the v0.1.38 rain sky cast. */
    fun rainCacheJson(): String = weatherCacheJson(
        code = 63, prob = 80, prec = 2.5,
        base = 11.0, amp = 2.5, uvPeak = 0.5,
        dailyMax = listOf(12.0, 13.0, 12.0), dailyMin = listOf(8.0, 9.0, 8.0),
        dailyUv = listOf(0.4, 0.5, 0.4)
    )

    /** Snow all day (code 73), around freezing — the v0.1.38 snow cast. */
    fun snowCacheJson(): String = weatherCacheJson(
        code = 73, prob = 70, prec = 1.2,
        base = -0.5, amp = 1.8, uvPeak = 0.3,
        dailyMax = listOf(0.0, 1.0, 0.0), dailyMin = listOf(-4.0, -3.0, -4.0),
        dailyUv = listOf(0.2, 0.3, 0.2)
    )

    /**
     * The shared fixture builder behind the weather caches: one
     * weather code all day, a sine temperature bell peaking around
     * 14:00, sunrise 06:00 / sunset 18:10 on all three days.
     */
    fun weatherCacheJson(
        code: Int, prob: Int, prec: Double,
        base: Double, amp: Double, uvPeak: Double,
        dailyMax: List<Double>, dailyMin: List<Double>, dailyUv: List<Double>
    ): String {
        val dates = listOf(today().minusDays(1), today(), today().plusDays(1))
        fun arr(values: List<String>): String = values.joinToString(",", "[", "]")
        fun darr(values: List<Double>): String =
            values.joinToString(",", "[", "]") { "%.1f".format(it) }
        val time = mutableListOf<String>()
        val temp = mutableListOf<String>()
        val app = mutableListOf<String>()
        val probL = mutableListOf<String>()
        val precL = mutableListOf<String>()
        val codeL = mutableListOf<String>()
        val pres = mutableListOf<String>()
        val wind = mutableListOf<String>()
        val hum = mutableListOf<String>()
        val uv = mutableListOf<String>()
        for ((d, date) in dates.withIndex()) {
            val dayOffset = if (d == 1) 0.0 else -2.0 // yesterday/tomorrow a bit cooler
            for (h in 0..23) {
                time.add("\"${date}T${"%02d".format(h)}:00\"")
                // Bell curve peaking around 14:00.
                val t = base + amp * sin((h - 9) / 24.0 * 2 * Math.PI) + dayOffset
                temp.add("%.1f".format(t))
                app.add("%.1f".format(t + 1.0))
                probL.add(prob.toString())
                precL.add("%.1f".format(prec))
                codeL.add(code.toString())
                pres.add((1015 + h % 3).toString())
                wind.add("2.0")
                hum.add("45")
                val u = if (h in 7..17) uvPeak * sin((h - 6) / 12.0 * Math.PI) else 0.0
                uv.add("%.1f".format(max(0.0, u)))
            }
        }
        val dailyTime = dates.joinToString(",", "[", "]") { "\"$it\"" }
        val sunrise = dates.joinToString(",", "[", "]") { "\"${it}T06:00\"" }
        val sunset = dates.joinToString(",", "[", "]") { "\"${it}T18:10\"" }
        return """
        {"hourly":{
          "time":${arr(time)},"temperature_2m":${arr(temp)},
          "apparent_temperature":${arr(app)},
          "precipitation_probability":${arr(probL)},
          "precipitation":${arr(precL)},"weather_code":${arr(codeL)},
          "pressure_msl":${arr(pres)},"wind_speed_10m":${arr(wind)},
          "relative_humidity_2m":${arr(hum)},"uv_index":${arr(uv)}},
         "daily":{
          "time":$dailyTime,
          "temperature_2m_max":${darr(dailyMax)},"temperature_2m_min":${darr(dailyMin)},
          "apparent_temperature_max":${darr(dailyMax.map { it + 1.0 })},"uv_index_max":${darr(dailyUv)},
          "sunrise":$sunrise,
          "sunset":$sunset}}
        """.trimIndent()
    }

    /**
     * Re-pins "now" to the frozen day instant, through the app's
     * MainActivity.nowMillis seam (the SystemClock call is kept for
     * the sandbox scheduler, as before). The app bakes the screen
     * state (night-mode switch, "from now" averages) from "now"
     * whenever a screen is (re)shown — so the pin must land right
     * before the interaction that re-bakes the screen under test.
     */
    fun repinClock() {
        MainActivity.nowMillis = { fixedNowMillis() }
        android.os.SystemClock.setCurrentTimeMillis(fixedNowMillis())
    }

    private var nightPinCache: Long = 0L

    /**
     * The frozen "night": [BASE_DATE] at 20:00 — past the app's
     * NIGHT_SWITCH_HOUR (19), so Home builds its tomorrow display.
     * Derived from [fixedNowMillis] (+10 h), on the same fixed date;
     * through the nowMillis seam the app's date moves with it, so
     * the seeded cache's tomorrow (BASE_DATE + 1) is the day the
     * night screen shows.
     */
    fun fixedNightMillis(): Long {
        if (nightPinCache == 0L) {
            nightPinCache = fixedNowMillis() + 10L * 60 * 60 * 1000
        }
        return nightPinCache
    }

    /** Re-pins "now" to the frozen night (20:00). */
    fun repinNightClock() {
        MainActivity.nowMillis = { fixedNightMillis() }
        android.os.SystemClock.setCurrentTimeMillis(fixedNightMillis())
    }

    private var duskPinCache: Long = 0L

    /**
     * The frozen "dusk": the pin day at 17:50 — twenty minutes
     * before the fixture sunset (18:10), inside the warm bump and
     * still ahead of the 19:00 night-mode switch, so Home shows
     * today under a sunset sky. Derived from [fixedNowMillis]
     * (+7h50m) so it is always later than the day pin.
     */
    fun fixedDuskMillis(): Long {
        if (duskPinCache == 0L) {
            duskPinCache = fixedNowMillis() + (7L * 60 + 50) * 60 * 1000
        }
        return duskPinCache
    }

    /** Re-pins "now" to the frozen dusk (17:50). */
    fun repinDuskClock() {
        MainActivity.nowMillis = { fixedDuskMillis() }
        android.os.SystemClock.setCurrentTimeMillis(fixedDuskMillis())
    }

    private var afternoonPinCache: Long = 0L

    /**
     * The frozen "afternoon": [BASE_DATE] at 14:29 — the exact
     * minute of the user's v0.1.41 real-device screenshot (a
     * dark-mode phone showing a midnight sky in daytime rain).
     * Derived from [fixedNowMillis] (+4h29m) on the same fixed date.
     */
    fun fixedAfternoonMillis(): Long {
        if (afternoonPinCache == 0L) {
            afternoonPinCache = fixedNowMillis() + (4L * 60 + 29) * 60 * 1000
        }
        return afternoonPinCache
    }

    /** Re-pins "now" to the frozen afternoon (14:29). */
    fun repinAfternoonClock() {
        MainActivity.nowMillis = { fixedAfternoonMillis() }
        android.os.SystemClock.setCurrentTimeMillis(fixedAfternoonMillis())
    }

    /**
     * Waits until the activity's background weather fetch has settled
     * (its failure/success re-render included). The fetch runs on real
     * threads and posts its result to the main looper; if it lands
     * mid-test it re-renders the current tab and can clobber a screen
     * the test just navigated to. Polls the private loadInFlight flag
     * while pumping the main looper so the posted result can apply.
     */
    fun awaitSettled(activity: MainActivity, timeoutMs: Long = 120_000) {
        val field = MainActivity::class.java.getDeclaredField("loadInFlight")
        field.isAccessible = true
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (!field.getBoolean(activity)) return
            if (System.nanoTime() > deadline) {
                println("awaitSettled: fetch still in flight after ${timeoutMs}ms; continuing")
                return
            }
            Thread.sleep(100)
        }
    }

    /** Seeds the cache MainActivity.loadWeather reads first (city 大阪）. */
    fun seedSunnyCache(context: Context) = seedCache(context, sunnyCacheJson())

    fun seedCloudyCache(context: Context) = seedCache(context, cloudyCacheJson())

    fun seedRainCache(context: Context) = seedCache(context, rainCacheJson())

    fun seedSnowCache(context: Context) = seedCache(context, snowCacheJson())

    private fun seedCache(context: Context, json: String) {
        val prefs = context.getSharedPreferences("tenki_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cache_大阪", json)
            // The stamp uses the REAL clock on purpose: the app's
            // cache-freshness checks read the real clock (they are
            // not part of the nowMillis seam), so a frozen stamp
            // would age past the 24 h freshness window.
            .putLong("cache_at_大阪", System.currentTimeMillis())
            .commit()
    }

    /**
     * Golden image location: app/src/test/roborazzi in the repo. The
     * VM's manual runner passes -Dtenki.golden.dir with an absolute
     * path; Gradle runs default to the module-relative location.
     */
    fun goldenFile(name: String): File {
        val prop = System.getProperty("tenki.golden.dir")
        val dir = when {
            prop != null -> File(prop)
            File("app/src/test/roborazzi").isDirectory -> File("app/src/test/roborazzi")
            else -> File("src/test/roborazzi")
        }
        dir.mkdirs()
        return File(dir, name)
    }

    /**
     * Golden capture/compare for the full-activity screens.
     *
     * Why not Roborazzi's exact verify (which the figure tests use)?
     * SkyView positions the sun/moon from the wall clock at draw time,
     * to the second. Robolectric's paused clock cannot be pinned
     * exactly: setCurrentTimeMillis cannot move it backward at all,
     * and it keeps drifting forward by seconds as the scheduler is
     * pumped (both measured in this environment). The sun's wide glow
     * gradient therefore resamples with per-channel deltas of ~1
     * (max ~13) inside the sky band on every run — pixel-exact
     * comparison of these four screens can never pass, no matter how
     * the clock is managed.
     *
     * So this compare tolerates ONLY that measured wobble:
     *  - any single-pixel delta above 40 fails;
     *  - pixels with delta above 4 must be at most 3% of the image
     *    and must all lie in the top sky band (y < 30% of height);
     *  - pixels with any delta at all must be at most 5%.
     * Everything below the sky band — text, figure, chips, tabs —
     * is effectively exact (delta <= 4), and a real breakage (a
     * collapsed or missing view, changed text) produces deltas of
     * 100+ over wide areas and fails. On failure the actual image
     * and a side-by-side compare are written to
     * app/build/outputs/roborazzi/ for inspection.
     *
     * Record mode follows the same switch as Roborazzi
     * (-Droborazzi.test.record=true, set by tools/run-unit-tests.sh).
     */
    fun captureGolden(bitmap: Bitmap, name: String) {
        val golden = goldenFile(name)
        if (System.getProperty("roborazzi.test.record") == "true") {
            golden.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        org.junit.Assert.assertTrue("golden not recorded yet: ${golden.path}", golden.isFile)
        val expected = android.graphics.BitmapFactory.decodeFile(golden.path)
        org.junit.Assert.assertNotNull("golden undecodable: ${golden.path}", expected)
        org.junit.Assert.assertEquals("golden width differs", expected.width, bitmap.width)
        org.junit.Assert.assertEquals("golden height differs", expected.height, bitmap.height)
        val w = bitmap.width
        val h = bitmap.height
        val skyBand = (h * 0.30f).toInt()
        var anyDelta = 0
        var strongDelta = 0
        var strongBelowSky = 0
        var maxDelta = 0
        var firstStrongBelow: String? = null
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = expected.getPixel(x, y)
                val b = bitmap.getPixel(x, y)
                if (a == b) continue
                val d = max(
                    max(
                        kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                        kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b))
                    ),
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                )
                anyDelta++
                if (d > maxDelta) maxDelta = d
                if (d > 4) {
                    strongDelta++
                    if (y >= skyBand) {
                        strongBelowSky++
                        if (firstStrongBelow == null) firstStrongBelow = "($x,$y) delta=$d"
                    }
                }
            }
        }
        val total = w * h
        val problems = mutableListOf<String>()
        if (maxDelta > 40) problems += "max pixel delta $maxDelta > 40"
        if (strongDelta > total * 0.03) problems += "pixels with delta>4: $strongDelta > 3% of $total"
        if (strongBelowSky > 0) problems += "$strongBelowSky pixels with delta>4 below the sky band, first at $firstStrongBelow"
        if (anyDelta > total * 0.05) problems += "pixels with any delta: $anyDelta > 5% of $total"
        if (problems.isNotEmpty()) {
            val outDir = File("build/outputs/roborazzi").apply { mkdirs() }
            val base = name.removeSuffix(".png")
            File(outDir, "${base}_actual.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val compare = Bitmap.createBitmap(w * 2, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(compare)
            canvas.drawBitmap(expected, 0f, 0f, null)
            canvas.drawBitmap(bitmap, w.toFloat(), 0f, null)
            File(outDir, "${base}_compare.png").outputStream().use {
                compare.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            org.junit.Assert.fail(
                "golden $name differs beyond the sky-wobble tolerance: " +
                    problems.joinToString("; ") +
                    " (anyDelta=$anyDelta, strongDelta=$strongDelta, maxDelta=$maxDelta;" +
                    " see build/outputs/roborazzi/${base}_compare.png)"
            )
        }
    }

    // ---------- view tree helpers ----------

    fun allViews(root: View): List<View> {
        val out = mutableListOf<View>()
        fun walk(v: View) {
            out.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    fun textViews(root: View): List<TextView> = allViews(root).filterIsInstance<TextView>()

    fun findByText(root: View, text: String): TextView? =
        textViews(root).firstOrNull { it.text?.toString() == text }

    fun findByContentDescription(root: View, desc: String): View? =
        allViews(root).firstOrNull { it.contentDescription?.toString() == desc }

    fun hasInstance(root: View, cls: Class<*>): Boolean =
        allViews(root).any { cls.isInstance(it) }

    // ---------- bitmap checks ----------

    /** Draws a laid-out view into a fresh bitmap (native graphics). */
    fun drawToBitmap(view: View): Bitmap {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    /**
     * The Migakeru guard: a screen that collapsed to zero height or
     * rendered as a single flat colour fails here even before the
     * golden comparison runs. Samples a grid and requires real colour
     * variety plus a meaningful share of non-background pixels.
     */
    fun assertNotBlank(bitmap: Bitmap, what: String) {
        assertTrue("$what: bitmap has no size", bitmap.width > 0 && bitmap.height > 0)
        val colors = HashMap<Int, Int>()
        var total = 0
        val stepX = max(1, bitmap.width / 60)
        val stepY = max(1, bitmap.height / 60)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                val c = bitmap.getPixel(x, y)
                colors[c] = (colors[c] ?: 0) + 1
                total++
                y += stepY
            }
            x += stepX
        }
        val dominant = colors.values.maxOrNull() ?: 0
        assertTrue(
            "$what: looks blank/flat — ${colors.size} distinct colours, " +
                "dominant covers ${100 * dominant / max(1, total)}%",
            colors.size >= 24 && dominant < total * 0.97
        )
    }
}
