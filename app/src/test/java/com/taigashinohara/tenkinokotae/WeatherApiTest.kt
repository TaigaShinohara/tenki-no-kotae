package com.taigashinohara.tenkinokotae

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherApiTest {

    @Test fun extractErrorReason_openMeteoJson() {
        val body = """{"error":true,"reason":"Data corrupted at line 3"}"""
        assertEquals("Data corrupted at line 3", WeatherApi.extractErrorReason(body))
    }

    @Test fun extractErrorReason_fallbacks() {
        assertNull(WeatherApi.extractErrorReason(null))
        assertNull(WeatherApi.extractErrorReason(""))
        assertNull(WeatherApi.extractErrorReason("{}"))
        assertNull(WeatherApi.extractErrorReason("<html><body>Forbidden</body></html>"))
        // 短いプレーンテキストはそのまま理由として使う
        assertEquals("Forbidden", WeatherApi.extractErrorReason("Forbidden"))
    }

    // ---------- parse: 配列の対応付け・時刻解析・確率 null の混在 ----------

    /**
     * 2日分×24時間の Open-Meteo 応答を組み立てる。
     * 気温は「日インデックス×100＋時」で一意にし、配列の対応付けが
     * ずれたら必ず検出できるようにする。降水確率は i%7==3 の位置を
     * JSON null にし、null と数値の混在を再現する。
     */
    private fun twoDayJson(): String {
        val dates = listOf("2026-11-18", "2026-11-19")
        fun arr(values: List<String>): String = values.joinToString(",", "[", "]")
        val time = mutableListOf<String>()
        val temp = mutableListOf<String>()
        val app = mutableListOf<String>()
        val prob = mutableListOf<String>()
        val prec = mutableListOf<String>()
        val code = mutableListOf<String>()
        val pres = mutableListOf<String>()
        val wind = mutableListOf<String>()
        val hum = mutableListOf<String>()
        val uv = mutableListOf<String>()
        for (d in dates.indices) {
            for (h in 0..23) {
                val i = d * 24 + h
                time.add("\"${dates[d]}T${"%02d".format(h)}:00\"")
                temp.add((d * 100 + h).toString())
                app.add((d * 100 + h + 0.5).toString())
                prob.add(if (i % 7 == 3) "null" else (h * 4).toString())
                prec.add(if (i == 12) "1.5" else "0.0")
                code.add("0")
                pres.add((1000 + h).toString())
                wind.add("3.0")
                hum.add("50")
                uv.add((h / 10.0).toString())
            }
        }
        return """
        {"hourly":{
          "time":${arr(time)},"temperature_2m":${arr(temp)},
          "apparent_temperature":${arr(app)},
          "precipitation_probability":${arr(prob)},
          "precipitation":${arr(prec)},"weather_code":${arr(code)},
          "pressure_msl":${arr(pres)},"wind_speed_10m":${arr(wind)},
          "relative_humidity_2m":${arr(hum)},"uv_index":${arr(uv)}},
         "daily":{
          "time":["2026-11-18","2026-11-19"],
          "temperature_2m_max":[21.5,19.0],"temperature_2m_min":[9.5,8.0],
          "apparent_temperature_max":[20.0,18.0],"uv_index_max":[3.5,2.5],
          "sunrise":["2026-11-18T06:30","2026-11-19T06:31"],
          "sunset":["2026-11-18T17:00","2026-11-19T17:01"]}}
        """.trimIndent()
    }

    @Test fun parse_mapsArraysToHoursAndDays() {
        val f = WeatherApi.parse(JSONObject(twoDayJson()))
        assertEquals(2, f.days.size)
        val d1 = f.day("2026-11-18")
        assertNotNull(d1)
        d1!!
        assertEquals(24, d1.hours.size)
        // 時刻文字列 "T09:00" から 9時を取り、同じ位置の配列要素が対応する
        val h9 = d1.hour(9)!!
        assertEquals(9.0, h9.temp, 0.001)
        assertEquals(9.5, h9.apparent, 0.001)
        assertEquals(1009.0, h9.pressure, 0.001)
        // 2日目の時間は2日目に帰属する（日付で仕分けされる）
        val d2 = f.day("2026-11-19")!!
        assertEquals(24, d2.hours.size)
        assertEquals(100.0, d2.hour(0)!!.temp, 0.001)
        assertEquals(123.0, d2.hour(23)!!.temp, 0.001)
        // 日別値は daily 側から来る（時間別の最大 23.0 とは別物）
        assertEquals(21.5, d1.tempMax, 0.001)
        assertEquals(9.5, d1.tempMin, 0.001)
        assertEquals(3.5, d1.uvMax, 0.001)
        assertEquals("2026-11-18T06:30", d1.sunrise)
        assertEquals("2026-11-18T17:00", d1.sunset)
        // 降水量は位置どおり
        assertEquals(1.5, d1.hour(12)!!.precip, 0.001)
        assertEquals(0.0, d1.hour(13)!!.precip, 0.001)
    }

    @Test fun parse_precipProbNullMixed() {
        val f = WeatherApi.parse(JSONObject(twoDayJson()))
        val d1 = f.day("2026-11-18")!!
        val d2 = f.day("2026-11-19")!!
        // i%7==3 の位置（1日目3時・10時…、2日目0時…）は null のまま
        assertNull(d1.hour(3)!!.precipProb)
        assertNull(d1.hour(10)!!.precipProb)
        assertNull(d2.hour(0)!!.precipProb)
        // それ以外は数値として読める
        assertEquals(16, d1.hour(4)!!.precipProb)
        assertEquals(4, d2.hour(1)!!.precipProb)
    }
}
