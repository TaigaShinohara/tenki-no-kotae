package com.taigashinohara.tenkinokotae

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Robolectric screen-transition tests: tab navigation must swap the
 * content screen each time, and no screen may collapse to zero height
 * (the layout-collapse failure mode the JVM verification standard
 * exists to catch before delivery).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MainActivityNavigationTest {

    @Before
    fun setUp() {
        SystemClock.setCurrentTimeMillis(ScreenshotSupport.fixedNowMillis())
        val app = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(
            app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f
        )
        ScreenshotSupport.seedSunnyCache(app)
    }

    private fun clickTab(activity: MainActivity, name: String) {
        val tab = ScreenshotSupport.findByContentDescription(activity.window.decorView, name)
        assertNotNull("tab $name not found", tab)
        tab!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertLaidOut(activity: MainActivity, what: String) {
        val decor = activity.window.decorView
        assertTrue("$what: decorView has no height", decor.height > 0)
        val figure = ScreenshotSupport.allViews(decor)
            .firstOrNull { it is PersonView }
        if (figure != null) {
            assertTrue("$what: PersonView collapsed to zero height", figure.height > 0)
        }
    }

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        ScreenshotSupport.awaitSettled(activity)
        return activity
    }

    @Test
    fun tabs_switchScreens_andBack() {
        val activity = launch()
        val decor = activity.window.decorView

        // Home: the figure is the home marker.
        assertTrue(
            "home should host the figure",
            ScreenshotSupport.hasInstance(decor, PersonView::class.java)
        )
        assertLaidOut(activity, "home")

        // Detail: 体感温度 hero + UV最大 stat, no figure.
        clickTab(activity, "数値で見る")
        assertNotNull(
            ScreenshotSupport.findByContentDescription(decor, "体感温度")
        )
        assertNotNull(ScreenshotSupport.findByText(decor, "UV最大"))
        assertNull(
            "detail should not host the home figure",
            ScreenshotSupport.allViews(decor).firstOrNull { it is PersonView }
        )
        assertLaidOut(activity, "detail")

        // Settings: 地点 section.
        clickTab(activity, "設定")
        assertNotNull(ScreenshotSupport.findByText(decor, "地点"))
        assertLaidOut(activity, "settings")

        // Back home: figure returns; re-tapping the same tab is stable.
        clickTab(activity, "ホーム")
        assertTrue(
            "home again should host the figure",
            ScreenshotSupport.hasInstance(decor, PersonView::class.java)
        )
        clickTab(activity, "ホーム")
        assertTrue(
            "home re-tap should keep the figure",
            ScreenshotSupport.hasInstance(decor, PersonView::class.java)
        )
        assertLaidOut(activity, "home again")
    }

    @Test
    fun settings_personTest_andBack() {
        val activity = launch()
        clickTab(activity, "設定")
        val open = ScreenshotSupport.findByText(activity.window.decorView, "見た目テストを開く")
        assertNotNull(open)
        open!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "person test should host the figure",
            ScreenshotSupport.hasInstance(activity.window.decorView, PersonView::class.java)
        )
        val decor = activity.window.decorView
        assertNotNull(ScreenshotSupport.findByText(decor, "ニット帽"))
        ScreenshotSupport.findByText(decor, "雨")!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(ScreenshotSupport.findByText(decor, "レインコートのフードをかぶります"))
        assertNull(ScreenshotSupport.findByText(decor, "ニット帽"))
        ScreenshotSupport.findByText(decor, "3")!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(ScreenshotSupport.findByText(decor, "ニット帽"))
        val back = ScreenshotSupport.findByText(activity.window.decorView, "設定にもどる")
        assertNotNull("person test should offer a way back", back)
        back!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(
            "back should return to settings",
            ScreenshotSupport.findByText(activity.window.decorView, "地点")
        )
    }
}
