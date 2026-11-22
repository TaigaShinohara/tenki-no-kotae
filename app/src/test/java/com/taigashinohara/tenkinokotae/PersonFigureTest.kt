package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「今日の人」 (v0.1.14): the pure variation function. The drawing is
 * not tested (it is a picture); what the picture must *say* is —
 * garment per rank, the umbrella in hand, the hat threshold, and the
 * cold / heat marks.
 */
class PersonFigureTest {

    private fun spec(
        rank: Int,
        level: Rules.UmbrellaLevel = Rules.UmbrellaLevel.NONE,
        uvMax: Double = 0.0
    ) = PersonFigure.spec(rank, level, uvMax)

    @Test fun garment_followsRank() {
        assertEquals(PersonGarment.TEE_SHORTS, spec(0).garment)
        assertEquals(PersonGarment.SHIRT, spec(1).garment)
        assertEquals(PersonGarment.CARDIGAN, spec(2).garment)
        assertEquals(PersonGarment.SWEATER_OUTER, spec(3).garment)
        assertEquals(PersonGarment.COAT, spec(4).garment)
        assertEquals(PersonGarment.COAT_SCARF, spec(5).garment)
        assertEquals(PersonGarment.DOWN_GLOVES, spec(6).garment)
    }

    @Test fun garment_rankIsClamped() {
        assertEquals(PersonGarment.TEE_SHORTS, spec(-2).garment)
        assertEquals(PersonGarment.DOWN_GLOVES, spec(9).garment)
    }

    @Test fun umbrella_followsLevel() {
        assertEquals(PersonUmbrella.OPEN, spec(3, Rules.UmbrellaLevel.NEED).umbrella)
        assertEquals(PersonUmbrella.FOLDED, spec(3, Rules.UmbrellaLevel.FOLDING).umbrella)
        assertEquals(PersonUmbrella.NONE, spec(3, Rules.UmbrellaLevel.NONE).umbrella)
    }

    @Test fun hat_fromUvMustLevel() {
        // Rules.sunscreen: 必須 starts at UV 6. The hat follows it.
        assertFalse(spec(0, uvMax = 5.9).hat)
        assertTrue(spec(0, uvMax = 6.0).hat)
        assertTrue(spec(0, uvMax = 8.0).hat)
    }

    @Test fun shiver_onlyOnColdRanks() {
        assertFalse(spec(4).shiver)
        assertTrue(spec(5).shiver)
        assertTrue(spec(6).shiver)
    }

    @Test fun sweat_onlyOnHottestRank() {
        assertTrue(spec(0).sweat)
        assertFalse(spec(1).sweat)
        assertFalse(spec(6).sweat)
    }

    @Test fun coldestDay_allColdSignsTogether() {
        val s = spec(6, Rules.UmbrellaLevel.NEED, uvMax = 1.0)
        assertEquals(PersonGarment.DOWN_GLOVES, s.garment)
        assertEquals(PersonUmbrella.OPEN, s.umbrella)
        assertTrue(s.shiver)
        assertFalse(s.sweat)
        assertFalse(s.hat)
    }

    @Test fun hottestDay_allHeatSignsTogether() {
        val s = spec(0, Rules.UmbrellaLevel.NONE, uvMax = 9.0)
        assertEquals(PersonGarment.TEE_SHORTS, s.garment)
        assertTrue(s.sweat)
        assertTrue(s.hat)
        assertFalse(s.shiver)
    }

    // ---- v0.1.16: the spec now picks generated picture assets ----

    @Test fun art_followsGarment() {
        // One portrait per garment, in rank order — the compositor's
        // base layer. A mix-up here would dress the person in the
        // wrong rank's clothes while the text says another.
        assertEquals(PersonArt.RANK0, PersonFigure.art(PersonGarment.TEE_SHORTS))
        assertEquals(PersonArt.RANK1, PersonFigure.art(PersonGarment.SHIRT))
        assertEquals(PersonArt.RANK2, PersonFigure.art(PersonGarment.CARDIGAN))
        assertEquals(PersonArt.RANK3, PersonFigure.art(PersonGarment.SWEATER_OUTER))
        assertEquals(PersonArt.RANK4, PersonFigure.art(PersonGarment.COAT))
        assertEquals(PersonArt.RANK5, PersonFigure.art(PersonGarment.COAT_SCARF))
        assertEquals(PersonArt.RANK6, PersonFigure.art(PersonGarment.DOWN_GLOVES))
    }

    // ---- v0.1.18: the person's height is sized from the leftover ----

