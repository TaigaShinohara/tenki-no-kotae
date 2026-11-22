package com.taigashinohara.tenkinokotae

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.roundToInt

/**
 * 「今日の人」: a person wearing today's answer, so the way to go out
 * is visible the moment the app opens — before a single word is read.
 * The user asked for it directly: 「開いた瞬間どんなふうに出かけたら
 * いいか分かるように人型のアイコンを置きたい。寒そうだったり、暖か
 * そうだったり」.
 *
 * History: v0.1.14 introduced the figure as a line drawing; v0.1.15
 * redrew it around per-rank silhouettes; v0.1.16 replaced the drawing
 * with generated picture assets composited by [PersonComposer] — one
 * rank portrait as the base, the day's props laid over it. v0.1.18
 * added [PersonFigure.heightDp], sizing the figure from the room the
 * day's fixed parts leave in the Home column.
 *
 * v0.1.19 makes the person a real layered composite, as the user
 * asked (「パーツごとに作ることで組み合わせれるように工夫して」):
 * three bare bodies and seven tops were generated in one shared
 * canvas, keyed by tools/key_parts.py, and are realigned by
 * [PersonCanvas] (each part's alpha bbox in that canvas) with a
 * per-top calibration in [PersonCanvas.topCal] — the generated tops
 * are product-shot proportions, longer than a worn garment, so two
 * of them are vertically compressed to their worn length. The
 * adoption was decided per rank from Python composites of the same
 * math (tools/preview_parts.py; the record is docs/FEATURE_v0.1.19.md):
 * ranks 0–4 and 6 layer body + top; rank 5 keeps its v0.1.16 portrait
 * as a hybrid base (the winter-coat top's proportions fit no body —
 * scaled by width it runs head to toe, scaled by height the body's
 * arms spill out), with the new props laid over it. The raincoat is
 * a full-body replacement outfit of its own. New weather-driven
 * parts join the old ones: knit cap (snow days), sunglasses and a
 * parasol (UV 8+), a fan (the hottest rank), a neck towel (extreme
 * heat), a mask (cold + dry days) — the thresholds live in
 * [PersonFigure] next to the older ones, and the sweat / shiver
 * signs, no longer baked into a portrait, are drawn in code again
 * (they were, in the line-drawing era). A 見た目テスト screen in
 * Settings overrides every slot by hand against the same composer.
 *
 * What did not change: the variation is still decided by the pure
 * function [PersonFigure.spec] (unit-tested without any drawing),
 * and the clothing rank / umbrella / UV-hat thresholds are the
 * v0.1.14 ones.
 */
enum class PersonGarment {
    TEE_SHORTS, SHIRT, CARDIGAN, SWEATER_OUTER, COAT, COAT_SCARF, DOWN_GLOVES
}

enum class PersonUmbrella { NONE, FOLDED, OPEN }

/** The base the composer builds on (v0.1.19). */
enum class PersonBase { BODY_A, BODY_B, BODY_C, RANK5_PORTRAIT, RAINCOAT }

/** A top laid over a body (v0.1.19); null on the portrait / raincoat bases. */
enum class PersonTop { TSHIRT, SHIRT, CARDIGAN, LIGHT_OUTER, KNIT_COAT, DOWN }

enum class PersonHead { NONE, CAP, KNITCAP }

enum class PersonNeck { NONE, MUFFLER, TOWEL }

/** What the right hand holds (v0.1.19). The rain umbrella always wins. */
enum class PersonHeld { NONE, OPEN, FOLDED, PARASOL, UCHIWA }

/** The generated picture assets the compositor can lay down. */
enum class PersonArt {
    RANK0, RANK1, RANK2, RANK3, RANK4, RANK5, RANK6,
    UMBRELLA_OPEN, UMBRELLA_FOLDING, HAT,
    BODY_A, BODY_B, BODY_C,
    TOP_TSHIRT, TOP_SHIRT, TOP_CARDIGAN, TOP_LIGHTOUTER, TOP_KNITCOAT, TOP_DOWN,
    OUTFIT_RAINCOAT,
    PROP_CAP, PROP_KNITCAP, PROP_MASK, PROP_SUNGLASSES, PROP_MUFFLER,
    PROP_MITTEN_L, PROP_MITTEN_R, PROP_TOWEL, PROP_PARASOL, PROP_UCHIWA,
    PROP_FOLDING
}

data class PersonSpec(
    val garment: PersonGarment,
    /** The rain-umbrella summary kept from v0.1.16: OPEN / FOLDED when
     * the day says so, NONE otherwise (a parasol or fan is not an
     * umbrella answer). The full held state is [held]. */
    val umbrella: PersonUmbrella,
    /** Kept from v0.1.16: true exactly when [head] is CAP. */
    val hat: Boolean,
    val shiver: Boolean,
    val sweat: Boolean,
    // ---- v0.1.19 slots ----
    val base: PersonBase,
    val top: PersonTop?,
    val head: PersonHead,
    val neck: PersonNeck,
    val mask: Boolean,
    val sunglasses: Boolean,
    val mittens: Boolean,
    val held: PersonHeld
)

