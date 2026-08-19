package dev.shino3.echopanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Home Assistant の REST クライアント。
 *
 * 天気もカレンダーも外気温の履歴も HA が既に持っているので、
 * 気象 API やカレンダー API と個別に繋ぎ込まない。認証も HA のトークン1つで済む。
 *
 * 依存を増やさないため HttpURLConnection と org.json だけで書いてある。
 */
class HaClient(private val baseUrl: String, private val token: String) {

    data class EntityState(
        val entityId: String,
        val state: String,
        val attributes: JSONObject,
    ) {
        fun attrDouble(key: String): Double? =
            if (attributes.has(key)) attributes.optDouble(key).takeIf { !it.isNaN() } else null

        fun attrString(key: String): String? =
            if (attributes.has(key)) attributes.optString(key) else null
    }

    data class ForecastEntry(
        val time: ZonedDateTime?,
        val condition: String?,
        val tempHigh: Double?,
        val tempLow: Double?,
        val precipProbability: Double?,
    )

    data class CalendarEvent(
        val summary: String,
        val start: String,
        val allDay: Boolean,
    )

    data class TrendPoint(val at: Instant, val value: Double)

    private fun open(path: String, method: String = "GET"): HttpURLConnection {
        val url = URL(baseUrl.trimEnd('/') + path)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5000
            readTimeout = 8000
        }
    }

    private fun HttpURLConnection.readBody(): String =
        inputStream.bufferedReader().use { it.readText() }

    suspend fun state(entityId: String): EntityState? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open("/api/states/${enc(entityId)}")
            if (c.responseCode != 200) return@runCatching null
            val o = JSONObject(c.readBody())
            EntityState(
                entityId = o.optString("entity_id"),
                state = o.optString("state"),
                attributes = o.optJSONObject("attributes") ?: JSONObject()
            )
        }.getOrNull()
    }

    /**
     * 天気予報。
     * HA 2024.4 以降 forecast は属性から外され、サービス呼び出しで取る形になった。
     */
    suspend fun forecast(entityId: String, type: String = "daily"): List<ForecastEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = open("/api/services/weather/get_forecasts?return_response=true", "POST")
                c.doOutput = true
                c.outputStream.use {
                    it.write(
                        JSONObject()
                            .put("entity_id", entityId)
                            .put("type", type)
                            .toString().toByteArray()
                    )
                }
                if (c.responseCode !in 200..299) return@runCatching emptyList()
                val root = JSONObject(c.readBody())
                val svc = root.optJSONObject("service_response") ?: return@runCatching emptyList()
                val ent = svc.optJSONObject(entityId) ?: return@runCatching emptyList()
                val arr = ent.optJSONArray("forecast") ?: JSONArray()
                (0 until arr.length()).map { i ->
                    val f = arr.getJSONObject(i)
                    ForecastEntry(
                        time = runCatching { ZonedDateTime.parse(f.optString("datetime")) }.getOrNull(),
                        condition = f.optString("condition").ifBlank { null },
                        tempHigh = f.optDouble("temperature").takeIf { !it.isNaN() },
                        tempLow = f.optDouble("templow").takeIf { !it.isNaN() },
                        precipProbability = f.optDouble("precipitation_probability")
                            .takeIf { !it.isNaN() }
                    )
                }
            }.getOrElse { emptyList() }
        }

    /** センサーの推移。外気温グラフ用。 */
    suspend fun history(entityId: String, hours: Int): List<TrendPoint> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS).toString()
                val path = "/api/history/period/${enc(start)}" +
                    "?filter_entity_id=${enc(entityId)}&minimal_response&significant_changes_only"
                val c = open(path)
                if (c.responseCode != 200) return@runCatching emptyList()
                val outer = JSONArray(c.readBody())
                if (outer.length() == 0) return@runCatching emptyList()
                val series = outer.getJSONArray(0)
                (0 until series.length()).mapNotNull { i ->
                    val o = series.getJSONObject(i)
                    val v = o.optString("state").toDoubleOrNull() ?: return@mapNotNull null
                    val t = runCatching {
                        Instant.parse(o.optString("last_changed").ifBlank { o.optString("last_updated") })
                    }.getOrNull() ?: return@mapNotNull null
                    TrendPoint(t, v)
                }
            }.getOrElse { emptyList() }
        }

    /** 直近の予定。時計の隣に出す用途なので件数は絞って呼ぶ。 */
    suspend fun calendarEvents(entityId: String, days: Int = 7): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = ZonedDateTime.now()
                val end = start.plusDays(days.toLong())
                val path = "/api/calendars/${enc(entityId)}" +
                    "?start=${enc(start.toInstant().toString())}&end=${enc(end.toInstant().toString())}"
                val c = open(path)
                if (c.responseCode != 200) return@runCatching emptyList()
                val arr = JSONArray(c.readBody())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val s = o.optJSONObject("start")
                    val dateTime = s?.optString("dateTime").orEmpty()
                    val date = s?.optString("date").orEmpty()
                    CalendarEvent(
                        summary = o.optString("summary", "(無題)"),
                        start = dateTime.ifBlank { date },
                        allDay = dateTime.isBlank()
                    )
                }.sortedBy { it.start }
            }.getOrElse { emptyList() }
        }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
