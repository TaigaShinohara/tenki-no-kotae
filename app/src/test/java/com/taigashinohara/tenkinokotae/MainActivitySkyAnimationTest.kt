package com.taigashinohara.tenkinokotae

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Animation tests for the v0.1.39 breathing sky. The production
 * frame loop never runs here (the animator duration scale is 0, so
 * SkyView itself takes the reduced-motion still path); instead each
 * frame is rendered by pinning SkyView.animTimeSec — possible
 * because every moving element is a pure function of that clock.
 * Two frames 0.6 s apart must differ where the particles live, and
 * the frames are also written to build/outputs/sky-anim/ as visual
 * evidence (rain/snow in motion, the breathing sun, twinkling
 * stars) for human inspection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivitySkyAnimationTest {

    @Before
    fun setUp() {
        ScreenshotSupport.repinClock()
        val app = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    private fun launchWith(seed: (Context) -> Unit, night: Boolean = false): MainActivity {
        seed(RuntimeEnvironment.getApplication())
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        if (night) ScreenshotSupport.repinNightClock() else ScreenshotSupport.repinClock()
        val home = ScreenshotSupport.findByContentDescription(activity.window.decorView, "ホーム")
        assertNotNull("home tab not found", home)
        home!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    /** Renders one frame with the animation clock pinned at [tSec]. */
    private fun frameAt(activity: MainActivity, tSec: Float, name: String, night: Boolean = false): Bitmap {
        if (night) ScreenshotSupport.repinNightClock() else ScreenshotSupport.repinClock()
        val decor = activity.window.decorView
        ScreenshotSupport.allViews(decor).filterIsInstance<SkyView>().forEach {
            it.clockMillis =
                if (night) ScreenshotSupport::fixedNightMillis
                else ScreenshotSupport::fixedNowMillis
            it.animTimeSec = tSec
        }
        val bitmap = ScreenshotSupport.drawToBitmap(decor)
        ScreenshotSupport.assertNotBlank(bitmap, name)
        val dir = File("build/outputs/sky-anim").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** Pixels whose strongest channel moved by more than [delta], in the top [yFrac] of the image. */
    private fun diffCount(a: Bitmap, b: Bitmap, delta: Int, yFrac: Float = 1f): Int {
        var n = 0
        val yMax = (a.height * yFrac).toInt()
        for (y in 0 until yMax) {
            for (x in 0 until a.width) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                if (pa == pb) continue
                val d = maxOf(
                    abs(Color.red(pa) - Color.red(pb)),
                    abs(Color.green(pa) - Color.green(pb)),
                    abs(Color.blue(pa) - Color.blue(pb))
                )
                if (d > delta) n++
            }
        }
        return n
    }

    @Test
    fun rain_twoFramesMove() {
        val activity = launchWith({ ScreenshotSupport.seedRainCache(it) })
        val a = frameAt(activity, 1.0f, "anim_rain_t1.png")
        val b = frameAt(activity, 1.6f, "anim_rain_t2.png")
        val moved = diffCount(a, b, delta = 8)
        assertTrue("rain should visibly move between frames (diff=$moved)", moved > 1000)
    }

    @Test
    fun snow_twoFramesMove() {
        val activity = launchWith({ ScreenshotSupport.seedSnowCache(it) })
        val a = frameAt(activity, 1.0f, "anim_snow_t1.png")
        val b = frameAt(activity, 1.6f, "anim_snow_t2.png")
        val moved = diffCount(a, b, delta = 8)
        assertTrue("snow should visibly move between frames (diff=$moved)", moved > 500)
    }

    @Test
    fun clear_sunGlowBreathes() {
        val activity = launchWith({ ScreenshotSupport.seedSunnyCache(it) })
        // t=0 is the breath's peak (factor 1.0); t=3.5 s is its
        // trough (factor 0.90) — the sun's glow must have changed.
        val a = frameAt(activity, 0.0f, "anim_sun_t1.png")
        val b = frameAt(activity, 3.5f, "anim_sun_t2.png")
        val moved = diffCount(a, b, delta = 2, yFrac = 0.35f)
        assertTrue("the sun's glow should breathe (diff=$moved)", moved > 200)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
    fun night_starsTwinkle() {
        val activity = launchWith({ ScreenshotSupport.seedSunnyCache(it) }, night = true)
        val a = frameAt(activity, 0.0f, "anim_night_t1.png", night = true)
        val b = frameAt(activity, 3.0f, "anim_night_t2.png", night = true)
        val moved = diffCount(a, b, delta = 6, yFrac = 0.40f)
        assertTrue("stars should twinkle between frames (diff=$moved)", moved > 100)
    }

    @Test
    fun settings_skyMotionToggle() {
        val activity = launchWith({ ScreenshotSupport.seedSunnyCache(it) })
        val decor = activity.window.decorView
        val tab = ScreenshotSupport.findByContentDescription(decor, "設定")
        assertNotNull("settings tab not found", tab)
        ScreenshotSupport.repinClock()
        tab!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(
            "settings should carry the 空の動き section",
            ScreenshotSupport.findByText(decor, "空の動き")
        )
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("tenki_prefs", Context.MODE_PRIVATE)
        assertTrue("sky motion defaults on", prefs.getBoolean("skymotion", true))
        val off = ScreenshotSupport.findByContentDescription(decor, "オフ")
        assertNotNull("オフ segment not found", off)
        off!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("tapping オフ must persist skymotion=false", false, prefs.getBoolean("skymotion", true))
        val on = ScreenshotSupport.findByContentDescription(decor, "オン")
        assertNotNull("オン segment not found", on)
        on!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("tapping オン must persist skymotion=true", true, prefs.getBoolean("skymotion", false))
    }
}
