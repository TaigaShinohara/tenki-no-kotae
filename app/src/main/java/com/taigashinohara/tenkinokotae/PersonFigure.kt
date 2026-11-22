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
 * arms spill out), with the new props laid over it.
 *
 * v0.1.22 (docs/FIX_v0.1.22.md) re-anchors every held prop on its
 * GRIP point — the fan, the folded umbrella, the open umbrella and
 * the parasol now place the handle itself on the hand instead of
 * offsetting the part's centre — and re-measures BODY_A's hands,
 * which sat mid-forearm (0.582 of the height) instead of at the arm's
 * end (0.638), dragging every hand-anchored part on ranks 1–4 up
 * with them. The same pass puts a neck calibration on EVERY top:
 * collars used to ride up to the chin (the polo's collar tips touched
 * the jaw and the light coat covered the mouth); each topCal now
 * lands its collar at the neck base, verified on 3× zoom crops of
 * the neckline for all six tops. The sweat drops move up and inward
 * onto the hair (they hung beside the cheeks like earrings).
 *
 * v0.1.21 (docs/FIX_v0.1.21.md) extends the hybrid call to rank 6:
 * the down top is a closed-collar product shot with no face opening
 * at all, so laid over the wide body it swallowed the whole face —
 * the user found only the eyes peeking out on device and asked for
 * the face back (「顔は見えるようにして」). Rank 6 now stands on
 * its v0.1.16 portrait like rank 5 (its muffler, gloves and shiver
 * are baked in), and a strict audit of every slot combination in
 * the Python mirror (tools/mirror_v0121.py) re-anchored the props
 * to measured feature lines ([PersonRig.eyeY] / [PersonRig.mouthY]):
 * the sunglasses sit on the eyes now (they were centered on the
 * mouth), the cap brim and the knit cap's band end above the eye
 * line (both covered the eyes), the mask hangs from the eye line
 * (on the standard body it slipped below the chin), and the parasol
 * — its asset re-cut without the J-hook — grows a drawn shaft down
 * to the hand like the rain umbrella's. The raincoat is
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

/** The base the composer builds on (v0.1.19; rank 6 joined the
 * portrait bases in v0.1.21). */
enum class PersonBase { BODY_A, BODY_B, BODY_C, RANK5_PORTRAIT, RANK6_PORTRAIT, RAINCOAT }

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

/** The outfit-linked elements of a spec (v0.1.20); see
 * [PersonFigure.outfitLinked]. */
