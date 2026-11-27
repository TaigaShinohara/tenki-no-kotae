package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.1.38: the weather-code -> sky-group mapping shared by the app
 * palette cast and the widget face choice, and the widget's
 * phase x wet face selection. Robolectric runner because the
 * snapshot data class touches android.graphics.Color defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkyGroupTest {

    @Test
    fun groupForCode_coversTheWmoTable() {
        assertEquals(SkyGroup.CLEAR, skyGroupForCode(0))
        assertEquals(SkyGroup.PARTLY, skyGroupForCode(1))
        assertEquals(SkyGroup.PARTLY, skyGroupForCode(2))
        assertEquals(SkyGroup.CLOUDY, skyGroupForCode(3))
        assertEquals(SkyGroup.FOG, skyGroupForCode(45))
        assertEquals(SkyGroup.FOG, skyGroupForCode(48))
        assertEquals(SkyGroup.RAIN, skyGroupForCode(51))
        assertEquals(SkyGroup.RAIN, skyGroupForCode(61))
        assertEquals(SkyGroup.RAIN, skyGroupForCode(65))
        assertEquals(SkyGroup.RAIN, skyGroupForCode(80))
        assertEquals(SkyGroup.RAIN, skyGroupForCode(82))
        assertEquals(SkyGroup.SNOW, skyGroupForCode(71))
        assertEquals(SkyGroup.SNOW, skyGroupForCode(75))
        assertEquals(SkyGroup.SNOW, skyGroupForCode(85))
        assertEquals(SkyGroup.THUNDER, skyGroupForCode(95))
        assertEquals(SkyGroup.THUNDER, skyGroupForCode(99))
        // Unknown / missing codes never invent weather.
        assertEquals(SkyGroup.CLEAR, skyGroupForCode(-1))
    }

    private fun snapshot(code: Int, sunrise: Int = 360, sunset: Int = 1080) =
        WidgetSnapshot.Data(
            city = "大阪", headline = "x", clothing = "x", umbrella = "x",
            sunscreen = "x", rank = 3, umbrellaLevel = "NONE", uvMax = 0.0,
            accentDark = 0, weatherCode = code,
            sunriseMin = sunrise, sunsetMin = sunset, updatedAt = 0L
        )

    @Test
    fun widgetFace_dayAndNightBySunriseSunset() {
        val clear = snapshot(0)
        assertEquals(
            WidgetFace.DAY,
            TenkiWidgetProvider.faceFor(clear, 720)
        )
        assertEquals(
            WidgetFace.NIGHT,
            TenkiWidgetProvider.faceFor(clear, 1200)
        )
        // The boundaries are the sunrise / sunset minutes themselves.
        assertEquals(
            WidgetFace.DAY,
            TenkiWidgetProvider.faceFor(clear, 360)
        )
        assertEquals(
            WidgetFace.NIGHT,
            TenkiWidgetProvider.faceFor(clear, 1080)
        )
    }

    @Test
    fun widgetFace_wetGroupsGetTheWetFaces() {
        for (code in listOf(61, 73, 95, 45)) {
            assertEquals(
                "code $code day", WidgetFace.DAY_WET,
                TenkiWidgetProvider.faceFor(snapshot(code), 720)
            )
            assertEquals(
                "code $code night", WidgetFace.NIGHT_WET,
                TenkiWidgetProvider.faceFor(snapshot(code), 1200)
            )
        }
        // Cloudy is not wet: it keeps the clear faces.
        assertEquals(
            WidgetFace.DAY,
            TenkiWidgetProvider.faceFor(snapshot(3), 720)
        )
    }
}
