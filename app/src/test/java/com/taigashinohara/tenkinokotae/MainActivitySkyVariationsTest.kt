package com.taigashinohara.tenkinokotae

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.SystemClock

/**
 * Screenshot tests for the v0.1.38 weather-cast skies: the home
 * screen under an overcast, a rainy and a snowy day, all at the
 * same frozen 10:00 as the sunny home golden (MainActivityScreenshot
 * Test's home.png is the clear-day member of the set, and its
 * home_night.png the clear night). Only the seeded cache differs
 * per test — the palette cast, the cloud/rain/snow layers and the
 * day's answers all follow the fixture's weather code.
 *
 * Kept in its own class so the shared sandbox clock never has to
 * move backward between the weather variants (every test here pins
 * the same 10:00).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivitySkyVariationsTest {

    @Before
    fun setUp() {
        SystemClock.setCurrentTimeMillis(ScreenshotSupport.fixedNowMillis())
        val app = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    private fun launchWith(seed: (android.content.Context) -> Unit): MainActivity {
        seed(RuntimeEnvironment.getApplication())
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        ScreenshotSupport.repinClock()
        // Re-selecting the home tab re-bakes the screen with the
        // pinned clock (the settle re-render used the drifted clock).
        val home = ScreenshotSupport.findByContentDescription(activity.window.decorView, "ホーム")
        assertNotNull("home tab not found", home)
        home!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun capture(activity: MainActivity, golden: String) {
        ScreenshotSupport.repinClock()
        val decor = activity.window.decorView
        ScreenshotSupport.allViews(decor).filterIsInstance<SkyView>().forEach {
            it.clockMillis = ScreenshotSupport::fixedNowMillis
        }
        val bitmap = ScreenshotSupport.drawToBitmap(decor)
        ScreenshotSupport.assertNotBlank(bitmap, golden)
        ScreenshotSupport.captureGolden(bitmap, golden)
    }

    @Test
    fun home_cloudy() {
        val activity = launchWith { ScreenshotSupport.seedCloudyCache(it) }
        capture(activity, "home_cloudy.png")
    }

    @Test
    fun home_rain() {
        val activity = launchWith { ScreenshotSupport.seedRainCache(it) }
        capture(activity, "home_rain.png")
    }

    @Test
    fun home_snow() {
        val activity = launchWith { ScreenshotSupport.seedSnowCache(it) }
        capture(activity, "home_snow.png")
    }
}