data class OutfitLinked(
    val neck: PersonNeck,
    val mittens: Boolean,
    val shiver: Boolean,
    val sweat: Boolean
)

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
     * Ranks 5 and 6 are the hybrids (their v0.1.16 portraits, no top
     * over them — rank 6 joined in v0.1.21: the down top is a closed
     * product shot that hid the face); the raincoat replaces the
     * outfit wholesale on its days.
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
            else -> PersonBase.RANK6_PORTRAIT to null
        }
    }

    /**
     * The elements an outfit carries with it (v0.1.20): the muffler
     * on the cold ranks, the mittens on the coldest, the shiver and
     * sweat signs. Derived from the outfit alone — never from the
     * day — so the test screen's manual outfit can re-derive them
     * from the outfit it picked instead of inheriting whatever
     * today's auto spec happened to wear (the v0.1.19 leak: a
     * hand-picked summer outfit kept the cold day's muffler, and a
     * hand-picked raincoat kept its muffler and shiver). The
     * raincoat is a finished full-body picture: it carries none of
     * these, at any rank. The neck towel is NOT outfit-linked — it
     * is a weather element (extreme heat), and [spec] layers it
     * over this result.
     */
    fun outfitLinked(rank: Int, raincoat: Boolean): OutfitLinked {
        if (raincoat) return OutfitLinked(PersonNeck.NONE, false, false, false)
        val r = rank.coerceIn(0, 6)
        return OutfitLinked(
            neck = if (r >= 5) PersonNeck.MUFFLER else PersonNeck.NONE,
            mittens = r == 6,
            shiver = r >= 5,
            sweat = r == 0
        )
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
        // The outfit-linked elements come from the outfit alone
        // (v0.1.20: one derivation for the auto spec and the test
        // screen alike); the neck towel is a weather element and
        // layers over the linked neck on its extreme-heat days.
        val linked = outfitLinked(r, raincoat)
        val neck =
            if (!raincoat && r == 0 && daytimeFeelAvg != null &&
                daytimeFeelAvg >= TOWEL_FEEL
            ) {
                PersonNeck.TOWEL
            } else {
                linked.neck
            }
        return PersonSpec(
            garment = garmentOf(r),
            umbrella = when (umbrellaLevel) {
                Rules.UmbrellaLevel.NEED -> PersonUmbrella.OPEN
                Rules.UmbrellaLevel.FOLDING -> PersonUmbrella.FOLDED
                Rules.UmbrellaLevel.NONE -> PersonUmbrella.NONE
            },
            hat = head == PersonHead.CAP,
            shiver = linked.shiver,
            sweat = linked.sweat,
            base = base,
            top = top,
            head = head,
            neck = neck,
            mask = r >= MASK_RANK && daytimeHumidityAvg != null &&
                daytimeHumidityAvg < MASK_HUMIDITY,
            sunglasses = uvMax >= SUNGLASSES_UV,
            mittens = linked.mittens,
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
        // The cap's dome rises a little past the hair (v0.1.21).
        spec.head == PersonHead.CAP -> 0.04f
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
        PersonBase.RANK6_PORTRAIT -> PersonArt.RANK6
    }

    fun topArt(top: PersonTop): PersonArt = when (top) {
        PersonTop.TSHIRT -> PersonArt.TOP_TSHIRT
        PersonTop.SHIRT -> PersonArt.TOP_SHIRT
        PersonTop.CARDIGAN -> PersonArt.TOP_CARDIGAN
        PersonTop.LIGHT_OUTER -> PersonArt.TOP_LIGHTOUTER
        PersonTop.KNIT_COAT -> PersonArt.TOP_KNITCOAT
        PersonTop.DOWN -> PersonArt.TOP_DOWN
    }

    /** Per-top calibration (scaleX, scaleY, dx, dy); dx/dy in body
     * fractions. Since v0.1.22 EVERY top carries one, solved so the
     * top's collar lands on the neck base of its body (drawn collar
     * top ≈ 0.33 of the body height; the neck itself spans
     * 0.298–0.337): the product-shot tops ride high — the polo's
     * collar tips touched the jaw, the shirt's collar sat at the
     * chin, the light coat's collar peaks covered the mouth. The
     * cardigan was already right and keeps its v0.1.19 values. The
     * vertical squeeze absorbs part of each shift so the hems stay
     * near their worn length. Verified on 3× neckline crops
     * (tools/audit22/) and pinned by a unit test. */
    fun topCal(top: PersonTop): FloatArray = when (top) {
        PersonTop.TSHIRT -> floatArrayOf(1.0f, 0.91f, 0f, 0.047f)
        PersonTop.SHIRT -> floatArrayOf(1.0f, 0.90f, 0f, 0.044f)
        PersonTop.CARDIGAN -> floatArrayOf(0.82f, 0.55f, 0f, 0.05f)
        PersonTop.LIGHT_OUTER -> floatArrayOf(1.0f, 0.87f, 0f, 0.075f)
        PersonTop.KNIT_COAT -> floatArrayOf(1.0f, 0.78f, 0f, 0.079f)
        PersonTop.DOWN -> floatArrayOf(1.0f, 0.85f, 0f, 0.090f)
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
    val handLx: Float, val handLy: Float,
    /** The measured eye line and mouth line (v0.1.21), fractions of
     * the body height, from ink-cluster analysis of the shipped
     * pictures. The face box above is the pale-skin component's
     * bounds — on some bases it runs into the neck or the hood, so
     * the face props (sunglasses, mask) are placed from these lines
     * instead of from fractions of the box. */
    val eyeY: Float,
    val mouthY: Float
) {
    val faceW: Float get() = faceRight - faceLeft
    val faceH: Float get() = faceBottom - faceTop
}

object PersonRigs {
    fun rig(base: PersonBase): PersonRig = when (base) {
        // BODY_A's hands were re-measured in v0.1.22: the old y
        // (0.582) sat mid-forearm; the hands end at 0.638 / 0.637.
        PersonBase.BODY_A -> PersonRig(
            0.502f, 0.180f, 0.399f, 0.248f, 0.747f,
            0.754f, 0.638f, 0.241f, 0.637f,
            0.216f, 0.260f
        )
        PersonBase.BODY_B -> PersonRig(
            0.480f, 0.180f, 0.337f, 0.307f, 0.689f,
            0.732f, 0.630f, 0.264f, 0.630f,
            0.216f, 0.260f
        )
        PersonBase.BODY_C -> PersonRig(
            0.519f, 0.180f, 0.336f, 0.330f, 0.668f,
            0.893f, 0.629f, 0.106f, 0.629f,
            0.216f, 0.260f
        )
        PersonBase.RANK5_PORTRAIT -> PersonRig(
            0.489f, 0.180f, 0.314f, 0.306f, 0.690f,
            0.756f, 0.629f, 0.242f, 0.629f,
            0.217f, 0.260f
        )
        PersonBase.RANK6_PORTRAIT -> PersonRig(
            0.497f, 0.180f, 0.330f, 0.323f, 0.671f,
            0.80f, 0.63f, 0.20f, 0.63f,
            0.216f, 0.261f
        )
        PersonBase.RAINCOAT -> PersonRig(
            0.507f, 0.081f, 0.336f, 0.201f, 0.743f,
            0.848f, 0.629f, 0.148f, 0.629f,
            0.216f, 0.260f
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
     * from the stub's end to the hand. Since v0.1.22 the canopy is
     * stood on the hand — the shaft's x IS the right hand's x — so
     * the drawn shaft runs straight down into the hand; until then
     * the shaft stood at a fixed 0.68 of the width and the line
     * kinked diagonally to reach hands that stand elsewhere (on
     * BODY_C the kink was about 15°). */
    const val UMB_WIDTH = 1.12f
    const val UMB_TOP = 0.005f

    /** Shaft center inside the umbrella asset (fraction of its width). */
    const val UMB_SHAFT_X = 0.507f

    /** Drawn shaft width / umbrella width, and its color (sampled
     * from the asset's own shaft: RGB (0, 27, 68) as an ARGB int —
     * a literal, not Color.rgb, so this object stays Android-free
     * and unit-testable). */
    const val UMB_SHAFT_W = 0.030f
    const val SHAFT_COLOR: Int = 0xFF001B44.toInt()

    // ---- v0.1.19 prop anchors (fractions of the body rect) ----

    /** Cap / knit cap: width / body width; the brim's / band's
     * bottom. Both end just above the measured eye line (0.216) —
     * until v0.1.20 they sat at 0.26 / 0.245, over the eyes. */
    const val CAP_W = 0.45f
    const val CAP_BOTTOM = 0.205f
    const val KNITCAP_W = 0.52f
    const val KNITCAP_BOTTOM = 0.20f

    /** Mask: width / face width; its top hangs this far below the
     * measured eye line ([PersonRig.eyeY]), so it starts under the
     * eyes on every base (until v0.1.20 it was placed by a share of
     * the skin-box height and slipped below the chin on BODY_A). */
    const val MASK_W = 1.06f
    const val MASK_DROP = 0.020f

    /** Sunglasses: width / face width; centered on the measured eye
     * line ([PersonRig.eyeY]) — until v0.1.20 they were centered at
     * 0.57 of the skin-box height, which is the mouth line. */
    const val SUNG_W = 1.12f

    /** Muffler (the asset is cropped to its worn length at keying
     * time): height / body height; its top. Sized down in v0.1.21
     * (0.32 → 0.27): at the old size the ring covered the whole
     * chest of the slim bodies, and its top band reached the mouth.
     * The neck towel: width / body width; its top (0.50 → 0.38 in
     * v0.1.21: at the old width it read as an apron). */
    const val MUFFLER_H = 0.27f
    const val MUFFLER_TOP = 0.305f
    const val TOWEL_W = 0.38f
    const val TOWEL_TOP = 0.355f

    /** Mittens: height / body height, centered on each hand, the
     * hand sitting this share down the mitten. */
    const val MITTEN_H = 0.115f
    const val MITTEN_HAND_AT = 0.42f

    /** The new folded umbrella: height / body height, placed by its
     * grip point (v0.1.22): the grip — the straight of the handle
     * just above the J-hook, measured at (0.52, 0.90) of the asset —
     * lands exactly on the hand. Until then the part was offset from
     * its center and hung beside the hand. */
    const val FOLD2_H = 0.30f
    const val FOLD2_GRIP_X = 0.52f
    const val FOLD2_GRIP_Y = 0.90f

    /** Parasol: width / body width; its top (above the person — the
     * pad in [PersonFigure.padTopRatio] makes the room); the shaft
     * sits at this fraction of the asset's width (measured 0.508,
     * not the 0.462 used until v0.1.20) and is stood on the right
     * hand. The asset was re-cut in v0.1.21 without its J-hook: a
     * drawn shaft — an outline pass in [PARASOL_SHAFT_EDGE], then a
     * fill pass in [PARASOL_SHAFT_COLOR] — runs from the cut end
     * down to the hand, because the asset's own shaft stopped about
     * a tenth of the body height short of the hand. */
    const val PARASOL_W = 1.15f
    const val PARASOL_TOP = -0.20f
    const val PARASOL_SHAFT_X = 0.508f

    /** Drawn parasol shaft width / asset width (the asset's own
     * shaft measures 0.057), and its colors sampled from the asset
     * (fill RGB (151, 201, 234); the outline is the family's ink). */
    const val PARASOL_SHAFT_W = 0.057f
    const val PARASOL_SHAFT_COLOR: Int = 0xFF97C9EA.toInt()
    const val PARASOL_SHAFT_EDGE: Int = 0xFF1B2566.toInt()

    /** Fan: height / body height, placed by its grip point
     * (v0.1.22): the handle's middle — measured at (0.502, 0.90) of
     * the asset; the handle spans y 0.79–1.00 at x ≈ 0.50 — lands
     * exactly on the hand, so the disc rides above the hand and the
     * handle runs into it. The older center-offset placement (even
     * after v0.1.21 narrowed it to 0.05) still left the handle a
     * full hand-width right of the hand, and the whole fan floated
     * off the body's right side. */
    const val UCHIWA_H = 0.21f
    const val UCHIWA_GRIP_X = 0.502f
    const val UCHIWA_GRIP_Y = 0.90f

    /** Code-drawn signs: sweat drops beside the head (x positions /
     * body width, their center height, radius / body height), and
     * the shiver zigzags at the torso sides. Colors are literals so
     * this object stays Android-free. The drops moved up and inward
     * in v0.1.22 (0.16/0.86 at y 0.30 → 0.19/0.83 at y 0.27): they
     * hung off the hair's lower corners beside the cheeks like
     * earrings; now they sit on the hair at the face's sides. */
    const val SWEAT_X1 = 0.19f
    const val SWEAT_X2 = 0.83f
    const val SWEAT_Y = 0.27f
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

    /** Grip-point placement (v0.1.22): the (left, top) a prop rect
     * of size (propW × propH) must take so that the prop's grip
     * point — (gripX, gripY) as fractions of the prop — lands
     * exactly on the hand at (handX, handY). All four held props
     * are placed this way (the two umbrellas' shafts stand on the
     * hand directly; the fan and the folded umbrella go through
     * this). Pure math, unit-tested. */
    fun gripOrigin(
        gripX: Float, gripY: Float, propW: Float, propH: Float,
        handX: Float, handY: Float
    ): FloatArray = floatArrayOf(handX - gripX * propW, handY - gripY * propH)
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
 * On the hybrid portrait bases (ranks 5 and 6) the muffler, the
 * mittens and the shiver are baked into the picture already, so
 * those are not drawn again. On the raincoat base — a finished
 * full-body picture — the neck, mitten and sign layers are not
 * drawn at all (v0.1.20).
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
        if (spec.base == PersonBase.RANK5_PORTRAIT ||
            spec.base == PersonBase.RANK6_PORTRAIT
        ) {
            val person = bmp(ctx, PersonCanvas.bodyArt(spec.base))
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
        val hybrid = spec.base == PersonBase.RANK5_PORTRAIT ||
            spec.base == PersonBase.RANK6_PORTRAIT
        // The raincoat is a finished full-body picture (v0.1.20):
        // no neck / mitten / sign layers over it, whatever the spec
        // fields say — the guarantee lives here, not only in the
        // spec's derivation, so a hand-built spec cannot leak them.
        // Head, face and held layers still go over it as usual.
        val raincoatBase = spec.base == PersonBase.RAINCOAT

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

        // ---- neck (the portrait base wears its muffler baked in;
        //      the raincoat base takes no neck layer at all) ----
        if (!raincoatBase) when (spec.neck) {
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

        // ---- mittens over the hands (the portrait bases wear
        //      theirs baked in) ----
        if (spec.mittens && !raincoatBase && !hybrid) {
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

        // ---- face (placed from the measured feature lines,
        //      v0.1.21: the sunglasses on the eye line, the mask
        //      hanging just under it) ----
        if (spec.mask) {
            drawProp(
                PersonArt.PROP_MASK, bx(rig.faceCx),
                by(rig.eyeY + PersonAnchors.MASK_DROP),
                PersonAnchors.MASK_W * rig.faceW * bw
            )
        }
        if (spec.sunglasses) {
            val img = bmp(ctx, PersonArt.PROP_SUNGLASSES)
            val w = PersonAnchors.SUNG_W * rig.faceW * bw
            val h = w * img.height / img.width
            val cy = by(rig.eyeY)
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
                // The shaft stands on the hand (v0.1.22): its x IS
                // handX, so the extension below runs straight down.
                val uLeft = handX - PersonAnchors.UMB_SHAFT_X * uw
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
                val (fLeft, fTop) = PersonAnchors.gripOrigin(
                    PersonAnchors.FOLD2_GRIP_X, PersonAnchors.FOLD2_GRIP_Y,
                    fw, fh, handX, handY
                ).let { it[0] to it[1] }
                canvas.drawBitmap(fold, null, RectF(fLeft, fTop, fLeft + fw, fTop + fh), paint)
            }
            PersonHeld.PARASOL -> {
                val img = bmp(ctx, PersonArt.PROP_PARASOL)
                val uw = PersonAnchors.PARASOL_W * bw
                val uh = uw * img.height / img.width
                val uLeft = handX - PersonAnchors.PARASOL_SHAFT_X * uw
                val uTop = pt + PersonAnchors.PARASOL_TOP * ph
                // The drawn shaft extension first (the asset's cut
                // end covers its top): an outline pass, then the
                // fill pass inside it, down to the hand (v0.1.21).
                val shaftX = uLeft + PersonAnchors.PARASOL_SHAFT_X * uw
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = PersonAnchors.PARASOL_SHAFT_EDGE
                    strokeWidth = PersonAnchors.PARASOL_SHAFT_W * uw
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(shaftX, uTop + uh, handX, handY, edge)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = PersonAnchors.PARASOL_SHAFT_COLOR
                    strokeWidth = PersonAnchors.PARASOL_SHAFT_W * uw * 0.55f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(shaftX, uTop + uh, handX, handY, fill)
                canvas.drawBitmap(img, null, RectF(uLeft, uTop, uLeft + uw, uTop + uh), paint)
            }
            PersonHeld.UCHIWA -> {
                val img = bmp(ctx, PersonArt.PROP_UCHIWA)
                val fh = PersonAnchors.UCHIWA_H * ph
                val fw = fh * img.width / img.height
                val (fLeft, fTop) = PersonAnchors.gripOrigin(
                    PersonAnchors.UCHIWA_GRIP_X, PersonAnchors.UCHIWA_GRIP_Y,
                    fw, fh, handX, handY
                ).let { it[0] to it[1] }
                canvas.drawBitmap(img, null, RectF(fLeft, fTop, fLeft + fw, fTop + fh), paint)
            }
            PersonHeld.NONE -> Unit
        }

        // ---- code-drawn signs (the hybrid portrait has its shiver
        //      baked in; the sweat was never on a cold rank; the
        //      raincoat base takes neither sign) ----
        if (spec.sweat && !raincoatBase) {
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
        if (spec.shiver && !hybrid && !raincoatBase) {
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
