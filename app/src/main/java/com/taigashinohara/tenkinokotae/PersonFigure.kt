package com.taigashinohara.tenkinokotae

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.ceil
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
 * v0.1.25 (docs/FEATURE_v0.1.25.md) changes how the head is drawn,
 * at the user's direction (「帽子などのかぶり物は髪型とセットで画
 * 像にしてほしい」): the cap and the knit cap are no longer
 * standalone props laid over the hair — each is a HEAD LAYER, a
 * generated picture of the hair already wearing the hat, laid over
 * the base so its face lands on the rig's face (scale from the
 * face width, centre x and top aligned; the raincoat base anchors
 * on the eye line instead — its rig face top is the hood opening,
 * not the forehead). The head layer replaces the base's hair
 * wholesale, so the hat can no longer sit off the hair it was
 * floating on. The same pass re-fits the two open-front outers
 * (the layered ranks the user still found off — 「まだ少しズレて
 * る」): their silhouettes sat inside the body's at the shoulder
 * rows; [PersonCanvas.topCal] widens them (sx only). The held
 * items keep the v0.1.22 grip-point drawing — a matching
 * arm-layer set was generated and measured but is NOT shipped:
 * whether a generic bare arm should serve every outfit is the
 * user's open question, so the arm layers wait in
 * tools/layers_keyed/, unused.
 *
 * v0.1.26 (docs/FEATURE_v0.1.26.md) settles that question the way
 * the user proposed (「その腕は服装ごとに用意しておくべき」) and
 * then grouped (sleeve groups, not one image per rank): the held
 * items are now drawn as ARM LAYERS — one generated picture of the
 * sleeve + arm + hand + item, made per sleeve group (the polo
 * group serves ranks 0–1, the cardigan group ranks 2–3, the knit
 * coat is rank 4's own) from the outfit's own sleeve, so the arm
 * arrives already wearing the day's clothes and the body's own
 * arm is covered, never doubled. The hybrid portraits and the
 * raincoat take the same idea whole: on an umbrella day the base
 * picture itself is swapped for a generated variant that already
 * holds the umbrella (open or folded), drawn so the variant's
 * inner rect — where the base picture lives inside it, measured
 * in tools/measure26b.py — lands on the standard person rect,
 * keeping every rig anchor valid. Combos with no arm picture
 * (the parasol / fan outside the polo group, a hand-built spec)
 * keep the v0.1.22 grip-point drawing as the fallback. The
 * generic bare-arm set from v0.1.25 still waits unused.
 *
 * v0.1.27 (docs/FEATURE_v0.1.27.md) works through the full
 * illustration audit (tools/illust_audit/, 8 findings — 3 medium,
 * 5 small; the fixes were prepared and verified in
 * tools/illust_fixes/FIX_REPORT.md). The head layers lose the
 * shirt collar / shoulder fragment that survived at their bottom
 * edge (it drew a straight horizontal seam across the chest);
 * the polo parasol's arm is rebuilt so its sleeve lands on the
 * body's shoulder, and the fan's arm is regenerated with a domed
 * shoulder cap instead of the flat cut that floated beside the
 * hair. The neck towel's hem is rounded. Two measured numbers
 * move: the RAINCOAT rig's cheekW 0.464 → 0.40 (it was the hood
 * opening's width, not the face's — the mask overflowed onto the
 * hood), and the TSHIRT topCal width 0.70 → 0.64 (the tee's wide
 * body panel hid half of rank 0's hands). The open-umbrella
 * portraits of ranks 5 and 6 are regenerated from their bases so
 * the figure keeps the base's build and muffler length (rank 6
 * still measures ≈5–10% slimmer — the generator redraws what it
 * should not touch; see the FEATURE doc), with their inner rects
 * re-measured the measure26b way. And the raincoat base gets its
 * own head-layer variants, clipped to the hood opening, so the
 * hair no longer spills over the hood's rim on that base.
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
    LAYER_HEAD_CAP, LAYER_HEAD_KNITCAP,
    // v0.1.27: the raincoat base's own head layers, clipped to
    // the hood opening (same canvas, same face anchors).
    LAYER_HEAD_CAP_RAIN, LAYER_HEAD_KNITCAP_RAIN,
    PROP_MASK, PROP_SUNGLASSES, PROP_MUFFLER,
    PROP_MITTEN_L, PROP_MITTEN_R, PROP_TOWEL, PROP_PARASOL, PROP_UCHIWA,
    PROP_FOLDING,
    // v0.1.26: sleeve-group arm layers (sleeve + arm + item in one
    // picture) and the umbrella variants of the portrait bases.
    ARM_POLO_OPEN, ARM_POLO_FOLDING, ARM_POLO_PARASOL, ARM_POLO_UCHIWA,
    ARM_CARDIGAN_OPEN, ARM_CARDIGAN_FOLDING,
    ARM_KNITCOAT_OPEN, ARM_KNITCOAT_FOLDING,
    FIG_RANK5_OPEN, FIG_RANK5_FOLDING, FIG_RANK6_OPEN, FIG_RANK6_FOLDING,
    FIG_RAINCOAT_OPEN, FIG_RAINCOAT_FOLDING,
    // v0.1.29: full-body parasol / fan portraits of the two
    // summer layered bases (rank 0 = BODY_B + T-shirt, rank 1 =
    // BODY_A + shirt), swapped in whole like the umbrella
    // variants above.
    FIG_RANK0_PARASOL, FIG_RANK0_UCHIWA,
    FIG_RANK1_PARASOL, FIG_RANK1_UCHIWA
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
     * fraction of the person's height: every picture that pokes
     * above the person top contributes its measured poke, rounded
     * up to the next 0.01 — a head layer (v0.1.25, its dome stands
     * taller than the hair it replaces), an arm layer's umbrella
     * canopy (v0.1.26), or an umbrella variant portrait whose inner
     * person sits lower in its frame (v0.1.26). The one flat rule
     * left is the FALLBACK parasol's 0.34 (the standing rule since
     * v0.1.19): the old floating asset rides well above the head,
     * but only where no arm-layer parasol serves — the polo group's
     * arm parasol is grip-anchored at the person top and needs no
     * room of its own. [PersonComposer] shrinks the person into
     * the given rect by this much; callers sizing a view for a
     * known person height multiply by (1 + padTopRatio).
     */
    fun padTopRatio(spec: PersonSpec): Float {
        var pad = 0f
        // A whole-picture variant owns the held item outright
        // (v0.1.29): its poke is the only held-item contribution —
        // the arm layer it suppresses must not add its own poke,
        // and the fallback parasol's flat 0.34 must not fire
        // under a parasol portrait.
        val variant = PersonAnchors.variantArt(spec.base, spec.top, spec.held)
        if (variant != null) {
            val vPoke = PersonAnchors.variantPoke(spec.base, spec.top, spec.held)
            if (vPoke > 0f) {
                pad = maxOf(pad, ceil((vPoke + 0.005f) * 100f) / 100f)
            }
        } else {
            val arm = PersonAnchors.armArt(spec.top, spec.held)
            if (spec.held == PersonHeld.PARASOL && arm == null) pad = 0.34f
            if (arm != null) {
                val poke = PersonAnchors.armPoke(spec.base, spec.top, spec.held)
                if (poke > 0f) {
                    pad = maxOf(pad, ceil((poke + 0.005f) * 100f) / 100f)
                }
            }
        }
        if (spec.head != PersonHead.NONE) {
            val poke = PersonAnchors.headLayerPoke(spec.base, spec.head)
            pad = maxOf(pad, ceil((poke + 0.005f) * 100f) / 100f)
        }
        return pad
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

    /** Drawn body width / height for a base (v0.1.25): the canvas
     * bases from their boxes, the portraits from their measured
     * asset pixel sizes (405×1024 / 448×1024). The head layer's
     * geometry is solved in these units (see
     * [PersonAnchors.headLayerRect]). */
    fun bodyAspect(base: PersonBase): Float = when (base) {
        PersonBase.RANK5_PORTRAIT -> 405f / 1024f
        PersonBase.RANK6_PORTRAIT -> 448f / 1024f
        else -> {
            val b = box(bodyArt(base))
            (b[2] - b[0]) * W / ((b[3] - b[1]) * H)
        }
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
     * chin, the light coat's collar peaks covered the mouth.
     *
     * v0.1.23 re-solved every value against the measured body
     * silhouettes: the product-shot tops were also far too WIDE
     * (silhouette excess over the body, arms included: cardigan
     * +0.33, light outer +0.35, knit coat +0.32 of the body width
     * at chest height) and hung to or past the hands, so the
     * figure read as wearing a tent and the hands vanished under
     * the cloth (hand-skin survival 0–8%). The new values narrow
     * each top until its silhouette tracks the body's (excess
     * −0.05…+0.05), keep the collar on the neck base, end the
     * long sleeves just above the hands (drawn cuff ≈ 0.61–0.62,
     * hands end ≈ 0.66) and give each garment its worn length.
     * Survival after the fix, measured on the shipped assets:
     * polo 91–97%, shirt 81%, cardigan 37%, light outer 65–67%,
     * knit coat 47%, down 99%. Verified on enlarged crops
     * (tools/audit23/) and pinned by unit tests.
     *
     * v0.1.24 shifts every top up by a uniform 0.012 (dy only).
     * The v0.1.23 solve had landed each top's drawn shoulder line
     * 0.016–0.027 BELOW the body's own shoulder (measured at the
     * shoulder bands: the body's torso top is 0.327 / 0.330 /
     * 0.312 on BODY_A / B / C, the drawn tops sat at 0.336–0.354),
     * so the body's tank straps peeked above every garment's
     * shoulder and the clothes read as slipping down — the user's
     * 「シャツが全部下に少しズレてる」. The uniform shift halves
     * the gap (to 0.004–0.015) while the collars stay inside the
     * neck-base band (0.318–0.323 after the shift; the pinned
     * band is 0.315–0.355), the hems keep their v0.1.23
     * worn-length bands, and the raised cuffs only free more of
     * the hands. Verified on enlarged crops (tools/audit24/).
     *
     * v0.1.25 widens the two open-front outers (sx only — the
     * vertical solve above is untouched). Measured per row, the
     * light outer's and the knit coat's silhouettes sat INSIDE
     * the body's at the shoulder rows (width excess at rows
     * 0.36–0.40: light outer −0.049/−0.039/−0.032, knit coat
     * −0.051/−0.033/−0.020), so the body's own shoulder line
     * rimmed the garment and the baked-in inner shirt dominated
     * the chest — the user's 「まだ少しズレてる」 on the layered
     * ranks. The cardigan already tracked the body (±0.01) and
     * is unchanged. After the widening: light outer
     * −0.011/−0.005/+0.004, knit coat −0.035/−0.019/−0.004
     * (its A-line asset trades the rest against hand survival,
     * 71.6→66.3 on the audit path); collars and hems are exactly
     * where v0.1.24 put them. The record is
     * docs/FEATURE_v0.1.25.md; verified on enlarged crops
     * (tools/audit25/). */
    fun topCal(top: PersonTop): FloatArray = when (top) {
        // v0.1.27: the width 0.70 → 0.64 — the tee's wide body
        // panel (not its short sleeves) covered the hands at its
        // hem line; at 0.64 the visible-hand share rises
        // 50% → 60% and the arm line reads at the sides. The
        // height terms were measured to make no difference and
        // stay. (tools/illust_fixes/ 小4.)
        PersonTop.TSHIRT -> floatArrayOf(0.64f, 0.875f, 0f, 0.028f)
        PersonTop.SHIRT -> floatArrayOf(0.80f, 0.80f, 0f, 0.014f)
        PersonTop.CARDIGAN -> floatArrayOf(0.55f, 0.45f, 0f, -0.003f)
        PersonTop.LIGHT_OUTER -> floatArrayOf(0.63f, 0.625f, 0f, 0.000f)
        PersonTop.KNIT_COAT -> floatArrayOf(0.67f, 0.60f, 0f, 0.011f)
        PersonTop.DOWN -> floatArrayOf(1.0f, 0.575f, 0f, 0.005f)
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
    val mouthY: Float,
    /** The measured cheek width (v0.1.24): the skin span at the
     * mask's height (y ≈ 0.27), as a fraction of the body width,
     * from the shipped pictures. The face box above is wider than
     * the cheeks on some bases (BODY_A's runs to 0.499 against
     * cheeks of 0.360), so the mask — which must span cheek to
     * cheek, no more — is sized from this instead. */
    val cheekW: Float
) {
    val faceW: Float get() = faceRight - faceLeft
    val faceH: Float get() = faceBottom - faceTop
}

