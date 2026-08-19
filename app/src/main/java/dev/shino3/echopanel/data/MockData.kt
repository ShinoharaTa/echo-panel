package dev.shino3.echopanel.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * panels.json に "mock": true を書いたときに流れる作り物のデータ。
 * HA の設定を済ませなくてもレイアウトと文字サイズの検証ができる。
 */
object MockData {

    fun weatherNow(): HaClient.EntityState = HaClient.EntityState(
        entityId = "weather.mock",
        state = "partlycloudy",
        attributes = JSONObject()
            .put("temperature", 21.4)
            .put("humidity", 62)
    )

    fun forecast(count: Int): List<HaClient.ForecastEntry> {
        val conds = listOf("sunny", "partlycloudy", "rainy", "cloudy", "sunny", "clear-night", "rainy", "sunny")
        val highs = listOf(32.0, 30.0, 26.0, 27.0, 29.0, 31.0, 25.0, 28.0)
        val lows = listOf(24.0, 23.0, 21.0, 20.0, 22.0, 23.0, 19.0, 21.0)
        val pops = listOf(0.0, 20.0, 80.0, 40.0, 10.0, 0.0, 90.0, 0.0)
        val today = ZonedDateTime.now()
        return (0 until count.coerceIn(1, 8)).map { i ->
            HaClient.ForecastEntry(
                time = today.plusDays(i.toLong()),
                condition = conds[i % conds.size],
                tempHigh = highs[i % highs.size],
                tempLow = lows[i % lows.size],
                precipProbability = pops[i % pops.size],
            )
        }
    }

    fun calendar(): List<HaClient.CalendarEvent> {
        val now = ZonedDateTime.now()
        return listOf(
            HaClient.CalendarEvent("チーム定例", now.withHour(10).withMinute(0).toString(), false),
            HaClient.CalendarEvent("歯医者", now.plusDays(1).withHour(15).withMinute(30).toString(), false),
            HaClient.CalendarEvent("粗大ごみ収集", now.plusDays(2).toLocalDate().toString(), true),
            HaClient.CalendarEvent("サーバーメンテ", now.plusDays(4).withHour(22).withMinute(0).toString(), false),
        )
    }

    /** 一日周期の気温っぽいカーブ。深夜が谷、14時ごろが山。 */
    fun trend(hours: Int): List<HaClient.TrendPoint> {
        val now = Instant.now()
        val points = (hours * 4)   // 15分間隔
        return (0..points).map { i ->
            val t = now.minus(((points - i) * 15).toLong(), ChronoUnit.MINUTES)
            val hourOfDay = t.atZone(java.time.ZoneId.systemDefault()).hour +
                t.atZone(java.time.ZoneId.systemDefault()).minute / 60.0
            val phase = (hourOfDay - 14.0) / 24.0 * 2 * PI
            val v = 26.0 + 5.5 * sin(phase + PI / 2) * -1
            HaClient.TrendPoint(t, Math.round(v * 10.0) / 10.0)
        }
    }
}
