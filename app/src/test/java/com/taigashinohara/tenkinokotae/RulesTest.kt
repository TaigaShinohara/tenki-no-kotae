package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun day(
        apparent: (Int) -> Double = { 15.0 },
        prob: (Int) -> Int? = { 0 },
        precip: (Int) -> Double = { 0.0 },
        pressure: (Int) -> Double = { 1013.0 },
        wind: (Int) -> Double = { 3.0 },
        uvMax: Double = 1.0
    ): DayForecast {
        val hours = (0..23).map { h ->
            HourEntry(h, apparent(h), apparent(h), prob(h), precip(h), 0, pressure(h), wind(h), 50, 0.0)
        }
        return DayForecast("2026-11-18", hours, 18.0, 8.0, 16.0, uvMax, "2026-11-18T06:30", "2026-11-18T17:00")
    }

    // ---------- 前日比 ----------

    @Test fun diffWord_levels() {
        assertEquals(-3, Rules.diffWord(-11.0).level)
        assertEquals(-2, Rules.diffWord(-7.0).level)
        assertEquals(-1, Rules.diffWord(-4.0).level)
        assertEquals(0, Rules.diffWord(0.0).level)
        assertEquals(0, Rules.diffWord(2.0).level)
        assertEquals(1, Rules.diffWord(4.0).level)
        assertEquals(2, Rules.diffWord(8.0).level)
        assertEquals(3, Rules.diffWord(12.0).level)
    }

    @Test fun daytimeAvg_uses9to18() {
        val d = day(apparent = { it.toDouble() })
        // hours 9..17 average = (9+17)/2 = 13
        assertEquals(13.0, Rules.daytimeAvg(d), 0.001)
    }

    // ---------- 服装 ----------

    @Test fun clothing_ranks() {
        assertEquals(0, Rules.baseRank(30.0))
        assertEquals(1, Rules.baseRank(25.0))
        assertEquals(2, Rules.baseRank(20.0))
        assertEquals(3, Rules.baseRank(15.0))
        assertEquals(4, Rules.baseRank(10.0))
        assertEquals(5, Rules.baseRank(5.0))
        assertEquals(6, Rules.baseRank(0.0))
    }

    @Test fun clothing_feelOffsetAndWind() {
        val mild = day(apparent = { 20.0 }) // rank 2
        assertEquals(2, Rules.clothing(mild, 0).rank)
        assertEquals(3, Rules.clothing(mild, 1).rank)  // 寒がり
        assertEquals(1, Rules.clothing(mild, -1).rank) // 暑がり
        val windy = day(apparent = { 20.0 }, wind = { 12.0 })
        assertEquals(3, Rules.clothing(windy, 0).rank) // 強風で+1
    }

    // ---------- 傘 ----------

    @Test fun umbrella_levels() {
        assertEquals(Rules.UmbrellaLevel.NEED, Rules.umbrella(day(prob = { 60 })).level)
        assertEquals(Rules.UmbrellaLevel.FOLDING, Rules.umbrella(day(prob = { 40 })).level)
        assertEquals(Rules.UmbrellaLevel.NONE, Rules.umbrella(day(prob = { 10 })).level)
        // 降水量1mm以上 → NEED
        assertEquals(Rules.UmbrellaLevel.NEED, Rules.umbrella(day(precip = { if (it == 12) 1.5 else 0.0 })).level)
        // 通り雨1時間 → FOLDING
        assertEquals(Rules.UmbrellaLevel.FOLDING, Rules.umbrella(day(precip = { if (it == 12) 0.3 else 0.0 })).level)
    }

    @Test fun umbrella_eveningNote() {
        val d = day(prob = { if (it >= 16) 60 else 0 }, precip = { if (it >= 16) 0.5 else 0.0 })
        assertEquals("夕方だけ降りそう", Rules.umbrella(d).note)
    }

    // ---------- 日焼け止め ----------

    @Test fun sunscreen_levels() {
        assertTrue(Rules.sunscreen(1.0).title.contains("不要"))
        assertTrue(Rules.sunscreen(4.0).title.contains("塗ると安心"))
        assertTrue(Rules.sunscreen(7.0).title.contains("必須"))
        assertFalse(Rules.sunscreen(7.0).title.contains("帽子"))
        assertTrue(Rules.sunscreen(9.0).title.contains("帽子"))
    }

    // ---------- 気圧 ----------

    @Test fun pressure_steadyDay_hidden() {
        val d = day(pressure = { 1013.0 })
        assertEquals(-1, Rules.pressure(d, 1).level)
        assertFalse(Rules.pressure(d, 2).visible)
    }

    @Test fun pressure_moderateDrop() {
        val d = day(pressure = { 1020.0 - it * 0.6 }) // 6h drop = 3.6
        val r = Rules.pressure(d, 1)
        assertEquals(1, r.level)
        assertTrue(r.visible)
        assertTrue(r.title.contains("やすいかも"))
    }

    @Test fun pressure_steepDrop_visibleEvenForInsensitive() {
        val d = day(pressure = { 1024.0 - it * 1.2 }) // 6h drop = 7.2
        val r = Rules.pressure(d, 0)
        assertEquals(2, r.level)
        assertTrue(r.visible)
    }

    @Test fun pressure_slightDrop_onlyForSensitive() {
        val d = day(pressure = { 1015.0 - it * 0.3 }) // 6h drop = 1.8
        assertEquals(0, Rules.pressure(d, 2).level)
        assertTrue(Rules.pressure(d, 2).visible)
        assertFalse(Rules.pressure(d, 1).visible)
    }

    @Test fun pressure_lowAbsolute_triggers() {
        val d = day(pressure = { 1005.0 }) // flat but below 1007
        assertEquals(1, Rules.pressure(d, 1).level)
        val d2 = day(pressure = { 1001.0 }) // below 1003
        assertEquals(2, Rules.pressure(d2, 1).level)
    }

    // ---------- 帰り・明日・結論 ----------

    @Test fun eveningLine_rain() {
        val d = day(prob = { if (it in 17..22) 80 else 0 }, precip = { if (it in 17..22) 0.5 else 0.0 })
        assertTrue(Rules.eveningLine(d).contains("雨"))
    }

    @Test fun eveningLine_calm() {
        assertEquals("帰りもそのままでOK", Rules.eveningLine(day()))
    }

    @Test fun tomorrowLine_andConclusion() {
        val today = day(apparent = { 15.0 })
        val tomorrow = day(apparent = { 10.0 })
        assertTrue(Rules.tomorrowLine(today, tomorrow).contains("今日より少し寒い"))
        val yesterday = day(apparent = { 20.0 })
        assertTrue(Rules.conclusion(today, yesterday, 0).contains("少し寒い"))
    }

    @Test fun gapNote_largeGap() {
        val d = day(apparent = { if (it in 12..15) 20.0 else 10.0 })
        assertEquals("夜は冷える。羽織りを足して", Rules.gapNote(d))
        assertEquals(null, Rules.gapNote(day()))
    }

    // ---------- v0.1.19: 「今日の人」の追加判定の入力 ----------

    @Test fun daytimeFeelAvg_uses9to18Window() {
        // apparent = hour: the 9..18 average is (9+18)/2 = 13.5 —
        // one hour wider than the clothing window (9..17 -> 13.0).
        val d = day(apparent = { it.toDouble() })
        assertEquals(13.5, Rules.daytimeFeelAvg(d)!!, 0.001)
        // Only the hours that exist are averaged (a partial day from
        // the backup source): hours 15..23 -> the 15..18 slice.
        val partial = DayForecast(
            "2026-11-18",
            (15..23).map { h ->
                HourEntry(h, h.toDouble(), h.toDouble(), 0, 0.0, 0, 1013.0, 3.0, 50, 0.0)
            },
            18.0, 8.0, 16.0, 1.0, "2026-11-18T06:30", "2026-11-18T17:00"
        )
        assertEquals(16.5, Rules.daytimeFeelAvg(partial)!!, 0.001)
        // No window hours at all -> null, and the rules stay silent.
        val night = DayForecast(
            "2026-11-18",
            (19..23).map { h ->
                HourEntry(h, 10.0, 10.0, 0, 0.0, 0, 1013.0, 3.0, 50, 0.0)
            },
            18.0, 8.0, 16.0, 1.0, "2026-11-18T06:30", "2026-11-18T17:00"
        )
        assertEquals(null, Rules.daytimeFeelAvg(night))
        assertEquals(null, Rules.daytimeHumidityAvg(night))
    }

    @Test fun daytimeHumidityAvg_averagesTheWindow() {
        val d = DayForecast(
            "2026-11-18",
            (0..23).map { h ->
                HourEntry(h, 15.0, 15.0, 0, 0.0, 0, 1013.0, 3.0, if (h in 9..18) 30 else 90, 0.0)
            },
            18.0, 8.0, 16.0, 1.0, "2026-11-18T06:30", "2026-11-18T17:00"
        )
        assertEquals(30.0, Rules.daytimeHumidityAvg(d)!!, 0.001)
    }
}