    @Test fun height_standardColumnTypicalDay() {
        // The standard column (700dp) and a typical day's fixed
        // parts as homeFixedHeightDp sums them (340dp = column
        // padding + city 53, the screenshot's two-line conclusion
        // 98, a gap note 34, chips at a two-line answer 96, two
        // small lines 59) leave 360dp; the person takes 60% of it —
        // about 200dp, up from the fixed 148dp step.
        assertEquals(216, PersonFigure.heightDp(700f, 340f))
    }

    @Test fun height_minimumColumnTypicalDay() {
        // The minimum column (620dp), same typical day: 280dp left,
        // 60% is 168 — the smaller screen still gets a present
        // figure, not the old 128dp step.
        assertEquals(168, PersonFigure.heightDp(620f, 340f))
    }

    @Test fun height_lightDayHitsTheMaximum() {
        // The v0.1.17 screenshot's own day (no gap note: fixed
        // 306dp) in the standard column leaves 394dp; 60% of it
        // overshoots the band, so the maximum answers.
        assertEquals(
            PersonFigure.HEIGHT_MAX_DP,
            PersonFigure.heightDp(700f, 306f)
        )
        // The same light day in the minimum column: 188dp.
        assertEquals(188, PersonFigure.heightDp(620f, 306f))
    }

    @Test fun height_fitCapBeatsMinimumOnTheFullestDay() {
        // Every fixed part at its maximum at once (465dp) in the
        // minimum column leaves 155dp: the share (93) and even the
        // minimum (150) would crowd out the reserve, so the cap
        // sizes the person to what is left minus the reserve — and
        // the stack still fits the column.
        val h = PersonFigure.heightDp(620f, 465f)
        assertEquals(143, h)
        assertTrue(465 + h <= 620)
    }

    @Test fun height_clampedAtMaximumOnTallColumns() {
        // A tall column with a light day leaves far more than the
        // person may take: the maximum keeps the figure from
        // growing without bound.
        assertEquals(
            PersonFigure.HEIGHT_MAX_DP,
            PersonFigure.heightDp(1000f, 300f)
        )
    }

    @Test fun height_neverOverflowsTheColumn() {
        // For any plausible column / fixed pair with something left,
        // fixed + person stays inside the column, with the reserve
        // to spare whenever the leftover allows it.
        for (column in listOf(500f, 620f, 700f, 800f, 1000f)) {
            for (fixed in listOf(280f, 353f, 420f, 465f)) {
                val h = PersonFigure.heightDp(column, fixed)
                assertTrue("h=$h column=$column fixed=$fixed", h >= 0)
                assertTrue(
                    "fixed + h <= column (column=$column fixed=$fixed h=$h)",
                    fixed + h <= column
                )
            }
        }
    }

    @Test fun height_growsWithTheLeftover() {
        // The share rule, monotonically: more room (a taller column
        // or a lighter day) never makes the person smaller.
        assertTrue(PersonFigure.heightDp(700f, 340f) > PersonFigure.heightDp(620f, 340f))
        assertTrue(PersonFigure.heightDp(700f, 300f) > PersonFigure.heightDp(700f, 400f))
    }

    @Test fun height_degenerateColumnKeepsTheMinimum() {
        // Fixed parts alone exceeding the column is a degenerate
        // window; the stack is over budget no matter what, and the
        // defined answer is the minimum.
        assertEquals(
            PersonFigure.HEIGHT_MIN_DP,
            PersonFigure.heightDp(400f, 465f)
        )
    }

    @Test fun anchors_stayInsideThePersonRect() {
        // The hand anchors are measured fractions of the person rect;
        // one outside 0..1 would put the umbrella's shaft end (and
        // the folded umbrella) off the figure entirely.
        for (g in PersonGarment.values()) {
            val (x, y) = PersonAnchors.handR(g)
            assertTrue("$g hand x in range", x in 0f..1f)
            assertTrue("$g hand y in range", y in 0f..1f)
        }
        assertTrue(PersonAnchors.HEAD_CX in 0f..1f)
        assertTrue(PersonAnchors.HAT_BOTTOM in 0f..1f)
        assertTrue(PersonAnchors.UMB_SHAFT_X in 0f..1f)
    }

    // ---- v0.1.19: the layered outfit ----

    private fun spec19(
        rank: Int,
        level: Rules.UmbrellaLevel = Rules.UmbrellaLevel.NONE,
        uvMax: Double = 0.0,
        snow: Boolean = false,
        feel: Double? = null,
        humidity: Double? = null
    ) = PersonFigure.spec(rank, level, uvMax, snow, feel, humidity)

