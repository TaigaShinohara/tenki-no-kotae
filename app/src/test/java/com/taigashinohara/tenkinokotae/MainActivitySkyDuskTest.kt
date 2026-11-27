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

/**
 * Screenshot test for the dusk sky (v0.1.38 variation set): the
 * sunny fixture at the frozen 17:50 — inside the sunset warm bump,
 * ahead of the 19:00 night-mode switch, so Home still shows today
 * under an orange-leaning horizon. Its own class so the dusk pin
 * (17:50) is applied in isolation from the day (10:00) and night
 * (20:00) pins of the other screen classes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivitySkyDuskTest {

    @Before
    fun setUp() {
        ScreenshotSupport.repinClock()
        val app = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    @Test
    fun home_dusk() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        ScreenshotSupport.repinDuskClock()
        val decor = activity.window.decorView
        val home = ScreenshotSupport.findByContentDescription(decor, "ホーム")
        assertNotNull("home tab not found", home)
        home!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.allViews(decor).filterIsInstance<SkyView>().forEach {
            it.clockMillis = ScreenshotSupport::fixedDuskMillis
        }
        val bitmap = ScreenshotSupport.drawToBitmap(decor)
        ScreenshotSupport.assertNotBlank(bitmap, "home_dusk.png")
        ScreenshotSupport.captureGolden(bitmap, "home_dusk.png")
    }
}