/**
 * The measured anchor of one v0.1.26 arm layer (sleeve + arm +
 * held item as one generated picture): where the gripping hand
 * sits inside the picture ([gripX] / [gripY], asset fractions),
 * where the umbrella shaft stands ([shaftX], open umbrellas only —
 * they are anchored horizontally on the shaft, not the grip), how
 * big the picture is drawn ([dhFrac] of the person height always;
 * [dwFrac] of the body width where the batch solved a width, else
 * the asset's own aspect), and the tilt the verified previews
 * applied ([rotDeg], PIL convention — counter-clockwise positive,
 * about the grip; the composer negates it for Canvas, whose
 * positive rotation is clockwise). Measured by eye from the keyed
 * PNGs in tools/sleeve_arms/preview_sleeve_arms.py, the placement
 * source of truth.
 */
data class ArmAnchor(
    val gripX: Float,
    val gripY: Float,
    val shaftX: Float?,
    val dwFrac: Float?,
    val dhFrac: Float,
    val rotDeg: Float
)

object PersonRigs {
    fun rig(base: PersonBase): PersonRig = when (base) {
        // BODY_A's hands were re-measured in v0.1.22: the old y
        // (0.582) sat mid-forearm; the hands end at 0.638 / 0.637.
        PersonBase.BODY_A -> PersonRig(
            0.502f, 0.180f, 0.399f, 0.248f, 0.747f,
            0.754f, 0.638f, 0.241f, 0.637f,
            0.216f, 0.260f, 0.360f
        )
        PersonBase.BODY_B -> PersonRig(
            0.480f, 0.180f, 0.337f, 0.307f, 0.689f,
            0.732f, 0.630f, 0.264f, 0.630f,
            0.216f, 0.260f, 0.362f
        )
        PersonBase.BODY_C -> PersonRig(
            0.519f, 0.180f, 0.336f, 0.330f, 0.668f,
            0.893f, 0.629f, 0.106f, 0.629f,
            0.216f, 0.260f, 0.316f
        )
        PersonBase.RANK5_PORTRAIT -> PersonRig(
            0.489f, 0.180f, 0.314f, 0.306f, 0.690f,
            0.756f, 0.629f, 0.242f, 0.629f,
            0.217f, 0.260f, 0.365f
        )
        PersonBase.RANK6_PORTRAIT -> PersonRig(
            0.497f, 0.180f, 0.330f, 0.323f, 0.671f,
            0.80f, 0.63f, 0.20f, 0.63f,
            0.216f, 0.261f, 0.328f
        )
        // v0.1.27: cheekW 0.464 → 0.40. The 0.464 was the hood
        // OPENING's width, not the face's: below the mouth the
        // baked face narrows fast (the chin tucks into the hood),
        // so the mask cloth (1.02 × cheekW) overflowed onto the
        // hood wall and its ear loops drew over the hood cloth.
        // At 0.40 the cloth stays on the face and the loops hide
        // at the hood's rim. cheekW feeds only the mask width.
        // (tools/illust_fixes/ 小2.)
        PersonBase.RAINCOAT -> PersonRig(
            0.507f, 0.081f, 0.336f, 0.201f, 0.743f,
            0.848f, 0.629f, 0.148f, 0.629f,
            0.216f, 0.260f, 0.40f
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

    // ---- v0.1.25 head layers: hair + hat as ONE picture ----

    /** Pixel sizes of the head-layer assets (measured from the
     * shipped PNGs, keyed by tools/key_layers.py). */
    const val HEAD_CAP_PX_W = 975
    const val HEAD_CAP_PX_H = 728
    const val HEAD_KNIT_PX_W = 769
    const val HEAD_KNIT_PX_H = 722

    /** The face box inside each head layer, (left, top, right,
     * CHIN) in asset fractions. v0.1.28 re-measured: the bottom
     * is the chin (the jaw line where the skin component's width
     * profile collapses), NOT the skin component's bottom edge —
     * that edge is the neck cut, which sits at a different height
     * in every picture and stretched / squashed the face when the
     * box was scaled onto a base. Measured with the skin/ink
     * detector in tools/audit28/ (the eyes sit at
     * (0.433 / 0.568, 0.654) in the cap layer and
     * (0.414 / 0.584, 0.650) in the knit cap layer, symmetric
     * about each box's centre x; chin − eye = 0.194 of the asset
     * height in both layers). */
    val HEAD_CAP_FACE = floatArrayOf(0.349f, 0.556f, 0.651f, 0.848f)
    val HEAD_KNIT_FACE = floatArrayOf(0.306f, 0.551f, 0.689f, 0.845f)

    /** The eye line inside each head layer (asset fraction; the
     * measured ink-cluster centroids above). Since v0.1.28 every
     * base anchors on it — see [headLayerRect]. */
    const val HEAD_CAP_EYE = 0.654f
    const val HEAD_KNIT_EYE = 0.650f

    /** The face box a head layer must land on, per base:
     * (left, top, right, CHIN) in body fractions, measured off
     * the shipped base pictures with the same detector as the
     * layers' (tools/audit28/). This is NOT the rig's face box:
     * the rig's box is the skin component's bounds — on BODY_A
     * it is a stale v0.1.19 measurement wider than the drawn
     * face, and on the RAINCOAT it is the hood opening. The rig
     * box keeps serving the face props (mask, sunglasses); the
     * head layers answer to this table instead. The chin is
     * ≈0.288 on every base (0.279 on RANK6), the top ≈0.180. */
    fun headTarget(base: PersonBase): FloatArray = when (base) {
        PersonBase.BODY_A -> floatArrayOf(0.308f, 0.180f, 0.687f, 0.288f)
        PersonBase.BODY_B -> floatArrayOf(0.307f, 0.181f, 0.688f, 0.289f)
        PersonBase.BODY_C -> floatArrayOf(0.330f, 0.180f, 0.667f, 0.288f)
        PersonBase.RANK5_PORTRAIT ->
            floatArrayOf(0.306f, 0.181f, 0.689f, 0.289f)
        PersonBase.RANK6_PORTRAIT ->
            floatArrayOf(0.324f, 0.181f, 0.670f, 0.279f)
        PersonBase.RAINCOAT -> floatArrayOf(0.251f, 0.180f, 0.740f, 0.288f)
    }

    /** The layer's measured face box (see HEAD_CAP_FACE). */
    fun headFace(head: PersonHead): FloatArray = when (head) {
        PersonHead.CAP -> HEAD_CAP_FACE
        PersonHead.KNITCAP -> HEAD_KNIT_FACE
        PersonHead.NONE -> HEAD_CAP_FACE
    }

    /** The layer's measured eye line (see HEAD_CAP_EYE). */
    fun headEye(head: PersonHead): Float = when (head) {
        PersonHead.CAP -> HEAD_CAP_EYE
        PersonHead.KNITCAP -> HEAD_KNIT_EYE
        PersonHead.NONE -> HEAD_CAP_EYE
    }

    /**
     * Where a head layer lands: (left, top, width, height) in the
     * person rect's units ((bl, pt) its top-left, bw × ph its
     * size). v0.1.28: the scale is NON-uniform — the layer's
     * measured face box is drawn exactly onto the base's measured
     * target face box ([headTarget]: width ratio on x, height
     * ratio on y) — and the position is set by the face's centre
     * x and the eye line (the layer's eye line lands on the
     * rig's eye line, on every base including the raincoat).
     * Until v0.1.27 the scale was uniform, set by the face width
     * alone, and most bases anchored on the face top: the
     * layers' faces are drawn with slightly different proportions
     * than the bases', so switching hats changed the face's
     * height and eye position (on BODY_A the eyes landed 0.011
     * of the height low and the chin 0.033 low; the raincoat's
     * eye-anchored uniform scale stretched its face vertically).
     * The hat and hair stretch by the same few percent — the
     * mirror verification (tools/audit28/verify_faces28.py) puts
     * every base's eye spacing / eye line / chin within 0.003 of
     * the body height of the bare head. Pure math, unit-tested.
     */
    fun headLayerRect(
        head: PersonHead, base: PersonBase,
        bl: Float, pt: Float, bw: Float, ph: Float
    ): FloatArray {
        if (head == PersonHead.NONE) return floatArrayOf(bl, pt, 0f, 0f)
        val face = headFace(head)
        val eye = headEye(head)
        val t = headTarget(base)
        val w = (t[2] - t[0]) * bw / (face[2] - face[0])
        val h = (t[3] - t[1]) * ph / (face[3] - face[1])
        val left = bl + (t[0] + t[2]) / 2f * bw -
            (face[0] + face[2]) / 2f * w
        val top = pt + PersonRigs.rig(base).eyeY * ph - eye * h
        return floatArrayOf(left, top, w, h)
    }

    /** How far a head layer pokes above the person top, as a
     * fraction of the person height (0 when the head is bare):
     * [headLayerRect] solved on a unit person rect of the base's
     * own aspect. [PersonFigure.padTopRatio] pads by this. */
    fun headLayerPoke(base: PersonBase, head: PersonHead): Float {
        if (head == PersonHead.NONE) return 0f
        val r = headLayerRect(
            head, base, 0f, 0f, PersonCanvas.bodyAspect(base), 1f
        )
        return maxOf(0f, -r[1])
    }

    /** Mask (v0.1.24: placed by its CLOTH box, like the hats).
     * [MASK_W] is the cloth width / cheek width
     * ([PersonRig.cheekW]), [MASK_H] the cloth height / body
     * height, and [MASK_PAINT] the cloth box inside the asset
     * (asset fractions, measured from the shipped PNG: the white
     * cloth component — the ear loops fill the rest of the asset's
     * width and land in the hair at the face's sides). The cloth
     * top hangs [MASK_DROP] below the measured eye line
     * ([PersonRig.eyeY]) — the nose bridge — and [MASK_H] ends it
     * at ≈0.304: the jaw line measures ≈0.287 on every base, so
     * the cloth wraps just under the chin and stops clear of the
     * muffler (baked tops ≈0.33 on the portraits, MUFFLER_TOP
     * 0.305 on the layered bases). Until v0.1.23 the mask was
     * placed by the asset rect at 1.06 × the rig face width: the
     * cloth came out at only ≈0.79 of that width (a quarter of
     * the asset is ear loops), and because the asset is tall for
     * this face (cloth aspect ≈0.82 h/w against the face's
     * ≈0.46), the cloth hung 0.05–0.08 of the body height below
     * the chin — down the neck, and into the muffler on the
     * portrait bases, which is what read as wrong. */
    const val MASK_W = 1.02f
    const val MASK_H = 0.068f
    const val MASK_DROP = 0.020f
    val MASK_PAINT = floatArrayOf(0.125f, 0.027f, 0.874f, 0.973f)

    /** Sunglasses: width / face width; centered on the measured eye
     * line ([PersonRig.eyeY]) — until v0.1.20 they were centered at
     * 0.57 of the skin-box height, which is the mouth line. */
    const val SUNG_W = 1.12f

    /** Muffler (the asset is cropped to its worn length at keying
     * time): height / body height; its top. Sized down in v0.1.21
     * (0.32 → 0.27): at the old size the ring covered the whole
     * chest of the slim bodies, and its top band reached the mouth.
     * The neck towel: height / body height; its top (v0.1.24:
     * height-driven like the muffler). The asset is a wrap band
     * (its top ≈34%, the scalloped top edge and the U-curve) over
     * two hanging panels with the chest showing between them.
     * Until v0.1.23 it was width-driven (0.50 → 0.38 in v0.1.21)
     * with its top at 0.355 — below the neck base (0.337) — so
     * the band floated on the chest, clear of the neck it was
     * meant to wrap, and the panels hung to 0.57–0.60 like an
     * apron (on the portrait bases, a white apron under the baked
     * muffler). The new top (0.298 — the jaw line measures
     * ≈0.287, the muffler's top is 0.305) puts the band around
     * the neck, and the height ends the panels at 0.510, on the
     * upper chest. The width follows the asset's aspect (1.43):
     * 0.33–0.38 of the body width depending on the base. */
    const val MUFFLER_H = 0.27f
    const val MUFFLER_TOP = 0.305f
    const val TOWEL_H = 0.212f
    const val TOWEL_TOP = 0.298f

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

    // ---- v0.1.26 arm layers: sleeve + arm + item as ONE picture ----

    /** Pixel sizes of the arm-layer assets (measured from the
     * shipped PNGs, keyed in tools/sleeve_arms/). */
    fun armPx(art: PersonArt): Pair<Int, Int> = when (art) {
        PersonArt.ARM_POLO_OPEN -> 626 to 1024
        PersonArt.ARM_POLO_FOLDING -> 290 to 1024
        PersonArt.ARM_POLO_PARASOL -> 789 to 1024
        PersonArt.ARM_POLO_UCHIWA -> 379 to 487
        PersonArt.ARM_CARDIGAN_OPEN -> 626 to 1024
        PersonArt.ARM_CARDIGAN_FOLDING -> 283 to 1024
        PersonArt.ARM_KNITCOAT_OPEN -> 625 to 1024
        PersonArt.ARM_KNITCOAT_FOLDING -> 282 to 1024
        else -> 1 to 1
    }

    /** The measured anchor of one arm layer (see [ArmAnchor]).
     * Values are the placement source of truth from
     * tools/sleeve_arms/preview_sleeve_arms.py (the ARM table),
     * whose composites were verified by eye before the batch was
     * adopted. */
    fun armAnchor(art: PersonArt): ArmAnchor = when (art) {
        PersonArt.ARM_POLO_OPEN ->
            ArmAnchor(0.83f, 0.84f, 0.815f, 1.20f, 0.95f, 0f)
        PersonArt.ARM_POLO_FOLDING ->
            ArmAnchor(0.40f, 0.40f, null, null, 0.70f, 12f)
        // v0.1.28: both pictures were redrawn by edit-generating
        // the app's own summer composite (tools/audit28/): the arm
        // now hangs from the shoulder — polo sleeve, bare forearm,
        // fist at hip height. The v0.1.27 parasol (a program-drawn
        // sleeve capsule floating at head height, the arm a
        // straight stick below it) and the v0.1.27 fan (the arm
        // growing UP out of the fist, the sleeve cap at head
        // height) are gone. The parasol anchors on its shaft like
        // the open umbrellas; the fan's grip is declared a touch
        // left of its fist's centre so the sleeve lands on the
        // polo's sleeve zone (measured in the mirror trials,
        // tools/audit28/trial_arms28.py).
        PersonArt.ARM_POLO_PARASOL ->
            ArmAnchor(0.598f, 0.909f, 0.598f, 1.578f, 0.815f, 0f)
        PersonArt.ARM_POLO_UCHIWA ->
            ArmAnchor(0.235f, 0.842f, null, 0.690f, 0.351f, 0f)
        PersonArt.ARM_CARDIGAN_OPEN ->
            ArmAnchor(0.57f, 0.84f, 0.545f, 1.20f, 0.95f, 0f)
        PersonArt.ARM_CARDIGAN_FOLDING ->
            ArmAnchor(0.42f, 0.40f, null, null, 0.70f, 24f)
        PersonArt.ARM_KNITCOAT_OPEN ->
            ArmAnchor(0.79f, 0.85f, 0.775f, 1.20f, 0.95f, 0f)
        PersonArt.ARM_KNITCOAT_FOLDING ->
            ArmAnchor(0.42f, 0.40f, null, null, 0.70f, 24f)
        else -> ArmAnchor(0.5f, 0.5f, null, null, 0f, 0f)
    }

    /**
     * The arm layer serving a (top, held) pair, or null where the
     * batch has no picture — the composer then falls back to the
     * v0.1.22 grip-point drawing. Sleeve groups (the user's
     * grouping): the polo group serves the short-sleeve tops
     * (ranks 0–1: TSHIRT and SHIRT), the cardigan group the thin
     * long sleeves (ranks 2–3: CARDIGAN and LIGHT_OUTER), and the
     * knit coat is rank 4's own. The parasol and the fan exist
     * only in the polo group — the auto rules only ever raise
     * them on the hottest days; any other top asking for them
     * (the test screen can) takes the fallback.
     */
    fun armArt(top: PersonTop?, held: PersonHeld): PersonArt? {
        val polo = top == PersonTop.TSHIRT || top == PersonTop.SHIRT
        val cardigan = top == PersonTop.CARDIGAN || top == PersonTop.LIGHT_OUTER
        val knit = top == PersonTop.KNIT_COAT
        return when (held) {
            PersonHeld.OPEN -> when {
                polo -> PersonArt.ARM_POLO_OPEN
                cardigan -> PersonArt.ARM_CARDIGAN_OPEN
                knit -> PersonArt.ARM_KNITCOAT_OPEN
                else -> null
            }
            PersonHeld.FOLDED -> when {
                polo -> PersonArt.ARM_POLO_FOLDING
                cardigan -> PersonArt.ARM_CARDIGAN_FOLDING
                knit -> PersonArt.ARM_KNITCOAT_FOLDING
                else -> null
            }
            PersonHeld.PARASOL ->
                if (polo) PersonArt.ARM_POLO_PARASOL else null
            PersonHeld.UCHIWA ->
                if (polo) PersonArt.ARM_POLO_UCHIWA else null
            PersonHeld.NONE -> null
        }
    }

    /**
     * Where an arm layer lands: (left, top, width, height) in the
     * person rect's units ((bl, pt) its top-left, bw × ph its
     * size), BEFORE the layer's rotation — the composer rotates
     * about the grip point, which this rect puts exactly on the
     * rig's right hand. The open umbrellas are shaft-anchored
     * horizontally (the shaft's x IS the hand's x, as in the
     * v0.1.22 drawing) and grip-anchored vertically; everything
     * else is grip-anchored on both axes. The drawn height is
     * [ArmAnchor.dhFrac] of the person height; the width follows
     * [ArmAnchor.dwFrac] of the body width where the batch solved
     * one (the canopies are wider than the body), else the asset's
     * own aspect. Mirrors tools/sleeve_arms/preview_sleeve_arms.py.
     * Pure math, unit-tested.
     */
    fun armRect(
        art: PersonArt, rig: PersonRig,
        bl: Float, pt: Float, bw: Float, ph: Float
    ): FloatArray {
        val a = armAnchor(art)
        val (iw, ih) = armPx(art)
        val dh = a.dhFrac * ph
        val dw = a.dwFrac?.let { it * bw } ?: dh * iw / ih
        val hx = bl + rig.handRx * bw
        val hy = pt + rig.handRy * ph
        val left = hx - (a.shaftX ?: a.gripX) * dw
        val top = hy - a.gripY * dh
        return floatArrayOf(left, top, dw, dh)
    }

    /** How far an arm layer pokes above the person top, as a
     * fraction of the person height (0 when it stays inside):
     * [armRect] solved on a unit person rect of the base's own
     * aspect. Only the canopies reach up there — the folded
     * umbrella and the fan hang from the hand, far below the top,
     * and their rotation about the grip never lifts them near it.
     * [PersonFigure.padTopRatio] pads by this. */
    fun armPoke(base: PersonBase, top: PersonTop?, held: PersonHeld): Float {
        val art = armArt(top, held) ?: return 0f
        val r = armRect(
            art, PersonRigs.rig(base),
            0f, 0f, PersonCanvas.bodyAspect(base), 1f
        )
        return maxOf(0f, -r[1])
    }

    // ---- v0.1.26 umbrella variants of the portrait bases ----

    /**
     * The whole-picture variant of a base for a held state, or
     * null where the day keeps the base picture: the base is
     * swapped whole for the generated picture that already holds
     * the item, and no held item or arm layer is drawn over it.
     * The portrait / raincoat bases swap on an OPEN or FOLDED
     * (umbrella) day; since v0.1.29 the two summer layered bases
     * swap on a PARASOL or UCHIWA day — the polo arm layers for
     * those two items never sat true on the body (the user's
     * on-device checks in v0.1.26–28), so the parasol and the fan
     * now arrive as one picture with the arm already in place,
     * the way the umbrellas always did. Every other layered
     * combo keeps its arm layer (or the grip fallback).
     */
    fun variantArt(
        base: PersonBase, top: PersonTop?, held: PersonHeld
    ): PersonArt? = when (base) {
        PersonBase.RANK5_PORTRAIT -> when (held) {
            PersonHeld.OPEN -> PersonArt.FIG_RANK5_OPEN
            PersonHeld.FOLDED -> PersonArt.FIG_RANK5_FOLDING
            else -> null
        }
        PersonBase.RANK6_PORTRAIT -> when (held) {
            PersonHeld.OPEN -> PersonArt.FIG_RANK6_OPEN
            PersonHeld.FOLDED -> PersonArt.FIG_RANK6_FOLDING
            else -> null
        }
        PersonBase.RAINCOAT -> when (held) {
            PersonHeld.OPEN -> PersonArt.FIG_RAINCOAT_OPEN
            PersonHeld.FOLDED -> PersonArt.FIG_RAINCOAT_FOLDING
            else -> null
        }
        PersonBase.BODY_B -> when {
            top == PersonTop.TSHIRT -> when (held) {
                PersonHeld.PARASOL -> PersonArt.FIG_RANK0_PARASOL
                PersonHeld.UCHIWA -> PersonArt.FIG_RANK0_UCHIWA
                else -> null
            }
            else -> null
        }
        PersonBase.BODY_A -> when {
            top == PersonTop.SHIRT -> when (held) {
                PersonHeld.PARASOL -> PersonArt.FIG_RANK1_PARASOL
                PersonHeld.UCHIWA -> PersonArt.FIG_RANK1_UCHIWA
                else -> null
            }
            else -> null
        }
        else -> null
    }

    /**
     * The inner rect of a variant picture: (x0, y0, x1, y1) in the
     * variant's own fractions — where the base picture's
     * (0,0)–(1,1) lives inside it. The generated edits are not
     * rigid transforms of the bases (the figure is redrawn a
     * little smaller and lower under an open canopy), so the rect
     * is anchored on what the composer keys on: the face (skin
     * box + eye line) and the boots, measured per picture in
     * tools/measure26b.py — its detectors reproduce the shipped
     * rigs' face boxes to ±0.002 and the eye lines exactly. The
     * folded variants kept their figures in place (inner ≈
     * identity, the raincoat's frame is simply wider on the
     * right); the open ones sit at 0.83–0.90 scale with the person
     * 0.10–0.17 of the frame lower. The composer draws the
     * variant so this rect lands exactly on the standard person
     * rect, so the head layer, mask and sunglasses — all placed
     * from the base's rig — stay true.
     */
    fun variantInner(art: PersonArt): FloatArray = when (art) {
        // v0.1.27: the two OPEN portraits were regenerated from
        // their bases (the audit found them redrawn ≈20% slimmer
        // on rank 5, with shorter mufflers); the rects below are
        // re-measured on the new pictures with the measure26b
        // detectors (see docs/FEATURE_v0.1.27.md).
        PersonArt.FIG_RANK5_OPEN ->
            floatArrayOf(0.0039f, 0.0818f, 0.8056f, 0.9999f)
        PersonArt.FIG_RANK5_FOLDING ->
            floatArrayOf(0.0002f, -0.0011f, 1.0002f, 1.0000f)
        PersonArt.FIG_RANK6_OPEN ->
            floatArrayOf(0.0037f, 0.1361f, 0.8830f, 0.9999f)
        PersonArt.FIG_RANK6_FOLDING ->
            floatArrayOf(0.0016f, -0.0023f, 0.9756f, 1.0000f)
        PersonArt.FIG_RAINCOAT_OPEN ->
            floatArrayOf(0.0624f, 0.1690f, 0.7201f, 0.9998f)
        PersonArt.FIG_RAINCOAT_FOLDING ->
            floatArrayOf(-0.0001f, -0.0007f, 0.8948f, 1.0000f)
        // v0.1.29: measured on the new portraits with
        // tools/audit29/measure29.py — ink anchors (the eye
        // spacing sets the width scale, the eye line and the
        // boot line the height), because the pale summer tops
        // defeat the skin-box detector. With these rects the
        // mapped silhouettes match the bases to 0.4% at the
        // hair and 1–2% at the hips / legs (see
        // docs/FEATURE_v0.1.29.md).
        PersonArt.FIG_RANK0_PARASOL ->
            floatArrayOf(0.1951f, 0.2091f, 0.6492f, 0.9999f)
        PersonArt.FIG_RANK0_UCHIWA ->
            floatArrayOf(-0.0005f, 0.0005f, 0.9108f, 0.9992f)
        PersonArt.FIG_RANK1_PARASOL ->
            floatArrayOf(0.1889f, 0.1945f, 0.6492f, 0.9999f)
        PersonArt.FIG_RANK1_UCHIWA ->
            floatArrayOf(0.0017f, -0.0005f, 0.7745f, 1.0001f)
        else -> floatArrayOf(0f, 0f, 1f, 1f)
    }

    /** How far a variant picture extends above the person top
     * when its inner rect is landed on the person rect, as a
     * fraction of the person height (the canopy's room):
     * innerTop / innerHeight. [PersonFigure.padTopRatio] pads
     * by this. */
    fun variantPoke(
        base: PersonBase, top: PersonTop?, held: PersonHeld
    ): Float {
        val art = variantArt(base, top, held) ?: return 0f
        val r = variantInner(art)
        return maxOf(0f, r[1] / (r[3] - r[1]))
    }
}

/**
 * Draws a [PersonSpec] as a composite of the generated assets: the
 * base (a layered body + top, the hybrid rank-5 portrait, or the
 * raincoat outfit — on an umbrella day a portrait / raincoat base
 * is swapped whole for its umbrella variant, v0.1.26), then the
 * arm layer where the batch has one (v0.1.26: sleeve + arm + held
 * item as one picture, over body + top), then neck, mittens, head
 * and face layers, then the held item — drawn only where neither
 * an arm layer nor a variant served, as the grip-point fallback —
 * and finally the code-drawn sweat / shiver signs. One routine for
 * both hosts — the in-app [PersonView] and the home-screen widget
 * (which renders it into a Bitmap, since RemoteViews cannot host a
 * custom View).
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
        PersonArt.LAYER_HEAD_CAP -> R.drawable.layer_head_cap
        PersonArt.LAYER_HEAD_KNITCAP -> R.drawable.layer_head_knitcap
        PersonArt.LAYER_HEAD_CAP_RAIN -> R.drawable.layer_head_cap_rain
        PersonArt.LAYER_HEAD_KNITCAP_RAIN -> R.drawable.layer_head_knitcap_rain
        PersonArt.PROP_MASK -> R.drawable.prop_mask
        PersonArt.PROP_SUNGLASSES -> R.drawable.prop_sunglasses
        PersonArt.PROP_MUFFLER -> R.drawable.prop_muffler
        PersonArt.PROP_MITTEN_L -> R.drawable.prop_mitten_l
        PersonArt.PROP_MITTEN_R -> R.drawable.prop_mitten_r
        PersonArt.PROP_TOWEL -> R.drawable.prop_towel
        PersonArt.PROP_PARASOL -> R.drawable.prop_parasol
        PersonArt.PROP_UCHIWA -> R.drawable.prop_uchiwa
        PersonArt.PROP_FOLDING -> R.drawable.prop_folding
        PersonArt.ARM_POLO_OPEN -> R.drawable.arm_polo_umbrella_open
        PersonArt.ARM_POLO_FOLDING -> R.drawable.arm_polo_umbrella_folding
        PersonArt.ARM_POLO_PARASOL -> R.drawable.arm_polo_parasol
        PersonArt.ARM_POLO_UCHIWA -> R.drawable.arm_polo_uchiwa
        PersonArt.ARM_CARDIGAN_OPEN -> R.drawable.arm_cardigan_umbrella_open
        PersonArt.ARM_CARDIGAN_FOLDING -> R.drawable.arm_cardigan_umbrella_folding
        PersonArt.ARM_KNITCOAT_OPEN -> R.drawable.arm_knitcoat_umbrella_open
        PersonArt.ARM_KNITCOAT_FOLDING -> R.drawable.arm_knitcoat_umbrella_folding
        PersonArt.FIG_RANK5_OPEN -> R.drawable.rank5_umbrella_open
        PersonArt.FIG_RANK5_FOLDING -> R.drawable.rank5_umbrella_folding
        PersonArt.FIG_RANK6_OPEN -> R.drawable.rank6_umbrella_open
        PersonArt.FIG_RANK6_FOLDING -> R.drawable.rank6_umbrella_folding
        PersonArt.FIG_RAINCOAT_OPEN -> R.drawable.raincoat_umbrella_open
        PersonArt.FIG_RAINCOAT_FOLDING -> R.drawable.raincoat_umbrella_folding
        PersonArt.FIG_RANK0_PARASOL -> R.drawable.rank0_parasol
        PersonArt.FIG_RANK0_UCHIWA -> R.drawable.rank0_uchiwa
        PersonArt.FIG_RANK1_PARASOL -> R.drawable.rank1_parasol
        PersonArt.FIG_RANK1_UCHIWA -> R.drawable.rank1_uchiwa
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
        // v0.1.26: on an umbrella day a portrait / raincoat base is
        // swapped whole for its umbrella variant — a generated
        // picture of the same figure already holding the umbrella.
        // The variant is drawn so its INNER rect (where the base
        // picture lives inside it; [PersonAnchors.variantInner])
        // lands exactly on the standard person rect, so every rig
        // anchor below stays true and no held item is drawn.
        val variant = PersonAnchors.variantArt(spec.base, spec.top, spec.held)
        val bl: Float
        val bw: Float
        if (variant != null) {
            val img = bmp(ctx, variant)
            val inner = PersonAnchors.variantInner(variant)
            bw = ph * PersonCanvas.bodyAspect(spec.base)
            bl = left + (width - bw) / 2f
            val dw = bw / (inner[2] - inner[0])
            val dh = ph / (inner[3] - inner[1])
            val dl = bl - inner[0] * dw
            val dt = pt - inner[1] * dh
            canvas.drawBitmap(img, null, RectF(dl, dt, dl + dw, dt + dh), paint)
        } else if (spec.base == PersonBase.RANK5_PORTRAIT ||
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

        // ---- arm layer (v0.1.26: sleeve + arm + held item as one
        //      picture, drawn over body + top. The sleeve arrives
        //      already wearing the day's top and covers the body's
        //      own arm, so the arm is never doubled. Where the
        //      batch has no picture for the combo, [arm] is null
        //      and the held slot below keeps the v0.1.22
        //      grip-point drawing as the fallback. A whole-picture
        //      variant suppresses the arm layer: the portrait
        //      already wears its arm holding the item (v0.1.29 —
        //      before, the polo parasol / fan arm would have been
        //      drawn over the new portraits) ----
        val arm = if (variant == null) {
            PersonAnchors.armArt(spec.top, spec.held)
        } else {
            null
        }
        if (arm != null) {
            val img = bmp(ctx, arm)
            val a = PersonAnchors.armAnchor(arm)
            val r = PersonAnchors.armRect(arm, rig, bl, pt, bw, ph)
            if (a.rotDeg != 0f) {
                // Rotated about the grip point, which [armRect]
                // puts exactly on the hand. The batch's angles are
                // PIL's (counter-clockwise positive); Canvas
                // rotates clockwise, so the sign flips.
                canvas.save()
                canvas.translate(handX, handY)
                canvas.rotate(-a.rotDeg)
                canvas.drawBitmap(
                    img, null,
                    RectF(
                        -a.gripX * r[2], -a.gripY * r[3],
                        -a.gripX * r[2] + r[2], -a.gripY * r[3] + r[3]
                    ),
                    paint
                )
                canvas.restore()
            } else {
                canvas.drawBitmap(
                    img, null,
                    RectF(r[0], r[1], r[0] + r[2], r[1] + r[3]), paint
                )
            }
        }
        val hybrid = spec.base == PersonBase.RANK5_PORTRAIT ||
            spec.base == PersonBase.RANK6_PORTRAIT
        // The raincoat is a finished full-body picture (v0.1.20):
        // no neck / mitten / sign layers over it, whatever the spec
        // fields say — the guarantee lives here, not only in the
        // spec's derivation, so a hand-built spec cannot leak them.
        // Head, face and held layers still go over it as usual.
        val raincoatBase = spec.base == PersonBase.RAINCOAT

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
            PersonNeck.TOWEL -> {
                // Height-driven like the muffler (v0.1.24): the
                // vertical story (band at the neck, panels ending
                // on the upper chest) is the same on every base;
                // the width follows the asset's aspect.
                val img = bmp(ctx, PersonArt.PROP_TOWEL)
                val h = PersonAnchors.TOWEL_H * ph
                val w = h * img.width / img.height
                canvas.drawBitmap(
                    img, null,
                    RectF(bx(0.5f) - w / 2f, by(PersonAnchors.TOWEL_TOP),
                        bx(0.5f) + w / 2f, by(PersonAnchors.TOWEL_TOP) + h),
                    paint
                )
            }
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

        // ---- head (v0.1.25: hair + hat as ONE picture — the head
        //      layer replaces the base's hair wholesale, its face
        //      anchored onto the rig's; see
        //      [PersonAnchors.headLayerRect]. The standalone cap /
        //      knit cap props are retired: laid over the hair
        //      separately they kept sitting slightly off it, so
        //      the hat now arrives already worn) ----
        if (spec.head != PersonHead.NONE) {
            // v0.1.27: the raincoat base wears its own head-layer
            // variants, clipped to the hood opening — the standard
            // layers' hair spilled over the hood's rim and the hat
            // sat on top of the hood. Same canvas and face
            // anchors, so the rect below is unchanged.
            val rain = spec.base == PersonBase.RAINCOAT
            val art = when {
                spec.head == PersonHead.CAP && rain ->
                    PersonArt.LAYER_HEAD_CAP_RAIN
                spec.head == PersonHead.CAP ->
                    PersonArt.LAYER_HEAD_CAP
                rain -> PersonArt.LAYER_HEAD_KNITCAP_RAIN
                else -> PersonArt.LAYER_HEAD_KNITCAP
            }
            val img = bmp(ctx, art)
            val r = PersonAnchors.headLayerRect(
                spec.head, spec.base, bl, pt, bw, ph
            )
            canvas.drawBitmap(
                img, null,
                RectF(r[0], r[1], r[0] + r[2], r[1] + r[3]), paint
            )
        }

        // ---- face (placed from the measured feature lines,
        //      v0.1.21: the sunglasses on the eye line, the mask
        //      hanging just under it; v0.1.24: the mask is placed
        //      by its cloth box — see [PersonAnchors.MASK_PAINT]) ----
        if (spec.mask) {
            val img = bmp(ctx, PersonArt.PROP_MASK)
            val p = PersonAnchors.MASK_PAINT
            val rectW = PersonAnchors.MASK_W * rig.cheekW * bw / (p[2] - p[0])
            val rectH = PersonAnchors.MASK_H * ph / (p[3] - p[1])
            val mLeft = bx(rig.faceCx) - (p[0] + p[2]) / 2f * rectW
            val mTop = by(rig.eyeY + PersonAnchors.MASK_DROP) - p[1] * rectH
            canvas.drawBitmap(
                img, null,
                RectF(mLeft, mTop, mLeft + rectW, mTop + rectH), paint
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

        // ---- held (the v0.1.22 grip-point drawing; since v0.1.26
        //      it is the fallback, serving exactly the combos the
        //      arm layers and the umbrella variants do not) ----
        if (variant == null && arm == null) when (spec.held) {
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