    @Test fun outfit_layersPerRank() {
        assertEquals(PersonBase.BODY_B to PersonTop.TSHIRT, PersonFigure.outfitFor(0, false))
        assertEquals(PersonBase.BODY_A to PersonTop.SHIRT, PersonFigure.outfitFor(1, false))
        assertEquals(PersonBase.BODY_A to PersonTop.CARDIGAN, PersonFigure.outfitFor(2, false))
        assertEquals(PersonBase.BODY_A to PersonTop.LIGHT_OUTER, PersonFigure.outfitFor(3, false))
        assertEquals(PersonBase.BODY_A to PersonTop.KNIT_COAT, PersonFigure.outfitFor(4, false))
        // Ranks 5 and 6 are the hybrids: their v0.1.16 portraits, no
        // top over them (rank 6 joined in v0.1.21 — the down top is
        // a closed product shot that hid the face).
        assertEquals(PersonBase.RANK5_PORTRAIT to null, PersonFigure.outfitFor(5, false))
        assertEquals(PersonBase.RANK6_PORTRAIT to null, PersonFigure.outfitFor(6, false))
        // The spec agrees with the outfit table.
        for (r in 0..6) {
            val s = spec19(r)
            assertEquals(PersonFigure.outfitFor(r, false).first, s.base)
            assertEquals(PersonFigure.outfitFor(r, false).second, s.top)
        }
    }

    @Test fun raincoat_onlyOnNeedDaysInMidRanks() {
        for (r in 2..4) {
            val s = spec19(r, Rules.UmbrellaLevel.NEED)
            assertEquals("rank $r", PersonBase.RAINCOAT, s.base)
            assertEquals("rank $r", null, s.top)
            assertEquals("rank $r", PersonHeld.OPEN, s.held)
        }
        // Outside the mid ranks the coat stays the rank's own, and a
        // folding day never brings the raincoat out.
        assertEquals(PersonBase.BODY_A, spec19(1, Rules.UmbrellaLevel.NEED).base)
        assertEquals(PersonBase.RANK5_PORTRAIT, spec19(5, Rules.UmbrellaLevel.NEED).base)
        assertEquals(PersonBase.RANK6_PORTRAIT, spec19(6, Rules.UmbrellaLevel.NEED).base)
        assertEquals(PersonBase.BODY_A, spec19(3, Rules.UmbrellaLevel.FOLDING).base)
    }

    @Test fun knitcap_onSnowDaysBeatsTheCap() {
        val s = spec19(3, uvMax = 9.0, snow = true)
        assertEquals(PersonHead.KNITCAP, s.head)
        assertFalse(s.hat)
        assertEquals(PersonHead.CAP, spec19(3, uvMax = 9.0, snow = false).head)
        assertEquals(PersonHead.NONE, spec19(3, uvMax = 2.0, snow = false).head)
    }

    @Test fun sunglasses_fromUv8() {
        assertFalse(spec19(0, uvMax = 7.9).sunglasses)
        assertTrue(spec19(0, uvMax = 8.0).sunglasses)
    }

    @Test fun parasol_needsUv8AndNoRainUmbrella() {
        assertEquals(PersonHeld.PARASOL, spec19(2, uvMax = 8.0).held)
        assertEquals(PersonHeld.PARASOL, spec19(0, uvMax = 9.5).held)
        // The rain umbrella always wins over the parasol.
        assertEquals(
            PersonHeld.OPEN,
            spec19(2, Rules.UmbrellaLevel.NEED, uvMax = 9.0).held
        )
        assertEquals(
            PersonHeld.FOLDED,
            spec19(2, Rules.UmbrellaLevel.FOLDING, uvMax = 9.0).held
        )
        // Below UV 8 there is no parasol (and only rank 0 fans itself).
        assertEquals(PersonHeld.NONE, spec19(2, uvMax = 7.9).held)
    }

    @Test fun uchiwa_onlyHottestRankWithoutUmbrella() {
        assertEquals(PersonHeld.UCHIWA, spec19(0, uvMax = 3.0).held)
        assertEquals(PersonHeld.NONE, spec19(1, uvMax = 3.0).held)
        assertEquals(
            PersonHeld.FOLDED,
            spec19(0, Rules.UmbrellaLevel.FOLDING, uvMax = 3.0).held
        )
    }