object PersonFigure {

    /** UV level at which sunscreen becomes 必須 (Rules.sunscreen) — the hat day. */
    const val HAT_UV = 6.0

    /** UV level for sunglasses — and, with no rain umbrella, the parasol. */
    const val SUNGLASSES_UV = 8.0
    const val PARASOL_UV = 8.0

    /** Neck towel: the hottest rank on a day whose daytime (9–18時)
     * average feel is at least this hot. */
    const val TOWEL_FEEL = 33.0

    /** Mask: a cold rank (this or colder) on a dry day (daytime
     * average humidity below [MASK_HUMIDITY]). */
    const val MASK_RANK = 4
    const val MASK_HUMIDITY = 40.0

    /** The raincoat outfit replaces the rank's clothes on a
     * 傘がいる day in these ranks (the mid-season ones). */
    val RAINCOAT_RANKS = 2..4

    /** WMO weather codes that mean snow (the same set SkyView's
     * weather table uses for its snow layer). */
    val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)

    fun garmentOf(rank: Int): PersonGarment = when (rank.coerceIn(0, 6)) {
        0 -> PersonGarment.TEE_SHORTS
        1 -> PersonGarment.SHIRT
        2 -> PersonGarment.CARDIGAN
        3 -> PersonGarment.SWEATER_OUTER
        4 -> PersonGarment.COAT
        5 -> PersonGarment.COAT_SCARF
        else -> PersonGarment.DOWN_GLOVES
    }

    /**
     * The layered outfit for a rank: which base, which top over it.
     * Rank 5 is the hybrid (its v0.1.16 portrait, no top); the
     * raincoat replaces the outfit wholesale on its days.
     */
    fun outfitFor(rank: Int, raincoat: Boolean): Pair<PersonBase, PersonTop?> {
        if (raincoat) return PersonBase.RAINCOAT to null
        return when (rank.coerceIn(0, 6)) {
            0 -> PersonBase.BODY_B to PersonTop.TSHIRT
            1 -> PersonBase.BODY_A to PersonTop.SHIRT
            2 -> PersonBase.BODY_A to PersonTop.CARDIGAN
            3 -> PersonBase.BODY_A to PersonTop.LIGHT_OUTER
            4 -> PersonBase.BODY_A to PersonTop.KNIT_COAT
            5 -> PersonBase.RANK5_PORTRAIT to null
            else -> PersonBase.BODY_C to PersonTop.DOWN
        }
    }

    /**
     * rank: Rules clothing rank 0 (hottest) .. 6 (coldest), already
     * including the feel offset and the wind step. Out-of-range ranks
     * are clamped, never an error.
     *
     * The v0.1.19 inputs beyond the v0.1.14 three: [snow] (any hour of
     * the day carries a snow weather code), [daytimeFeelAvg] and
     * [daytimeHumidityAvg] (the 9–18時 averages, null when the day
     * has no such hours — a rule whose input is absent simply never
     * fires, it does not guess).
     */
    fun spec(
        rank: Int,
        umbrellaLevel: Rules.UmbrellaLevel,
        uvMax: Double,
        snow: Boolean = false,
        daytimeFeelAvg: Double? = null,
        daytimeHumidityAvg: Double? = null
    ): PersonSpec {
        val r = rank.coerceIn(0, 6)
        val raincoat = umbrellaLevel == Rules.UmbrellaLevel.NEED && r in RAINCOAT_RANKS
        val (base, top) = outfitFor(r, raincoat)
        val head = when {
            snow -> PersonHead.KNITCAP
            uvMax >= HAT_UV -> PersonHead.CAP
            else -> PersonHead.NONE
        }
        val held = when (umbrellaLevel) {
            Rules.UmbrellaLevel.NEED -> PersonHeld.OPEN
            Rules.UmbrellaLevel.FOLDING -> PersonHeld.FOLDED
            Rules.UmbrellaLevel.NONE -> when {
                uvMax >= PARASOL_UV -> PersonHeld.PARASOL
                r == 0 -> PersonHeld.UCHIWA
                else -> PersonHeld.NONE
            }
        }
        val neck = when {
            r >= 5 -> PersonNeck.MUFFLER
            r == 0 && daytimeFeelAvg != null && daytimeFeelAvg >= TOWEL_FEEL ->
                PersonNeck.TOWEL
            else -> PersonNeck.NONE
        }
        return PersonSpec(
            garment = garmentOf(r),
            umbrella = when (umbrellaLevel) {
                Rules.UmbrellaLevel.NEED -> PersonUmbrella.OPEN
                Rules.UmbrellaLevel.FOLDING -> PersonUmbrella.FOLDED
                Rules.UmbrellaLevel.NONE -> PersonUmbrella.NONE
            },
            hat = head == PersonHead.CAP,
            shiver = r >= 5,
            sweat = r == 0,
            base = base,
            top = top,
            head = head,
            neck = neck,
            mask = r >= MASK_RANK && daytimeHumidityAvg != null &&
                daytimeHumidityAvg < MASK_HUMIDITY,
            sunglasses = uvMax >= SUNGLASSES_UV,
            mittens = r == 6,
            held = held
        )
    }

    /** The base portrait for a garment (the generated rank image). */
    fun art(garment: PersonGarment): PersonArt = when (garment) {
        PersonGarment.TEE_SHORTS -> PersonArt.RANK0
        PersonGarment.SHIRT -> PersonArt.RANK1
        PersonGarment.CARDIGAN -> PersonArt.RANK2
        PersonGarment.SWEATER_OUTER -> PersonArt.RANK3
        PersonGarment.COAT -> PersonArt.RANK4
        PersonGarment.COAT_SCARF -> PersonArt.RANK5
        PersonGarment.DOWN_GLOVES -> PersonArt.RANK6
    }

    /**
     * How much headroom above the person the composition needs, as a
     * fraction of the person's height: the parasol's canopy rides
     * well above the head, the knit cap pokes a little past the
     * hair. [PersonComposer] shrinks the person into the given rect
     * by this much; callers sizing a view for a known person height
     * multiply by (1 + padTopRatio).
     */
    fun padTopRatio(spec: PersonSpec): Float = when {
        spec.held == PersonHeld.PARASOL -> 0.34f
        spec.head == PersonHead.KNITCAP -> 0.04f
        else -> 0f
    }

    // ---- Home size of the person (v0.1.18) ----

    /** Share of the column's leftover height the person takes. */
    const val HEIGHT_SHARE = 0.60f

    /** The person's height band (dp): the share result is clamped
     * into it, so short columns still get a present figure and tall
     * ones do not grow the person without bound. */
    const val HEIGHT_MIN_DP = 150
    const val HEIGHT_MAX_DP = 230

    /** Free space (dp) the fit cap always leaves in the column, so
     * the fixed parts' small measurement slack (font padding and
     * the like) is absorbed by the spacers, never by clipping the
     * last small line. */
    const val HEIGHT_RESERVE_DP = 12f

    /**
     * Height (dp) of 「今日の人」 on Home, sized from the room the
     * day's fixed parts leave in the column (v0.1.18; before, three
     * fixed steps 148/128/112 by screen height). [columnDp] is the
     * height the Home stack must fit; [fixedDp] is the summed height
     * of everything else that day — both in dp, computed by the
     * caller from the same constants the views are built with.
     *
     * The person takes [HEIGHT_SHARE] of what is left, clamped to
     * [HEIGHT_MIN_DP]..[HEIGHT_MAX_DP]; whatever remains after the
     * person is split evenly above and below it by the two weighted
     * spacers, so the figure floats at the center of the gap between
     * the conclusion and the chips instead of hugging the top. When
     * the leftover is too small for the minimum, the fit cap wins
     * over the minimum: the person shrinks to the leftover minus
     * [HEIGHT_RESERVE_DP], and the stack still fits. Only when
     * nothing is left at all is the minimum returned as-is; the
     * stack is over budget then no matter what the person does.
     *
     * The v0.1.19 overload also reserves the composition's top pad
     * ([padTopRatio]): the *block* (person + pad) is what the share
     * and the cap budget, so a parasol day shrinks the person a
     * little instead of pushing the stack over the column.
     */
    fun heightDp(columnDp: Float, fixedDp: Float): Int = heightDp(columnDp, fixedDp, 0f)

    fun heightDp(columnDp: Float, fixedDp: Float, padRatio: Float): Int {
        val remaining = columnDp - fixedDp
        if (remaining <= 0f) return HEIGHT_MIN_DP
        val block = 1f + padRatio
        val byShare = (remaining * HEIGHT_SHARE / block).roundToInt()
        val clamped = byShare.coerceIn(HEIGHT_MIN_DP, HEIGHT_MAX_DP)
        val cap = ((remaining - HEIGHT_RESERVE_DP) / block).roundToInt()
        return minOf(clamped, cap).coerceAtLeast(0)
    }
}

