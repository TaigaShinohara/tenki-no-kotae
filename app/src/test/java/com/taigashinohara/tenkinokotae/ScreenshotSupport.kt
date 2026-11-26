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
 * The app is driven deterministically: the wall clock is pinned to
 * 10:00 JST (today, or tomorrow when today's 10:00 is already past —
 * Robolectric's paused clock cannot move backward; see
 * fixedNowMillis) and SharedPreferences is seeded with a fresh
 * Open-Meteo cache entry, so onCreate shows the home screen from cache
 * at once — no network, no loading spinner, no intro staging.
 */
object ScreenshotSupport {

    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    /** Today in the app's own timezone (MainActivity.todayStr uses Asia/Tokyo). */
    fun today(): LocalDate = LocalDate.now(TOKYO)

    private var pinCache: Long = 0L

    /**
     * The frozen "now" for renders: 10:00 JST, today — or tomorrow if
     * that is already past. Robolectric's ShadowPausedSystemClock
     * refuses to move the clock BACKWARD (setCurrentTimeMillis
     * returns false and does nothing when the target is earlier than
     * the current sandbox time, which starts at the real time), so
     * the pin must always be in the future; the sandbox clock is then
     * exactly 10:00 plus a few seconds of scheduler drift. The app
     * reads only the time-of-day from the sandbox clock (sun position,
     * "from now" averages) and the date from java.time (real clock,
     * unshadowed), so pinning to tomorrow's 10:00 is safe. Computed
     * once per JVM so every test in the class pins to the same value.
     */
    fun fixedNowMillis(): Long {
        if (pinCache == 0L) {
            var t = today().atTime(10, 0).atZone(TOKYO).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            while (t <= now) t += 24L * 60 * 60 * 1000
            pinCache = t
        }
        return pinCache
    }

    /**
     * A 3-day (yesterday / today / tomorrow) Open-Meteo-shaped response:
     * a hot, clear, high-UV day. Today peaks at UV 9.2 with no rain, so
     * the home figure holds a parasol (the v0.1.33/34 rebuilt area) and
     * the conclusion leads with the parasol/sunscreen answers.
     */
    fun sunnyCacheJson(): String {
        val dates = listOf(today().minusDays(1), today(), today().plusDays(1))
        fun arr(values: List<String>): String = values.joinToString(",", "[", "]")
        val time = mutableListOf<String>()
        val temp = mutableListOf<String>()
        val app = mutableListOf<String>()
        val prob = mutableListOf<String>()
        val prec = mutableListOf<String>()
        val code = mutableListOf<String>()
        val pres = mutableListOf<String>()
        val wind = mutableListOf<String>()
        val hum = mutableListOf<String>()
        val uv = mutableListOf<String>()
        for ((d, date) in dates.withIndex()) {
            val dayOffset = if (d == 1) 0.0 else -2.0 // yesterday/tomorrow a bit cooler
            for (h in 0..23) {
                time.add("\"${date}T${"%02d".format(h)}:00\"")
                // Bell curve peaking at 31℃ around 14:00, 24℃ at night.
                val t = 27.5 + 3.5 * sin((h - 9) / 24.0 * 2 * Math.PI) + dayOffset
                temp.add("%.1f".format(t))
                app.add("%.1f".format(t + 1.0))
                prob.add("0")
                prec.add("0.0")
                code.add("0")
                pres.add((1015 + h % 3).toString())
                wind.add("2.0")
                hum.add("45")
                val u = if (h in 7..17) 9.2 * sin((h - 6) / 12.0 * Math.PI) else 0.0
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
          "precipitation_probability":${arr(prob)},
          "precipitation":${arr(prec)},"weather_code":${arr(code)},
          "pressure_msl":${arr(pres)},"wind_speed_10m":${arr(wind)},
          "relative_humidity_2m":${arr(hum)},"uv_index":${arr(uv)}},
         "daily":{
          "time":$dailyTime,
          "temperature_2m_max":[30.0,32.0,30.0],"temperature_2m_min":[22.0,24.0,22.0],
          "apparent_temperature_max":[31.0,33.0,31.0],"uv_index_max":[8.5,9.2,8.5],
          "sunrise":$sunrise,
          "sunset":$sunset}}
        """.trimIndent()
    }

    /**
     * Re-pins the sandbox wall clock to the frozen "now". Robolectric's
     * paused clock still advances as its scheduler is pumped (idles,
     * Espresso syncs), and the app bakes the sky state (sun position,
     * "from now" averages) from the wall clock to the second whenever
     * a screen is (re)shown — so the pin must land right before the
     * interaction that re-bakes the screen under test.
     */
    fun repinClock() {
        android.os.SystemClock.setCurrentTimeMillis(fixedNowMillis())
    }

    private var nightPinCache: Long = 0L

    /**
     * The frozen "night": the pin day at 20:00 — past the app's
     * NIGHT_SWITCH_HOUR (19), so Home builds its tomorrow display.
     * Derived from [fixedNowMillis] (+10 h) so it is always later
     * than the day pin (the paused clock cannot move backward) and
     * on the same sandbox date. The app's *date* still comes from
     * java.time (real clock), so the seeded cache's tomorrow is the
     * day the night screen must show.
     */
    fun fixedNightMillis(): Long {
        if (nightPinCache == 0L) {
            nightPinCache = fixedNowMillis() + 10L * 60 * 60 * 1000
        }
        return nightPinCache
    }

    /** Re-pins the sandbox wall clock to the frozen night (20:00). */
    fun repinNightClock() {
        android.os.SystemClock.setCurrentTimeMillis(fixedNightMillis())
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
    fun seedSunnyCache(context: Context) {
        val prefs = context.getSharedPreferences("tenki_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cache_大阪", sunnyCacheJson())
            .putLong("cache_at_大阪", fixedNowMillis())
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