    @Test fun towel_needsHottestRankAnd33Degrees() {
        assertEquals(PersonNeck.TOWEL, spec19(0, feel = 33.0).neck)
        assertEquals(PersonNeck.TOWEL, spec19(0, feel = 36.5).neck)
        assertEquals(PersonNeck.NONE, spec19(0, feel = 32.9).neck)
        // No daytime data, no towel — the rule never guesses.
        assertEquals(PersonNeck.NONE, spec19(0, feel = null).neck)
        assertEquals(PersonNeck.NONE, spec19(1, feel = 35.0).neck)
    }

    @Test fun mask_needsColdRankAndDryAir() {
        assertTrue(spec19(4, humidity = 39.9).mask)
        assertTrue(spec19(6, humidity = 20.0).mask)
        assertFalse(spec19(4, humidity = 40.0).mask)
        assertFalse(spec19(3, humidity = 20.0).mask)
        // A source without real humidity never fires the mask.
        assertFalse(spec19(5, humidity = null).mask)
    }

    @Test fun mufflerAndMittens_onTheColdRanks() {
        assertEquals(PersonNeck.MUFFLER, spec19(5).neck)
        assertEquals(PersonNeck.MUFFLER, spec19(6).neck)
        assertEquals(PersonNeck.NONE, spec19(4).neck)
        assertTrue(spec19(6).mittens)
        assertFalse(spec19(5).mittens)
    }

    @Test fun padTop_followsTheTallestProp() {
        assertEquals(0.34f, PersonFigure.padTopRatio(spec19(2, uvMax = 8.0)), 0.001f)
        // v0.1.25: the head layers are tall pictures (hair + hat),
        // so the pad is the layer's measured poke over the person
        // rect, per base — on BODY_A both layers poke ≈0.09.
        assertEquals(0.10f, PersonFigure.padTopRatio(spec19(3, snow = true)), 0.001f)
        assertEquals(0.10f, PersonFigure.padTopRatio(spec19(3, uvMax = 7.0)), 0.001f)
        assertEquals(0f, PersonFigure.padTopRatio(spec19(3)), 0.001f)
    }

    @Test fun height_withPadBudgetsTheWholeBlock() {
        // padRatio 0 must reproduce the v0.1.18 numbers exactly.
        for ((c, f) in listOf(700f to 340f, 620f to 340f, 620f to 465f, 1000f to 300f)) {
            assertEquals(PersonFigure.heightDp(c, f), PersonFigure.heightDp(c, f, 0f))
        }
        // A parasol day: the person shrinks so person + pad still
        // fits the same share of the leftover.
        val h = PersonFigure.heightDp(700f, 340f, 0.34f)
        assertEquals(161, h)
        assertTrue(340f + h * 1.34f <= 700f)
        // And never overflows the column, pad included.
        for (column in listOf(500f, 620f, 700f, 800f)) {
            for (fixed in listOf(280f, 420f, 465f)) {
                val hh = PersonFigure.heightDp(column, fixed, 0.34f)
                assertTrue("h=$hh column=$column fixed=$fixed", hh >= 0)
                assertTrue(
                    "fixed + block <= column (column=$column fixed=$fixed h=$hh)",
                    fixed + hh * 1.34f <= column + 0.5f
                )
            }
        }
    }

    // ---- v0.1.20: outfit-linked elements derive from the outfit ----

    @Test fun outfitLinked_followsTheOutfitAlone() {
        // Rank 0 carries its sweat and nothing else — in particular
        // no muffler, whatever the day around it is doing.
        val l0 = PersonFigure.outfitLinked(0, false)
        assertEquals(PersonNeck.NONE, l0.neck)
        assertFalse(l0.mittens)
        assertFalse(l0.shiver)
        assertTrue(l0.sweat)
        // The mid ranks carry nothing.
        for (r in 1..4) {
            val l = PersonFigure.outfitLinked(r, false)
            assertEquals("rank $r", PersonNeck.NONE, l.neck)
            assertFalse("rank $r", l.mittens)
            assertFalse("rank $r", l.shiver)
            assertFalse("rank $r", l.sweat)
        }
        // Rank 5: muffler + shiver, bare hands. Rank 6 adds mittens.
        val l5 = PersonFigure.outfitLinked(5, false)
        assertEquals(PersonNeck.MUFFLER, l5.neck)
        assertFalse(l5.mittens)
        assertTrue(l5.shiver)
        assertFalse(l5.sweat)
        val l6 = PersonFigure.outfitLinked(6, false)
        assertEquals(PersonNeck.MUFFLER, l6.neck)
        assertTrue(l6.mittens)
        assertTrue(l6.shiver)
        assertFalse(l6.sweat)
    }

