package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 予備源（MET Norway）向けに追加した経路のテスト。
 * 降水確率が無いデータでは傘は降水量だけで判定し、昨日が無い場合は
 * 結論を今日の絶対評価から作る。確率ありの従来経路は RulesTest が担保。
 */
class FallbackRulesTest {

    /** precipProb が全時間 null の日（予備源の形）。 */
    private fun metDay(
        apparent: (Int) -> Double = { 15.0 },
        precip: (Int) -> Double = { 0.0 }
    ): DayForecast {
        val hours = (0..23).map { h ->
            HourEntry(h, apparent(h), apparent(h), null, precip(h), 0, 1013.0, 3.0, 50, 0.0)
        }
        return DayForecast("2026-11-19", hours, 18.0, 8.0, 16.0, 1.0, "", "")
    }

    // ---------- 傘： 降水量のみの判定 ----------

    @Test fun umbrella_amountOnly_need() {
        val d = metDay(precip = { if (it == 12) 1.5 else 0.0 })
        assertEquals(Rules.UmbrellaLevel.NEED, Rules.umbrella(d).level)
    }

    @Test fun umbrella_amountOnly_folding() {
        val single = metDay(precip = { if (it == 12) 0.3 else 0.0 })
        assertEquals(Rules.UmbrellaLevel.FOLDING, Rules.umbrella(single).level)
        // 0.1〜0.9mm が複数時間あっても、量ベースでは FOLDING のまま
        // （確率ベース経路の「2時間以上で NEED」とは分岐が別）
        val multi = metDay(precip = { if (it in listOf(10, 12, 14)) 0.3 else 0.0 })
        assertEquals(Rules.UmbrellaLevel.FOLDING, Rules.umbrella(multi).level)
    }

    @Test fun umbrella_amountOnly_noneAndNote() {
        assertEquals(Rules.UmbrellaLevel.NONE, Rules.umbrella(metDay()).level)
        assertEquals("雨の心配なし", Rules.umbrella(metDay()).note)
        val evening = metDay(precip = { if (it in 16..17) 0.5 else 0.0 })
        assertEquals("夕方だけ降りそう", Rules.umbrella(evening).note)
    }

    // ---------- 結論： 昨日なし ----------

    @Test fun conclusionNoYesterday_bands() {
        val cold = Rules.conclusionNoYesterday(metDay(apparent = { 10.0 }), 0)
        assertTrue(cold.contains("今日は寒い"))
        assertTrue(cold.contains("ニット＋コートで正解。"))

        val hot = Rules.conclusionNoYesterday(metDay(apparent = { 30.0 }), 0)
        assertTrue(hot.contains("今日はかなり暑い"))
        assertTrue(hot.contains("半袖でOK。"))

        val mild = Rules.conclusionNoYesterday(metDay(apparent = { 20.0 }), 0)
        assertTrue(mild.contains("過ごしやすい"))
    }
}
