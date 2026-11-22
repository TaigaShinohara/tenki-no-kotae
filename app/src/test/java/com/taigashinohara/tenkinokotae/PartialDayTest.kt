package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 部分日（予備源 MET Norway の「今日」は現在時刻以降しかない）での
 * 判定を固定するテスト群（BUG_REVIEW #1・#2 の修正）。
 * 従来のフィクスチャは全て 0〜23時の完全日で、部分日の壊れ方は
 * テスト構造上検出できなかった。ここでは「実在する時間だけを使う」
 * 「実在しない時間帯は完全日のふりをしない」を固定する。
 */
class PartialDayTest {

    /** 指定した時間だけを持つ日（予備源の形： 確率は全時間 null）。 */
    private fun partialDay(
        hours: List<Int>,
        apparent: (Int) -> Double = { 15.0 },
        precip: (Int) -> Double = { 0.0 },
        pressure: (Int) -> Double = { 1013.0 }
    ): DayForecast {
        val entries = hours.map { h ->
            HourEntry(h, apparent(h), apparent(h), null, precip(h), 0, pressure(h), 3.0, 50, 0.0)
        }
        return DayForecast("2026-11-19", entries, 18.0, 8.0, 16.0, 1.0, "", "")
    }

    private fun fullDay(apparent: (Int) -> Double): DayForecast =
        partialDay((0..23).toList(), apparent)

    // ---------- daytimeAvg： 実在する日中だけ／無ければ残り時間 ----------

    @Test fun daytimeAvg_partialDaytime_usesExistingDaytimeHoursOnly() {
        // 15〜23時だけの日： 日中として使うのは実在する 15・16・17時だけ
        val d = partialDay((15..23).toList(), apparent = { it.toDouble() })
        assertEquals(16.0, Rules.daytimeAvg(d), 0.001)
        assertTrue(Rules.hasDaytimeHours(d))
    }

    @Test fun daytimeAvg_eveningOnly_evaluatesRemainingHours() {
        // 19〜23時だけの日： 日中は1件も無いので、残り時間の平均で評価する
        val d = partialDay(
            (19..23).toList(),
            apparent = { h -> listOf(10.0, 11.0, 12.0, 13.0, 14.0)[h - 19] }
        )
        assertEquals(12.0, Rules.daytimeAvg(d), 0.001)
        assertFalse(Rules.hasDaytimeHours(d))
        assertTrue(Rules.isPartialDay(d))
    }

    @Test fun isPartialDay_fullDayIsNotPartial() {
        assertFalse(Rules.isPartialDay(fullDay { 15.0 }))
        assertTrue(Rules.isPartialDay(partialDay((15..23).toList())))
    }

    // ---------- 結論： 前日比は同じ時間同士、比べられなければ絶対評価 ----------

    @Test fun conclusion_eveningPartial_usesAbsoluteEvaluation() {
        // 今日は夜の時間しかない。昨日の日中と引いて「昨日より寒い」を作らない
        val today = partialDay((19..23).toList(), apparent = { 5.0 })
        val yesterday = fullDay { 20.0 }
        val text = Rules.conclusion(today, yesterday, 0)
        assertTrue(text.contains("今日は"))
        assertFalse(text.contains("昨日"))
    }

    @Test fun conclusion_partialDaytime_comparesMatchedHours() {
        // 今日は 15時以降のみ・昨日は完全日。体感＝時刻のデータでは、
        // 従来の「今日の日中平均（15〜17）vs 昨日の日中平均（9〜17）」は
        // +3℃のずれを作るが、同じ時間同士なら差は 0 になる。
        val today = partialDay((15..23).toList(), apparent = { it.toDouble() })
        val yesterday = fullDay { it.toDouble() }
        val text = Rules.conclusion(today, yesterday, 0)
        assertTrue(text.contains("昨日とほぼ同じ体感"))
    }

    @Test fun tomorrowLine_partialToday_comparesMatchedHours() {
        val today = partialDay((15..23).toList(), apparent = { it.toDouble() })
        val tomorrow = fullDay { it.toDouble() }
        val text = Rules.tomorrowLine(today, tomorrow)
        assertTrue(text.contains("今日とほぼ同じ体感"))
    }

    // ---------- 傘： 窓が空でも「心配なし」と決めつけない ----------

    @Test fun umbrella_eveningOnly_usesRemainingHours() {
        val rainy = partialDay((19..23).toList(), precip = { if (it == 20) 1.5 else 0.0 })
        assertEquals(Rules.UmbrellaLevel.NEED, Rules.umbrella(rainy).level)
        val passing = partialDay((19..23).toList(), precip = { if (it == 21) 0.3 else 0.0 })
        assertEquals(Rules.UmbrellaLevel.FOLDING, Rules.umbrella(passing).level)
        val dry = partialDay((19..23).toList())
        assertEquals(Rules.UmbrellaLevel.NONE, Rules.umbrella(dry).level)
        assertEquals("雨の心配なし", Rules.umbrella(dry).note)
    }

    // ---------- 気圧： 実時刻で測る／測れなければ未測定 ----------

    @Test fun pressure_partialDay_dropTimingUsesRealHours() {
        // 15〜23時、1時間に 1.0 hPa ずつ低下。最大の6時間低下は
        // 15→21時で 6.0、終了は実時刻 21時（従来のインデックス前提
        // では「6時」と誤り、朝の文言になっていた）。
        val d = partialDay(
            (15..23).toList(),
            pressure = { h -> 1030.0 - (h - 15) * 1.0 }
        )
        val r = Rules.pressure(d, 1)
        assertTrue(r.dropMeasured)
        assertEquals(6.0, r.maxDrop6h, 0.001)
        assertEquals(2, r.level)
        assertEquals("夜にかけて下がっていく", r.timingNote)
    }

    @Test fun pressure_shortSpan_isUnmeasured() {
        // 19〜23時の5件だけでは6時間幅が取れない。下がっていても
        // 「測れた 0.0」ではなく未測定として区別される。
        val d = partialDay(
            (19..23).toList(),
            pressure = { h -> 1015.0 - (h - 19) * 1.0 }
        )
        val r = Rules.pressure(d, 1)
        assertFalse(r.dropMeasured)
        assertEquals(0.0, r.maxDrop6h, 0.001)
        // 最低気圧 1011 は閾値未満の低下材料にならないので level は -1
        assertEquals(-1, r.level)
    }

    @Test fun pressure_fullDay_zeroDropIsMeasured() {
        // 完全日で変化なしなら「測れた 0.0」。未測定とは区別される。
        val r = Rules.pressure(fullDay { 15.0 }, 1)
        assertTrue(r.dropMeasured)
        assertEquals(0.0, r.maxDrop6h, 0.001)
    }
}