/**
 * The shared generated canvas (v0.1.19): bodies, tops and the
 * raincoat were generated as pictures in one 1120×2240 frame — a
 * body standing in it, a top floating where it would be worn — then
 * trimmed to their alpha bbox by tools/key_parts.py. A part's place
 * in the frame is that bbox, recorded here as fractions of the
 * canvas; the composer scales the frame so the body's bbox height
 * is the drawn person height and lays every part back into it.
 * [topCal] then adjusts each top (scaleX, scaleY, dx, dy — dx/dy in
 * body fractions): the tops are product-shot proportions, so the
 * cardigan and the knit coat are compressed vertically to their
 * worn length. Tuned by eye against Python composites of the same
 * math (tools/preview_parts.py) — the record is in
 * docs/FEATURE_v0.1.19.md.
 */
object PersonCanvas {

    const val W = 1120f
    const val H = 2240f

    /** Alpha bbox (x0, y0, x1, y1) of a canvas part, canvas fractions. */
    fun box(art: PersonArt): FloatArray = when (art) {
        PersonArt.BODY_A -> floatArrayOf(0.1589f, 0.0768f, 0.8438f, 0.9411f)
        PersonArt.BODY_B -> floatArrayOf(0.1580f, 0.0763f, 0.8446f, 0.9397f)
        PersonArt.BODY_C -> floatArrayOf(0.1116f, 0.0759f, 0.8893f, 0.9411f)
        PersonArt.TOP_TSHIRT -> floatArrayOf(0.1795f, 0.3031f, 0.8214f, 0.6741f)
        PersonArt.TOP_SHIRT -> floatArrayOf(0.2420f, 0.3125f, 0.7580f, 0.6263f)
        PersonArt.TOP_CARDIGAN -> floatArrayOf(0.0839f, 0.1911f, 0.9170f, 0.8004f)
        PersonArt.TOP_LIGHTOUTER -> floatArrayOf(0.1411f, 0.2741f, 0.8625f, 0.6920f)
        PersonArt.TOP_KNITCOAT -> floatArrayOf(0.1491f, 0.2384f, 0.8527f, 0.7781f)
        PersonArt.TOP_DOWN -> floatArrayOf(0.0598f, 0.2487f, 0.9411f, 0.7098f)
        PersonArt.OUTFIT_RAINCOAT -> floatArrayOf(0.2348f, 0.0759f, 0.7670f, 0.9420f)
        else -> floatArrayOf(0f, 0f, 1f, 1f)
    }

