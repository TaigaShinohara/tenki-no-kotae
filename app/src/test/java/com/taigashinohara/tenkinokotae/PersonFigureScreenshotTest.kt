package com.taigashinohara.tenkinokotae

import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the 「今日の人」 figure itself, focused on the
 * umbrella area rebuilt in v0.1.32–v0.1.34 (rain umbrella, parasol,
 * raincoat, uchiwa). Each case renders a [PersonView] at a fixed size
 * with native graphics, checks the bitmap is not blank, and compares
 * against the golden in src/test/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PersonFigureScreenshotTest {

    private fun render(spec: PersonSpec, golden: String) {
        val view = PersonView(RuntimeEnvironment.getApplication(), spec)
        val w = 720
        val h = 1080
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        val bitmap = ScreenshotSupport.drawToBitmap(view)
        ScreenshotSupport.assertNotBlank(bitmap, golden)
        bitmap.captureRoboImage(ScreenshotSupport.goldenFile(golden))
    }

    @Test
    fun parasol_rank2_cardigan() {
        // The combination that fell back to the broken prop until v0.1.33.
        val spec = PersonFigure.spec(2, Rules.UmbrellaLevel.NONE, uvMax = 9.0)
        assertEquals(PersonHeld.PARASOL, spec.held)
        render(spec, "figure_parasol_rank2.png")
    }

    @Test
    fun parasol_rank5_portrait() {
        val spec = PersonFigure.spec(5, Rules.UmbrellaLevel.NONE, uvMax = 9.0)
        assertEquals(PersonHeld.PARASOL, spec.held)
        assertEquals(PersonBase.RANK5_PORTRAIT, spec.base)
        render(spec, "figure_parasol_rank5.png")
    }

    @Test
    fun rainUmbrella_open_rank1() {
        val spec = PersonFigure.spec(1, Rules.UmbrellaLevel.NEED, uvMax = 1.0)
        assertEquals(PersonHeld.OPEN, spec.held)
        render(spec, "figure_rain_rank1.png")
    }

    @Test
    fun raincoat_rank3() {
        // Ranks 2..4 on a 傘がいる day wear the raincoat outfit.
        val spec = PersonFigure.spec(3, Rules.UmbrellaLevel.NEED, uvMax = 1.0)
        assertEquals(PersonBase.RAINCOAT, spec.base)
        assertEquals(PersonHeld.OPEN, spec.held)
        render(spec, "figure_raincoat_rank3.png")
    }

    @Test
    fun uchiwa_rank0() {
        // The program-drawn fan rebuilt in v0.1.31.
        val spec = PersonFigure.spec(0, Rules.UmbrellaLevel.NONE, uvMax = 3.0)
        assertEquals(PersonHeld.UCHIWA, spec.held)
        render(spec, "figure_uchiwa_rank0.png")
    }
}
