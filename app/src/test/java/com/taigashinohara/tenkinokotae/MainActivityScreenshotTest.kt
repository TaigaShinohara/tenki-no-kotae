package com.taigashinohara.tenkinokotae

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
import android.os.SystemClock

/**
 * Screenshot tests for the main screens (Robolectric + Roborazzi,
 * native graphics) — the no-emulator pre-delivery standard
 * (docs/VERIFICATION_SETUP.md). Goldens live in src/test/roborazzi;
 * record with -Droborazzi.test.record=true, verify (default in the
 * runner) with -Droborazzi.test.verify=true.
 *
 * Coverage: the first screen after launch (home, from a seeded cache),
 * the detail and settings tabs, and the 見た目テスト screen that hosts
 * the 「今日の人」 figure. This app has no onboarding flow, so the
 * launch home screen is the entry screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivityScreenshotTest {

    @Before
    fun setUp() {
        SystemClock.setCurrentTimeMillis(ScreenshotSupport.fixedNowMillis())
        val app = RuntimeEnvironment.getApplication()
        // Freeze the living sky: with the animator scale at 0 the app
        // itself treats the sky as a still picture (SkyView's
        // reduced-motion path), so goldens don't depend on how many
        // choreographer frames happened to run before the capture.
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        // Let the background fetch settle (and apply its re-render)
        // before interacting, so it cannot clobber a later screen.
        ScreenshotSupport.awaitSettled(activity)
        ScreenshotSupport.repinClock()
        return activity
    }

    /**
     * Captures the decor view as a bitmap golden. The bitmap is drawn
     * directly on this thread (no Espresso sync pumping the scheduler
     * clock mid-capture), and compared with ScreenshotSupport's
     * sky-tolerant golden compare — SkyView draws the sun/moon from
     * the wall clock at draw time, and Robolectric's clock cannot be
     * held to the second (see ScreenshotSupport.captureGolden).
     */
    private fun capture(activity: MainActivity, golden: String) {
        ScreenshotSupport.repinClock()
        val bitmap = ScreenshotSupport.drawToBitmap(activity.window.decorView)
        ScreenshotSupport.assertNotBlank(bitmap, golden)
        ScreenshotSupport.captureGolden(bitmap, golden)
    }

    private fun clickTab(activity: MainActivity, name: String) {
        // Re-pin BEFORE the click: showScreen re-bakes the sky state
        // (sun position, "from now" averages) from the wall clock, and
        // Robolectric's paused clock drifts while its scheduler is
        // pumped — the pin must be the last thing before the re-bake.
        ScreenshotSupport.repinClock()
        val tab = ScreenshotSupport.findByContentDescription(activity.window.decorView, name)
        assertNotNull("tab $name not found", tab)
        tab!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun home_firstScreenAfterLaunch() {
        val activity = launch()
        // Re-selecting the home tab re-bakes the screen with the
        // pinned clock (the settle re-render used the drifted clock).
        clickTab(activity, "ホーム")
        val decor = activity.window.decorView
        // The seeded sunny day puts the parasol figure on the home screen.
        assertTrue(
            "home should host the PersonView figure",
            ScreenshotSupport.hasInstance(decor, PersonView::class.java)
        )
        capture(activity, "home.png")
    }

    @Test
    fun detail_numbersTab() {
        val activity = launch()
        clickTab(activity, "数値で見る")
        assertNotNull(
            "detail should show the UV最大 stat",
            ScreenshotSupport.findByText(activity.window.decorView, "UV最大")
        )
        capture(activity, "detail.png")
    }

    @Test
    fun settings_tab() {
        val activity = launch()
        clickTab(activity, "設定")
        assertNotNull(
            "settings should show the 地点 section",
            ScreenshotSupport.findByText(activity.window.decorView, "地点")
        )
        capture(activity, "settings.png")
    }

    @Test
    fun personTest_figurePlayground() {
        val activity = launch()
        clickTab(activity, "設定")
        val open = ScreenshotSupport.findByText(activity.window.decorView, "見た目テストを開く")
        assertNotNull("person-test entry button not found", open)
        ScreenshotSupport.repinClock() // showPersonTest re-bakes the sky too
        open!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "person test should host the PersonView figure",
            ScreenshotSupport.hasInstance(activity.window.decorView, PersonView::class.java)
        )
        capture(activity, "person_test.png")
    }
}