    @Test fun outfitLinked_raincoatSuppressesEverything() {
        // The raincoat is a finished full-body picture: no muffler,
        // no mittens, no signs — at any rank, cold ones included.
        for (r in 0..6) {
            val l = PersonFigure.outfitLinked(r, true)
            assertEquals("rank $r", PersonNeck.NONE, l.neck)
            assertFalse("rank $r", l.mittens)
            assertFalse("rank $r", l.shiver)
            assertFalse("rank $r", l.sweat)
        }
    }

    @Test fun spec_agreesWithOutfitLinked() {
        // The auto spec's linked fields are the outfit derivation
        // itself (no weather inputs in play), so the test screen
        // and Home can never drift apart on what an outfit carries.
        for (r in 0..6) {
            val s = spec19(r)
            val l = PersonFigure.outfitLinked(r, false)
            assertEquals("rank $r neck", l.neck, s.neck)
            assertEquals("rank $r mittens", l.mittens, s.mittens)
            assertEquals("rank $r shiver", l.shiver, s.shiver)
            assertEquals("rank $r sweat", l.sweat, s.sweat)
        }
    }

    @Test fun raincoatSpec_carriesNoLinkedElements() {
        // Home's own guarantee: on a raincoat day the spec itself
        // already carries no muffler / mittens / signs (the
        // composer's suppression is the second line of defence).
        for (r in 2..4) {
            val s = spec19(r, Rules.UmbrellaLevel.NEED)
            assertEquals("rank $r", PersonBase.RAINCOAT, s.base)
            assertEquals("rank $r", PersonNeck.NONE, s.neck)
            assertFalse("rank $r", s.mittens)
            assertFalse("rank $r", s.shiver)
            assertFalse("rank $r", s.sweat)
        }
        // Even with a cold, dry, snowy day stacked around it, the
        // raincoat spec grows no neck layer and no signs.
        val s = spec19(4, Rules.UmbrellaLevel.NEED, uvMax = 1.0,
            snow = true, humidity = 20.0)
        assertEquals(PersonBase.RAINCOAT, s.base)
        assertEquals(PersonNeck.NONE, s.neck)
        assertFalse(s.shiver)
        assertFalse(s.sweat)
        assertFalse(s.mittens)
    }

    // ---- v0.1.21: the face stays visible ----