    fun bodyArt(base: PersonBase): PersonArt = when (base) {
        PersonBase.BODY_A -> PersonArt.BODY_A
        PersonBase.BODY_B -> PersonArt.BODY_B
        PersonBase.BODY_C -> PersonArt.BODY_C
        PersonBase.RAINCOAT -> PersonArt.OUTFIT_RAINCOAT
        PersonBase.RANK5_PORTRAIT -> PersonArt.RANK5
    }

    fun topArt(top: PersonTop): PersonArt = when (top) {
        PersonTop.TSHIRT -> PersonArt.TOP_TSHIRT
        PersonTop.SHIRT -> PersonArt.TOP_SHIRT
        PersonTop.CARDIGAN -> PersonArt.TOP_CARDIGAN
        PersonTop.LIGHT_OUTER -> PersonArt.TOP_LIGHTOUTER
        PersonTop.KNIT_COAT -> PersonArt.TOP_KNITCOAT
        PersonTop.DOWN -> PersonArt.TOP_DOWN
    }

    /** Per-top calibration (scaleX, scaleY, dx, dy); dx/dy in body fractions. */
    fun topCal(top: PersonTop): FloatArray = when (top) {
        PersonTop.CARDIGAN -> floatArrayOf(0.82f, 0.55f, 0f, 0.05f)
        PersonTop.KNIT_COAT -> floatArrayOf(1.0f, 0.83f, 0f, 0.04f)
        else -> floatArrayOf(1f, 1f, 0f, 0f)
    }
}

/**
 * A base's measured rig (v0.1.19): the face box and the hands as
 * fractions of the drawn body rect, measured off the generated
 * pictures by the keying tools (the pale-skin connected component:
 * face = its box, hands = the skin clusters at the arm ends). One
 * rig per base; every standalone prop is placed from it, so the
 * same anchors serve the layered bodies, the hybrid portrait and
 * the raincoat alike.
 */
data class PersonRig(
    val faceCx: Float, val faceTop: Float, val faceBottom: Float,
    val faceLeft: Float, val faceRight: Float,
    val handRx: Float, val handRy: Float,
    val handLx: Float, val handLy: Float
) {
    val faceW: Float get() = faceRight - faceLeft
    val faceH: Float get() = faceBottom - faceTop
}

object PersonRigs {
    fun rig(base: PersonBase): PersonRig = when (base) {
        PersonBase.BODY_A -> PersonRig(
            0.502f, 0.180f, 0.399f, 0.248f, 0.747f,
            0.753f, 0.582f, 0.243f, 0.581f
        )
        PersonBase.BODY_B -> PersonRig(
            0.480f, 0.180f, 0.337f, 0.307f, 0.689f,
            0.732f, 0.630f, 0.264f, 0.630f
        )
        PersonBase.BODY_C -> PersonRig(
            0.519f, 0.180f, 0.336f, 0.330f, 0.668f,
            0.893f, 0.629f, 0.106f, 0.629f
        )
        PersonBase.RANK5_PORTRAIT -> PersonRig(
            0.489f, 0.180f, 0.314f, 0.306f, 0.690f,
            0.756f, 0.629f, 0.242f, 0.629f
        )
        PersonBase.RAINCOAT -> PersonRig(
            0.507f, 0.081f, 0.336f, 0.201f, 0.743f,
            0.848f, 0.629f, 0.148f, 0.629f
        )
    }
}

/**
 * Where the props land (fractions of the drawn body rect unless
 * noted). The v0.1.16 anchors (the hat, the folded umbrella, the
 * open umbrella and its drawn shaft) are kept for the pieces that
 * survived; the v0.1.19 anchors were tuned in the same Python
 * mirror. The old hat asset itself is retired — the UV hat is the
 * new cap now — and the old folded umbrella likewise; their
 * constants stay only where the tests still cite them.
 */
