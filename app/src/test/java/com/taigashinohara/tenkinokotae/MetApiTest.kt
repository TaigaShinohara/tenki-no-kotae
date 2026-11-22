package com.taigashinohara.tenkinokotae

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetApiTest {

    // ---------- 体感温度（BOM/Steadman） ----------

    @Test fun apparentTemperature_knownValues() {
        // 高温多湿・弱風 → 気温より高く感じる
        assertEquals(33.67, MetApi.apparentTemperature(30.0, 70.0, 3.0), 0.05)
        // 低温・強風 → 気温より大きく低く感じる
        assertEquals(-4.56, MetApi.apparentTemperature(5.0, 50.0, 10.0), 0.05)
        // 穏やかな日 → 気温よりやや低め
        assertEquals(18.45, MetApi.apparentTemperature(20.0, 50.0, 2.0), 0.05)
    }

    // ---------- symbol_code → WMOコード ----------

    @Test fun symbolMapping() {
        assertEquals(0, MetApi.symbolToWeatherCode("clearsky_day"))
        assertEquals(0, MetApi.symbolToWeatherCode("clearsky_night"))
        assertEquals(1, MetApi.symbolToWeatherCode("fair_day"))
        assertEquals(2, MetApi.symbolToWeatherCode("partlycloudy_night"))
        assertEquals(3, MetApi.symbolToWeatherCode("cloudy"))
        assertEquals(45, MetApi.symbolToWeatherCode("fog"))
        assertEquals(61, MetApi.symbolToWeatherCode("lightrain"))
        assertEquals(63, MetApi.symbolToWeatherCode("rain"))
        assertEquals(65, MetApi.symbolToWeatherCode("heavyrain"))
        assertEquals(80, MetApi.symbolToWeatherCode("lightrainshowers_day"))
        assertEquals(81, MetApi.symbolToWeatherCode("rainshowers"))
        assertEquals(82, MetApi.symbolToWeatherCode("heavyrainshowers_night"))
        assertEquals(71, MetApi.symbolToWeatherCode("lightsnow"))
        assertEquals(73, MetApi.symbolToWeatherCode("snow"))
        assertEquals(75, MetApi.symbolToWeatherCode("heavysnow"))
        assertEquals(86, MetApi.symbolToWeatherCode("snowshowers_day"))
        assertEquals(95, MetApi.symbolToWeatherCode("rainandthunder"))
        assertEquals(95, MetApi.symbolToWeatherCode("lightsnowandthunder"))
        assertEquals(96, MetApi.symbolToWeatherCode("heavyrainandthunder"))
    }

    // ---------- parse: UTC → JST の組み立て ----------

    @Test fun parse_convertsUtcToTokyoDays() {
        val json = """
        {"properties":{"meta":{},"timeseries":[
          {"time":"2026-11-18T15:00:00Z","data":{
            "instant":{"details":{"air_temperature":10.0,"air_pressure_at_sea_level":1010.0,
              "relative_humidity":50.0,"wind_speed":2.0,"ultraviolet_index_clear_sky":0.0}},
            "next_1_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":1.5}}}},
          {"time":"2026-11-18T16:00:00Z","data":{
            "instant":{"details":{"air_temperature":9.0,"air_pressure_at_sea_level":1011.0,
              "relative_humidity":60.0,"wind_speed":3.0}},
            "next_1_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":0.0}}}},
          {"time":"2026-11-19T00:00:00Z","data":{
            "instant":{"details":{"air_temperature":15.0,"air_pressure_at_sea_level":1012.0,
              "relative_humidity":40.0,"wind_speed":1.0,"ultraviolet_index_clear_sky":3.5}},
            "next_6_hours":{"summary":{"symbol_code":"clearsky_day"},
              "details":{"precipitation_amount":0.0,"air_temperature_max":16.0,"air_temperature_min":14.0}}}}
        ]}}
        """.trimIndent()
        val f = MetApi.parse(JSONObject(json))
        assertEquals(1, f.days.size)
        val day = f.day("2026-11-19")
        assertNotNull(day)
        day!!
        // 15Z->0時, 16Z->1時, 翌00Z->9時（すべて JST 11/19）
        assertEquals(listOf(0, 1, 9), day.hours.map { it.hour })
        val h0 = day.hour(0)!!
        assertEquals(10.0, h0.temp, 0.001)
        assertEquals(6.62, h0.apparent, 0.05) // 自前計算の体感温度
        assertNull(h0.precipProb)             // 確率は提供されない
        assertEquals(1.5, h0.precip, 0.001)
        assertEquals(63, h0.weatherCode)      // rain
        assertEquals(1010.0, h0.pressure, 0.001)
        val h9 = day.hour(9)!!
        assertEquals(0, h9.weatherCode)       // clearsky_day
        assertEquals(0.0, h9.precip, 0.001)   // next_6_hours は按分（0/6）
        // 日別集計は時間別から算出
        assertEquals(15.0, day.tempMax, 0.001)
        assertEquals(9.0, day.tempMin, 0.001)
        assertEquals(3.5, day.uvMax, 0.001)
        assertEquals("", day.sunrise)
        assertEquals("", day.sunset)
    }

    // ---------- parse: 実サンプル（大阪）での検証 ----------

    @Test fun parse_realSample_osaka() {
        // The sample lives in test resources (src/test/resources) so this
        // test always runs: previously it read a file under .local/ and
        // assumeTrue-skipped silently when absent, so a green count did
        // not mean it had executed (BUG_REVIEW #5).
        val stream = javaClass.getResourceAsStream("/met_complete_sample.json")
        assertNotNull("met_complete_sample.json must be on the test classpath", stream)
        val f = MetApi.parse(JSONObject(stream!!.bufferedReader().readText()))

        // サンプル取得は 2026-11-18T13:20Z。JST では 11/19 01時から始まる
        val d19 = f.day("2026-11-19")
        assertNotNull(d19)
        assertEquals(23, d19!!.hours.size) // 1時〜23時
        val d20 = f.day("2026-11-20")
        assertNotNull(d20)
        assertEquals(24, d20!!.hours.size) // 終日1時間刻み
        // 確率は全時間 null、日の出入りは空、体感は気温と大きく乖離しない
        assertTrue(d20.hours.all { it.precipProb == null })
        assertEquals("", d20.sunrise)
        assertTrue(d20.hours.all { Math.abs(it.apparent - it.temp) < 12.0 })
        assertTrue(d20.tempMax >= d20.tempMin)
        assertTrue(d20.uvMax >= 0.0)
    }
}
