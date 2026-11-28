package com.taigashinohara.tenkinokotae

import android.graphics.Bitmap
import android.os.Looper
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

/**
 * Screenshot tests for the v0.1.41 scene decision — the sky's
 * light/dark scene comes from the time and the weather, never from
 * the device's dark-mode setting (the user's decision after their
 * real-device screenshot showed a midnight sky at 14:29 in the
 * rain on a dark-mode phone).
 *
 * The two captures reproduce that report and its mirror image:
 *  - dark uiMode + 14:29 + rain must render the LIGHT rainy sky;
 *  - light uiMode + 20:00 + rain must render the DARK rainy sky
 *    (and its rain streaks must stay readable — checked by eye on
 *    the evidence copy, like every sky variation).
 * Evidence copies land in build/outputs/sky-scene/ next to the
 * goldens, which lock the behavior in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivitySkySceneTest {

    @Before
    fun setUp() {
        ScreenshotSupport.repinClock()
        val app = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    private fun launchWith(
        seed: (android.content.Context) -> Unit,
        repin: () -> Unit
    ): MainActivity {
        seed(RuntimeEnvironment.getApplication())
        repin()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        repin()
        // Re-selecting the home tab re-bakes the screen with the
        // pinned clock (the settle re-render used the drifted clock).
        val home = ScreenshotSupport.findByContentDescription(activity.window.decorView, "ホーム")
        assertNotNull("home tab not found", home)
        home!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    /** WCAG relative luminance of a bitmap pixel. */
    private fun lumAt(bmp: Bitmap, fx: Float, fy: Float): Double {
        val c = bmp.getPixel((bmp.width * fx).toInt(), (bmp.height * fy).toInt())
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(android.graphics.Color.red(c)) +
            0.7152 * lin(android.graphics.Color.green(c)) +
            0.0722 * lin(android.graphics.Color.blue(c))
    }

    private fun capture(
        activity: MainActivity, golden: String, evidence: String,
        skyClock: () -> Long
    ): Bitmap {
        val decor = activity.window.decorView
        ScreenshotSupport.allViews(decor).filterIsInstance<SkyView>().forEach {
            it.clockMillis = skyClock
        }
        val bitmap = ScreenshotSupport.drawToBitmap(decor)
        ScreenshotSupport.assertNotBlank(bitmap, golden)
        val outDir = File("build/outputs/sky-scene").apply { mkdirs() }
        File(outDir, evidence).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        ScreenshotSupport.captureGolden(bitmap, golden)
        return bitmap
    }

    /**
     * The user's report, reproduced: a dark-mode phone at 14:29 in
     * the rain. The scene must be the light rainy sky — the top of
     * the sky is a bright grey-blue, not midnight navy.
     */
    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
    fun darkUiMode_daytimeRain_showsLightSky() {
        val activity = launchWith(
            { ScreenshotSupport.seedRainCache(it) },
            { ScreenshotSupport.repinAfternoonClock() }
        )
        ScreenshotSupport.repinAfternoonClock()
        val bmp = capture(
            activity, "home_scene_darkui_rain.png", "scene_darkui_day_rain.png",
            ScreenshotSupport::fixedAfternoonMillis
        )
        val topLum = listOf(0.25f, 0.5f, 0.75f).map { lumAt(bmp, it, 0.05f) }.average()
        assertTrue(
            "dark uiMode at 14:29 must show the light sky (top luminance $topLum)",
            topLum > 0.25
        )
    }

    /**
     * Evidence only (no golden): the settings screen in the dark
     * scene, captured for the v0.1.42 glass-material unification —
     * the section cards, segments and city chips at night must read
     * as the same glass material as their day forms, dyed navy.
     * Checked by eye on the evidence copy (docs/FEATURE_v0.1.42.md).
     */
    @Test
    fun nightSettings_evidence() {
        val activity = launchWith(
            { ScreenshotSupport.seedSunnyCache(it) },
            { ScreenshotSupport.repinNightClock() }
        )
        ScreenshotSupport.repinNightClock()
        val settings = ScreenshotSupport.findByContentDescription(
            activity.window.decorView, "設定"
        )
        assertNotNull("settings tab not found", settings)
        settings!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        ScreenshotSupport.repinNightClock()
        val decor = activity.window.decorView
        ScreenshotSupport.allViews(decor).filterIsInstance<SkyView>().forEach {
            it.clockMillis = ScreenshotSupport::fixedNightMillis
        }
        val bitmap = ScreenshotSupport.drawToBitmap(decor)
        ScreenshotSupport.assertNotBlank(bitmap, "scene_settings_night.png")
        val outDir = File("build/outputs/sky-scene").apply { mkdirs() }
        File(outDir, "scene_settings_night.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * The mirror image: a light-mode phone at 20:00 in the rain.
     * The scene must be the dark rainy sky.
     */
    @Test
    fun lightUiMode_nightRain_showsDarkSky() {
        val activity = launchWith(
            { ScreenshotSupport.seedRainCache(it) },
            { ScreenshotSupport.repinNightClock() }
        )
        ScreenshotSupport.repinNightClock()
        val bmp = capture(
            activity, "home_scene_night_rain.png", "scene_lightui_night_rain.png",
            ScreenshotSupport::fixedNightMillis
        )
        val topLum = listOf(0.25f, 0.5f, 0.75f).map { lumAt(bmp, it, 0.05f) }.average()
        assertTrue(
            "light uiMode at 20:00 must show the dark sky (top luminance $topLum)",
            topLum < 0.10
        )
    }
}