object PersonAnchors {

    /** Head center x (the portraits are symmetric). */
    const val HEAD_CX = 0.50f

    /** Hat: width / person width, and where its brim bottom sits. */
    const val HAT_WIDTH = 0.60f
    const val HAT_BOTTOM = 0.16f

    /** Folded umbrella (v0.1.16 asset): height / person height; it
     * hangs beside the right hand. */
    const val FOLD_HEIGHT = 0.24f
    const val FOLD_DX = 0.13f
    const val FOLD_BOTTOM_DY = 0.03f

    /** Open umbrella: width / person width; canopy top at this
     * fraction of the person height. The asset is canopy + a shaft
     * stub: the rest of the shaft is a drawn line in [SHAFT_COLOR]
     * from the stub's end to the hand. */
    const val UMB_WIDTH = 1.12f
    const val UMB_TOP = 0.005f

    /** Shaft center inside the umbrella asset (fraction of its width). */
    const val UMB_SHAFT_X = 0.507f

    /** Where the shaft stands, as a fraction of the person width —
     * right of center, so the drawn shaft runs down the body's right
     * side to the hand instead of slashing across the torso. */
    const val UMB_SHAFT_AT = 0.68f

    /** Drawn shaft width / umbrella width, and its color (sampled
     * from the asset's own shaft: RGB (0, 27, 68) as an ARGB int —
     * a literal, not Color.rgb, so this object stays Android-free
     * and unit-testable). */
    const val UMB_SHAFT_W = 0.030f
    const val SHAFT_COLOR: Int = 0xFF001B44.toInt()

    // ---- v0.1.19 prop anchors (fractions of the body rect) ----

    /** Cap / knit cap: width / body width; the band's bottom. */
    const val CAP_W = 0.48f
    const val CAP_BOTTOM = 0.26f
    const val KNITCAP_W = 0.54f
    const val KNITCAP_BOTTOM = 0.245f

    /** Mask: width / face width; its top at face top + this share of
     * the face height (just under the eyes). */
    const val MASK_W = 1.06f
    const val MASK_TOP = 0.46f

    /** Sunglasses: width / face width; center at face top + this
     * share of the face height (the eye line). */
    const val SUNG_W = 1.12f
    const val SUNG_CY = 0.57f

    /** Muffler (the asset is cropped to its worn length at keying
     * time): height / body height; its top. The neck towel: width /
     * body width; its top. */
    const val MUFFLER_H = 0.32f
    const val MUFFLER_TOP = 0.29f
    const val TOWEL_W = 0.50f
    const val TOWEL_TOP = 0.355f

    /** Mittens: height / body height, centered on each hand, the
     * hand sitting this share down the mitten. */
    const val MITTEN_H = 0.115f
    const val MITTEN_HAND_AT = 0.42f

    /** The new folded umbrella: height / body height; its center
     * offset right of the hand; its bottom this far below the hand. */
    const val FOLD2_H = 0.30f
    const val FOLD2_DX = 0.10f
    const val FOLD2_BOTTOM_DY = 0.05f

    /** Parasol: width / body width; its top (above the person — the
     * pad in [PersonFigure.padTopRatio] makes the room); the shaft
     * sits at this fraction of the asset's width and is stood on the
     * right hand. */
    const val PARASOL_W = 1.15f
    const val PARASOL_TOP = -0.30f
    const val PARASOL_SHAFT_X = 0.462f

    /** Fan: height / body height; its center offset right of the
     * hand; its bottom (the handle's end) this far below the hand. */
    const val UCHIWA_H = 0.21f
    const val UCHIWA_DX = 0.12f
    const val UCHIWA_BOTTOM_DY = 0.03f

    /** Code-drawn signs: sweat drops beside the head (x positions /
     * body width, their center height, radius / body height), and
     * the shiver zigzags at the torso sides. Colors are literals so
     * this object stays Android-free. */
    const val SWEAT_X1 = 0.16f
    const val SWEAT_X2 = 0.86f
    const val SWEAT_Y = 0.30f
    const val SWEAT_R = 0.022f
    const val SWEAT_COLOR: Int = 0xFFA8D8F0.toInt()
    const val SHIVER_X1 = 0.045f
    const val SHIVER_X2 = 0.955f
    const val SHIVER_TOP = 0.42f
    const val SHIVER_STEP = 0.028f
    const val SHIVER_AMP = 0.018f
    const val SHIVER_W = 0.006f
    const val SHIVER_COLOR: Int = 0xFF0A1A3C.toInt()

    /** Right-hand position per garment (fractions of the person
     * rect), measured from the generated portraits (skin / mitten
     * centroids in tools/key_assets.py). Kept from v0.1.16; the
     * composer now reads the per-base [PersonRigs] instead. */
    fun handR(garment: PersonGarment): Pair<Float, Float> = when (garment) {
        PersonGarment.TEE_SHORTS -> 0.74f to 0.63f
        PersonGarment.SHIRT -> 0.731f to 0.633f
        PersonGarment.CARDIGAN -> 0.733f to 0.634f
        PersonGarment.SWEATER_OUTER -> 0.726f to 0.614f
        PersonGarment.COAT -> 0.767f to 0.627f
        PersonGarment.COAT_SCARF -> 0.756f to 0.629f
        PersonGarment.DOWN_GLOVES -> 0.90f to 0.66f
    }
}