    @Test fun faceAnchors_keepEyesAndMouthClear() {
        // The v0.1.21 audit invariants, measured off the shipped
        // pictures: the mask starts above the mouth, and the
        // muffler's top band starts below it — on every base. (The
        // portrait bases draw neither muffler nor mask from these
        // anchors the same way, but the invariants are kept uniform
        // so a hand-built spec cannot break them either.) The hats
        // are head layers since v0.1.25 and carry their own face
        // invariants — see headLayers_anchorTheFaceOntoTheRig.
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            assertTrue("$base eye above mouth", rig.eyeY < rig.mouthY)
            assertTrue(
                "$base mask starts above the mouth",
                rig.eyeY + PersonAnchors.MASK_DROP < rig.mouthY
            )
            assertTrue(
                "$base muffler starts below the mouth",
                PersonAnchors.MUFFLER_TOP > rig.mouthY
            )
        }
    }

    @Test fun padTopRatio_parasolStillBeatsTheCap() {
        // Cap + parasol together: the parasol's tall pad wins.
        val s = spec19(0, uvMax = 9.0).copy(held = PersonHeld.PARASOL)
        assertEquals(PersonHead.CAP, s.head)
        assertEquals(0.34f, PersonFigure.padTopRatio(s), 0.001f)
    }

    @Test fun rigsAndCanvasBoxes_stayInRange() {
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            assertTrue("$base face", rig.faceLeft < rig.faceRight)
            assertTrue("$base face", rig.faceTop < rig.faceBottom)
            for (v in listOf(
                rig.faceCx, rig.faceTop, rig.faceBottom, rig.faceLeft, rig.faceRight,
                rig.handRx, rig.handRy, rig.handLx, rig.handLy,
                rig.eyeY, rig.mouthY, rig.cheekW
            )) {
                assertTrue("$base rig value $v in range", v in 0f..1f)
            }
        }
        val canvasArts = listOf(
            PersonArt.BODY_A, PersonArt.BODY_B, PersonArt.BODY_C,
            PersonArt.TOP_TSHIRT, PersonArt.TOP_SHIRT, PersonArt.TOP_CARDIGAN,
            PersonArt.TOP_LIGHTOUTER, PersonArt.TOP_KNITCOAT, PersonArt.TOP_DOWN,
            PersonArt.OUTFIT_RAINCOAT
        )
        for (art in canvasArts) {
            val b = PersonCanvas.box(art)
            assertTrue("$art box", b[0] >= 0f && b[1] >= 0f && b[2] <= 1f && b[3] <= 1f)
            assertTrue("$art box", b[0] < b[2] && b[1] < b[3])
        }
    }

    // ---- v0.1.22: grip-point anchoring and neck calibration ----

    @Test fun rigHands_sitAtTheArmEnds() {
        // Every base's hands were measured at the arm ends, in the
        // 0.60–0.66 band of the body height. BODY_A's rig sat at
        // 0.582 (mid-forearm) until v0.1.22, which dragged every
        // hand-anchored part on ranks 1–4 up off the hand.
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            assertTrue("$base right hand ${rig.handRy}", rig.handRy in 0.60f..0.66f)
            assertTrue("$base left hand ${rig.handLy}", rig.handLy in 0.60f..0.66f)
        }
    }

    @Test fun gripPlacement_putsTheGripExactlyOnTheHand() {
        // The composer places the fan and the folded umbrella
        // through gripOrigin: the prop's grip point must come out
        // exactly on the rig hand, on every base.
        val grips = listOf(
            PersonAnchors.UCHIWA_GRIP_X to PersonAnchors.UCHIWA_GRIP_Y,
            PersonAnchors.FOLD2_GRIP_X to PersonAnchors.FOLD2_GRIP_Y
        )
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            for ((gx, gy) in grips) {
                val (l, t) = PersonAnchors.gripOrigin(gx, gy, 37f, 210f, rig.handRx, rig.handRy)
                    .let { it[0] to it[1] }
                assertEquals("$base grip x", rig.handRx, l + gx * 37f, 0.0001f)
                assertEquals("$base grip y", rig.handRy, t + gy * 210f, 0.0001f)
            }
        }
    }

    @Test fun gripAnchors_sitOnTheMeasuredHandles() {
        // The fan's handle spans y 0.79–1.00 of its asset at
        // x ≈ 0.50; the folded umbrella is gripped on the straight
        // above its J-hook. A grip outside those spans would put
        // the disc / the canopy body in the hand instead.
        assertTrue(PersonAnchors.UCHIWA_GRIP_X in 0.45f..0.55f)
        assertTrue(PersonAnchors.UCHIWA_GRIP_Y in 0.79f..1.0f)
        assertTrue(PersonAnchors.FOLD2_GRIP_X in 0.45f..0.60f)
        assertTrue(PersonAnchors.FOLD2_GRIP_Y in 0.85f..0.95f)
    }

    @Test fun topCollars_landAtTheNeckBase() {
        // Each top's drawn collar top (its canvas box top, run
        // through topCal) must land on the neck base of its body —
        // the measured neck spans 0.298–0.337 of the body height.
        // Until v0.1.22 the uncalibrated tops rode up to 0.20–0.27:
        // the polo's collar tips touched the jaw and the light
        // coat's collar peaks covered the mouth.
        val pairs = (0..4).map { r ->
            val (base, top) = PersonFigure.outfitFor(r, false)
            top!! to base
        } + (PersonTop.DOWN to PersonBase.BODY_C)
        for ((top, base) in pairs) {
            val tb = PersonCanvas.box(PersonCanvas.topArt(top))
            val bb = PersonCanvas.box(PersonCanvas.bodyArt(base))
            val bh = bb[3] - bb[1]
            val topF = (tb[1] - bb[1]) / bh
            val botF = (tb[3] - bb[1]) / bh
            val cen = (topF + botF) / 2f
            val cal = PersonCanvas.topCal(top)
            val collar = cen + cal[3] + (topF - cen) * cal[1]
            val hem = cen + cal[3] + (botF - cen) * cal[1]
            assertTrue("$top collar $collar at the neck base", collar in 0.315f..0.355f)
            assertTrue("$top hem $hem at a worn length", hem in 0.60f..0.85f)
        }
    }

    // ---- v0.1.23: the tops fit the body ----
    // (the hats sat on the hair until v0.1.25 replaced them with
    // head layers — the next section)

    @Test fun topHems_keepTheirWornLengths() {
        // The v0.1.23 fit pass, pinned per top: each drawn hem
        // (canvas box bottom through topCal, the same math as
        // topCollars_landAtTheNeckBase) keeps the garment's worn
        // length — the polo no longer hangs past the hands, the
        // knit coat no longer reaches the shins.
        val bands = mapOf(
            PersonTop.TSHIRT to (0.68f..0.73f),
            PersonTop.SHIRT to (0.60f..0.65f),
            PersonTop.CARDIGAN to (0.63f..0.68f),
            PersonTop.LIGHT_OUTER to (0.61f..0.66f),
            PersonTop.KNIT_COAT to (0.69f..0.73f),
            PersonTop.DOWN to (0.61f..0.66f)
        )
        val pairs = (0..4).map { r ->
            val (base, top) = PersonFigure.outfitFor(r, false)
            top!! to base
        } + (PersonTop.DOWN to PersonBase.BODY_C)
        for ((top, base) in pairs) {
            val tb = PersonCanvas.box(PersonCanvas.topArt(top))
            val bb = PersonCanvas.box(PersonCanvas.bodyArt(base))
            val bh = bb[3] - bb[1]
            val topF = (tb[1] - bb[1]) / bh
            val botF = (tb[3] - bb[1]) / bh
            val cen = (topF + botF) / 2f
            val cal = PersonCanvas.topCal(top)
            val hem = cen + cal[3] + (botF - cen) * cal[1]
            assertTrue("$top hem $hem in ${bands[top]}", hem in bands[top]!!)
        }
    }

    @Test fun topWidths_noLongerTentTheBody() {
        // The drawn width of a top's canvas box, in body widths.
        // The product-shot tops ran 0.93–1.22 before v0.1.23 —
        // wider than the body itself, which is what made them
        // hang like tents and swallow the arms. The fitted tops
        // land at 0.60–0.67. The down is the exception: its box
        // stays wider than the (widest) BODY_C box, and its fit
        // was verified on silhouettes in the audit instead.
        val pairs = (0..4).map { r ->
            val (base, top) = PersonFigure.outfitFor(r, false)
            top!! to base
        }
        for ((top, base) in pairs) {
            val tb = PersonCanvas.box(PersonCanvas.topArt(top))
            val bb = PersonCanvas.box(PersonCanvas.bodyArt(base))
            val ratio = (tb[2] - tb[0]) / (bb[2] - bb[0]) *
                PersonCanvas.topCal(top)[0]
            assertTrue("$top drawn box width $ratio", ratio in 0.55f..0.72f)
        }
        val tb = PersonCanvas.box(PersonCanvas.topArt(PersonTop.DOWN))
        val bb = PersonCanvas.box(PersonCanvas.bodyArt(PersonBase.BODY_C))
        val downRatio = (tb[2] - tb[0]) / (bb[2] - bb[0]) *
            PersonCanvas.topCal(PersonTop.DOWN)[0]
        assertTrue("DOWN drawn box width $downRatio", downRatio in 1.05f..1.20f)
    }

    // ---- v0.1.25: the head layers anchor the face onto the rig ----

    @Test fun headLayers_anchorTheFaceOntoTheRig() {
        // The v0.1.25 head layers, pinned: each layer is a picture
        // of the hair already wearing the hat, placed so ITS face
        // lands on the rig's face — centre x and top aligned, the
        // raincoat on the eye line instead (its rig face top is
        // the hood opening). The layer must cover the rig's whole
        // face box (it replaces the base's hair), and its poke
        // over the person rect must fit the spec's pad.
        // bw/ph per base come from PersonCanvas.bodyAspect.
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            val bw = PersonCanvas.bodyAspect(base) * 1000f
            val ph = 1000f
            val eyeAnchored = base == PersonBase.RAINCOAT
            for (head in listOf(PersonHead.CAP, PersonHead.KNITCAP)) {
                val face = PersonAnchors.headFace(head)
                val r = PersonAnchors.headLayerRect(
                    head, rig, eyeAnchored, 0f, 0f, bw, ph
                )
                val faceCx = r[0] + (face[0] + face[2]) / 2f * r[2]
                assertEquals(
                    "$base $head face centre x", rig.faceCx * bw, faceCx, 1.5f
                )
                if (eyeAnchored) {
                    val eye = r[1] + PersonAnchors.headEye(head) * r[3]
                    assertEquals(
                        "$base $head eye line", rig.eyeY * ph, eye, 1.5f
                    )
                } else {
                    val faceTop = r[1] + face[1] * r[3]
                    assertEquals(
                        "$base $head face top", rig.faceTop * ph, faceTop, 1.5f
                    )
                }
                assertTrue(
                    "$base $head covers the face box",
                    r[0] < rig.faceLeft * bw &&
                        r[0] + r[2] > rig.faceRight * bw &&
                        // The layer's hair ends at the chin band:
                        // on BODY_A it lands within 0.002 of the
                        // rig's chin, so the band gets 0.01 of slack.
                        r[1] + r[3] > rig.faceBottom * ph - 0.01f * ph
                )
                val poke = maxOf(0f, -r[1] / ph)
                val spec = spec19(3).copy(base = base, head = head)
                assertTrue(
                    "$base $head poke $poke within pad",
                    poke <= PersonFigure.padTopRatio(spec) + 0.001f
                )
            }
        }
        // The layer anchors themselves stay as measured.
        assertEquals(975, PersonAnchors.HEAD_CAP_PX_W)
        assertEquals(728, PersonAnchors.HEAD_CAP_PX_H)
        assertEquals(769, PersonAnchors.HEAD_KNIT_PX_W)
        assertEquals(722, PersonAnchors.HEAD_KNIT_PX_H)
        for (head in listOf(PersonHead.CAP, PersonHead.KNITCAP)) {
            val f = PersonAnchors.headFace(head)
            assertTrue(f[0] < f[2] && f[1] < f[3])
            assertTrue(f.all { it in 0f..1f })
            assertTrue(PersonAnchors.headEye(head) in 0.60f..0.70f)
        }
    }

    @Test fun sweatDrops_hugTheHair() {
        // The drops sit on the hair at the face's sides: inside the
        // body's silhouette horizontally and above the old earring
        // height (0.30) that hung them beside the cheeks.
        assertTrue(PersonAnchors.SWEAT_X1 in 0.17f..0.22f)
        assertTrue(PersonAnchors.SWEAT_X2 in 0.80f..0.85f)
        assertTrue(PersonAnchors.SWEAT_Y < 0.30f)
    }

    // ---- v0.1.24: the towel wraps the neck, the mask fits the face ----

    @Test fun towelWrapsTheNeck_notTheChest() {
        // The v0.1.24 towel, pinned: the band's top sits at the
        // neck — at or above the muffler's top (0.305) and below
        // the mouth — and the panels end on the upper chest. Until
        // v0.1.23 the top was 0.355 (below the neck base) and the
        // panels hung to 0.57–0.60 like an apron.
        assertTrue(PersonAnchors.TOWEL_TOP in 0.29f..0.31f)
        assertTrue(PersonAnchors.TOWEL_TOP <= PersonAnchors.MUFFLER_TOP)
        assertTrue(PersonAnchors.TOWEL_TOP > 0.261f)
        assertTrue(PersonAnchors.TOWEL_H in 0.20f..0.22f)
        val bottom = PersonAnchors.TOWEL_TOP + PersonAnchors.TOWEL_H
        assertTrue("towel bottom $bottom", bottom in 0.50f..0.52f)
    }

    @Test fun maskCloth_landsOnTheChin() {
        // The v0.1.24 mask, pinned: on every base the cloth starts
        // at the nose bridge (eye line + DROP, above the mouth —
        // also asserted in faceAnchors_*) and ends at ≈0.304, a
        // wrap under the measured jaw line (≈0.287 family-wide)
        // and clear of the muffler below it. The cloth spans the
        // measured cheeks (MASK_W × cheekW), not the wider rig
        // face box. Until v0.1.23 the cloth bottom ran to
        // 0.337–0.368 — into the muffler on the portraits.
        for (base in PersonBase.values()) {
            val rig = PersonRigs.rig(base)
            val top = rig.eyeY + PersonAnchors.MASK_DROP
            val bottom = top + PersonAnchors.MASK_H
            assertTrue("$base mask bottom $bottom", bottom in 0.295f..0.315f)
            assertTrue("$base cheekW ${rig.cheekW}", rig.cheekW in 0.30f..0.48f)
            val clothW = PersonAnchors.MASK_W * rig.cheekW
            assertTrue("$base mask cloth width $clothW", clothW in 0.31f..0.48f)
        }
        assertTrue(PersonAnchors.MASK_W in 0.98f..1.06f)
        assertTrue(PersonAnchors.MASK_H in 0.064f..0.072f)
        val p = PersonAnchors.MASK_PAINT
        assertTrue(p[0] < p[2] && p[1] < p[3])
        assertTrue(p.all { it in 0f..1f })
    }
}