/**
 * Draws a [PersonSpec] as a composite of the generated assets: the
 * base (a layered body + top, the hybrid rank-5 portrait, or the
 * raincoat outfit), then neck, mittens, head, face and held layers
 * in that order, then the code-drawn sweat / shiver signs. One
 * routine for both hosts — the in-app [PersonView] and the
 * home-screen widget (which renders it into a Bitmap, since
 * RemoteViews cannot host a custom View).
 *
 * The given rect is the whole canvas: the person stands at its
 * bottom, shrunk by [PersonFigure.padTopRatio] so a parasol canopy
 * or a knit cap has room above the head, and centered horizontally.
 * On the hybrid portrait base the muffler and the shiver are baked
 * into the picture already, so those two are not drawn again.
 */
object PersonComposer {

    private val cache = HashMap<Int, Bitmap>()

    private fun resId(art: PersonArt): Int = when (art) {
        PersonArt.RANK0 -> R.drawable.person_rank0
        PersonArt.RANK1 -> R.drawable.person_rank1
        PersonArt.RANK2 -> R.drawable.person_rank2
        PersonArt.RANK3 -> R.drawable.person_rank3
        PersonArt.RANK4 -> R.drawable.person_rank4
        PersonArt.RANK5 -> R.drawable.person_rank5
        PersonArt.RANK6 -> R.drawable.person_rank6
        PersonArt.UMBRELLA_OPEN -> R.drawable.item_umbrella_open
        PersonArt.UMBRELLA_FOLDING -> R.drawable.item_umbrella_folding
        PersonArt.HAT -> R.drawable.item_hat
        PersonArt.BODY_A -> R.drawable.body_a
        PersonArt.BODY_B -> R.drawable.body_b
        PersonArt.BODY_C -> R.drawable.body_c
        PersonArt.TOP_TSHIRT -> R.drawable.top_tshirt
        PersonArt.TOP_SHIRT -> R.drawable.top_shirt
        PersonArt.TOP_CARDIGAN -> R.drawable.top_cardigan
        PersonArt.TOP_LIGHTOUTER -> R.drawable.top_lightouter
        PersonArt.TOP_KNITCOAT -> R.drawable.top_knitcoat
        PersonArt.TOP_DOWN -> R.drawable.top_down
        PersonArt.OUTFIT_RAINCOAT -> R.drawable.outfit_raincoat
        PersonArt.PROP_CAP -> R.drawable.prop_cap
        PersonArt.PROP_KNITCAP -> R.drawable.prop_knitcap
        PersonArt.PROP_MASK -> R.drawable.prop_mask
        PersonArt.PROP_SUNGLASSES -> R.drawable.prop_sunglasses
        PersonArt.PROP_MUFFLER -> R.drawable.prop_muffler
        PersonArt.PROP_MITTEN_L -> R.drawable.prop_mitten_l
        PersonArt.PROP_MITTEN_R -> R.drawable.prop_mitten_r
        PersonArt.PROP_TOWEL -> R.drawable.prop_towel
        PersonArt.PROP_PARASOL -> R.drawable.prop_parasol
        PersonArt.PROP_UCHIWA -> R.drawable.prop_uchiwa
        PersonArt.PROP_FOLDING -> R.drawable.prop_folding
    }

    private fun bmp(ctx: Context, art: PersonArt): Bitmap {
        val id = resId(art)
        return cache.getOrPut(id) { BitmapFactory.decodeResource(ctx.resources, id) }
    }

    fun draw(
        canvas: Canvas, ctx: Context, spec: PersonSpec,
        left: Float, top: Float, width: Float, height: Float
    ) {
        if (width <= 0f || height <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // The person occupies the bottom of the rect; the pad above
        // is the props' headroom.
        val ph = height / (1f + PersonFigure.padTopRatio(spec))
        val pt = top + height - ph
        val rig = PersonRigs.rig(spec.base)

        // ---- base ----
        val bl: Float
        val bw: Float
        if (spec.base == PersonBase.RANK5_PORTRAIT) {
            val person = bmp(ctx, PersonArt.RANK5)
            bw = ph * person.width / person.height
            bl = left + (width - bw) / 2f
            canvas.drawBitmap(person, null, RectF(bl, pt, bl + bw, pt + ph), paint)
        } else {
            val bodyArt = PersonCanvas.bodyArt(spec.base)
            val box = PersonCanvas.box(bodyArt)
            val scale = ph / ((box[3] - box[1]) * PersonCanvas.H)
            val cw = PersonCanvas.W * scale
            val ch = PersonCanvas.H * scale
            bw = (box[2] - box[0]) * cw
            bl = left + (width - bw) / 2f
            val ox = bl - box[0] * cw
            val oy = pt - box[1] * ch
            fun partRect(art: PersonArt, cal: FloatArray?): RectF {
                val b = PersonCanvas.box(art)
                var x0 = ox + b[0] * cw
                var y0 = oy + b[1] * ch
                var x1 = ox + b[2] * cw
                var y1 = oy + b[3] * ch
                if (cal != null) {
                    val cx = (x0 + x1) / 2f
                    val cy = (y0 + y1) / 2f
                    val w = (x1 - x0) * cal[0]
                    val h = (y1 - y0) * cal[1]
                    x0 = cx - w / 2f + cal[2] * bw
                    y0 = cy - h / 2f + cal[3] * ph
                    x1 = x0 + w
                    y1 = y0 + h
                }
                return RectF(x0, y0, x1, y1)
            }
            canvas.drawBitmap(bmp(ctx, bodyArt), null, partRect(bodyArt, null), paint)
            spec.top?.let { topPart ->
                canvas.drawBitmap(
                    bmp(ctx, PersonCanvas.topArt(topPart)), null,
                    partRect(PersonCanvas.topArt(topPart), PersonCanvas.topCal(topPart)),
                    paint
                )
            }
        }

        fun bx(f: Float) = bl + f * bw
        fun by(f: Float) = pt + f * ph
        val handX = bx(rig.handRx)
        val handY = by(rig.handRy)
        val hybrid = spec.base == PersonBase.RANK5_PORTRAIT

        fun drawProp(art: PersonArt, cx: Float, topY: Float, w: Float) {
            val img = bmp(ctx, art)
            val h = w * img.height / img.width
            canvas.drawBitmap(img, null, RectF(cx - w / 2f, topY, cx + w / 2f, topY + h), paint)
        }

        /** A prop placed by where its bottom edge lands (the hats). */
        fun drawPropBottom(art: PersonArt, cx: Float, bottomY: Float, w: Float) {
            val img = bmp(ctx, art)
            drawProp(art, cx, bottomY - w * img.height / img.width, w)
        }

        // ---- neck (the portrait base wears its muffler baked in) ----
        when (spec.neck) {
            PersonNeck.MUFFLER -> if (!hybrid) {
                val img = bmp(ctx, PersonArt.PROP_MUFFLER)
                val h = PersonAnchors.MUFFLER_H * ph
                val w = h * img.width / img.height
                canvas.drawBitmap(
                    img, null,
                    RectF(bx(0.5f) - w / 2f, by(PersonAnchors.MUFFLER_TOP),
                        bx(0.5f) + w / 2f, by(PersonAnchors.MUFFLER_TOP) + h),
                    paint
                )
            }
            PersonNeck.TOWEL -> drawProp(
                PersonArt.PROP_TOWEL, bx(0.5f), by(PersonAnchors.TOWEL_TOP),
                PersonAnchors.TOWEL_W * bw
            )
            PersonNeck.NONE -> Unit
        }

        // ---- mittens over the hands ----
        if (spec.mittens) {
            val mh = PersonAnchors.MITTEN_H * ph
            for ((art, hx, hy) in listOf(
                Triple(PersonArt.PROP_MITTEN_L, rig.handLx, rig.handLy),
                Triple(PersonArt.PROP_MITTEN_R, rig.handRx, rig.handRy)
            )) {
                val img = bmp(ctx, art)
                val mw = mh * img.width / img.height
                val cxp = bx(hx)
                val cyp = by(hy)
                canvas.drawBitmap(
                    img, null,
                    RectF(cxp - mw / 2f, cyp - PersonAnchors.MITTEN_HAND_AT * mh,
                        cxp + mw / 2f, cyp - PersonAnchors.MITTEN_HAND_AT * mh + mh),
                    paint
                )
            }
        }

        // ---- head ----
        when (spec.head) {
            PersonHead.CAP -> drawPropBottom(
                PersonArt.PROP_CAP, bx(rig.faceCx), by(PersonAnchors.CAP_BOTTOM),
                PersonAnchors.CAP_W * bw
            )
            PersonHead.KNITCAP -> drawPropBottom(
                PersonArt.PROP_KNITCAP, bx(rig.faceCx), by(PersonAnchors.KNITCAP_BOTTOM),
                PersonAnchors.KNITCAP_W * bw
            )
            PersonHead.NONE -> Unit
        }

        // ---- face ----
        if (spec.mask) {
            drawProp(
                PersonArt.PROP_MASK, bx(rig.faceCx),
                by(rig.faceTop + PersonAnchors.MASK_TOP * rig.faceH),
                PersonAnchors.MASK_W * rig.faceW * bw
            )
        }
        if (spec.sunglasses) {
            val img = bmp(ctx, PersonArt.PROP_SUNGLASSES)
            val w = PersonAnchors.SUNG_W * rig.faceW * bw
            val h = w * img.height / img.width
            val cy = by(rig.faceTop + PersonAnchors.SUNG_CY * rig.faceH)
            canvas.drawBitmap(
                img, null,
                RectF(bx(rig.faceCx) - w / 2f, cy - h / 2f, bx(rig.faceCx) + w / 2f, cy + h / 2f),
                paint
            )
        }

        // ---- held ----
        when (spec.held) {
            PersonHeld.OPEN -> {
                val umb = bmp(ctx, PersonArt.UMBRELLA_OPEN)
                val uw = PersonAnchors.UMB_WIDTH * bw
                val uh = uw * umb.height / umb.width
                val uLeft = bl + PersonAnchors.UMB_SHAFT_AT * bw -
                    PersonAnchors.UMB_SHAFT_X * uw
                val uTop = pt + PersonAnchors.UMB_TOP * ph
                // The shaft extension first: the asset's stub is drawn
                // over its top, so the join hides under the canopy.
                val shaft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = PersonAnchors.SHAFT_COLOR
                    strokeWidth = PersonAnchors.UMB_SHAFT_W * uw
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(
                    uLeft + PersonAnchors.UMB_SHAFT_X * uw, uTop + uh,
                    handX, handY, shaft
                )
                canvas.drawBitmap(umb, null, RectF(uLeft, uTop, uLeft + uw, uTop + uh), paint)
            }
            PersonHeld.FOLDED -> {
                val fold = bmp(ctx, PersonArt.PROP_FOLDING)
                val fh = PersonAnchors.FOLD2_H * ph
                val fw = fh * fold.width / fold.height
                val fLeft = handX + PersonAnchors.FOLD2_DX * bw - fw / 2f
                val fTop = handY + PersonAnchors.FOLD2_BOTTOM_DY * ph - fh
                canvas.drawBitmap(fold, null, RectF(fLeft, fTop, fLeft + fw, fTop + fh), paint)
            }
            PersonHeld.PARASOL -> {
                val img = bmp(ctx, PersonArt.PROP_PARASOL)
                val uw = PersonAnchors.PARASOL_W * bw
                val uh = uw * img.height / img.width
                val uLeft = handX - PersonAnchors.PARASOL_SHAFT_X * uw
                val uTop = pt + PersonAnchors.PARASOL_TOP * ph
                canvas.drawBitmap(img, null, RectF(uLeft, uTop, uLeft + uw, uTop + uh), paint)
            }
            PersonHeld.UCHIWA -> {
                val img = bmp(ctx, PersonArt.PROP_UCHIWA)
                val fh = PersonAnchors.UCHIWA_H * ph
                val fw = fh * img.width / img.height
                val fLeft = handX + PersonAnchors.UCHIWA_DX * bw - fw / 2f
                val fTop = handY + PersonAnchors.UCHIWA_BOTTOM_DY * ph - fh
                canvas.drawBitmap(img, null, RectF(fLeft, fTop, fLeft + fw, fTop + fh), paint)
            }
            PersonHeld.NONE -> Unit
        }

        // ---- code-drawn signs (the hybrid portrait has its shiver
        //      baked in; the sweat was never on a cold rank) ----
        if (spec.sweat) {
            val drop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PersonAnchors.SWEAT_COLOR
                style = Paint.Style.FILL
            }
            for (sx in listOf(PersonAnchors.SWEAT_X1, PersonAnchors.SWEAT_X2)) {
                val cx = bx(sx)
                val cy = by(PersonAnchors.SWEAT_Y)
                val r = PersonAnchors.SWEAT_R * ph
                val path = Path().apply {
                    moveTo(cx, cy - r * 1.5f)
                    quadTo(cx + r * 0.62f, cy - r * 0.2f, cx + r * 0.62f, cy + r * 0.25f)
                    arcTo(RectF(cx - r * 0.62f, cy - r * 0.38f, cx + r * 0.62f, cy + r * 0.88f), 0f, 180f)
                    quadTo(cx - r * 0.62f, cy - r * 0.2f, cx, cy - r * 1.5f)
                    close()
                }
                canvas.drawPath(path, drop)
            }
        }
        if (spec.shiver && !hybrid) {
            val zig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PersonAnchors.SHIVER_COLOR
                style = Paint.Style.STROKE
                strokeWidth = PersonAnchors.SHIVER_W * ph
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (sx in listOf(PersonAnchors.SHIVER_X1, PersonAnchors.SHIVER_X2)) {
                val x0 = bx(sx)
                val path = Path()
                for (i in 0..10) {
                    val yy = by(PersonAnchors.SHIVER_TOP + i * PersonAnchors.SHIVER_STEP)
                    val xx = x0 + (if (i % 2 == 1) PersonAnchors.SHIVER_AMP else -PersonAnchors.SHIVER_AMP) * bw
                    if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                canvas.drawPath(path, zig)
            }
        }
    }
}

/**
 * The in-app 「今日の人」: a plain View hosting [PersonComposer].
 * The caller sizes it (the composition centers itself in whatever
 * rect it gets, pad included). Set a contentDescription at the call
 * site (the day's clothing answer), as with IconView.
 */
class PersonView(
    ctx: Context,
    private val spec: PersonSpec
) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        PersonComposer.draw(
            canvas, context, spec, 0f, 0f, width.toFloat(), height.toFloat()
        )
    }
}
