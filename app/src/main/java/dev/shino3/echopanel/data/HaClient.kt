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
 * HA が期待どおりに応答しなかったときの例外。
 * 設定画面にそのまま出すので、文面は机の前で読める言葉にする。
 */
class HaException(val code: Int, detail: String? = null) : RuntimeException(
    when (code) {
        401, 403 -> "認証に失敗 ($code)。トークンを確認"
        404 -> "エンティティが見つからない (404)"
        in 500..599 -> "HA 側のエラー ($code)"
        else -> "HTTP $code" + (detail?.trim()?.take(60)?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
    }
)

/**
 * Home Assistant の REST クライアント。
 *
 * 天気もカレンダーも外気温の履歴も HA が既に持っているので、
 * 気象 API やカレンダー API と個別に繋ぎ込まない。認証も HA のトークン1つで済む。
 *
 * 依存を増やさないため HttpURLConnection と org.json だけで書いてある。
 *
 * 取得系はすべて Result を返す。失敗を空リストや null に潰すと、
 * 呼ぶ側で「HA が落ちている」と「本当に予定が無い」の区別が付かず、
 * 通信が一瞬切れるだけで画面が「取得中」へ巻き戻ってしまうため。
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

    private fun open(path: String, method: String): HttpURLConnection {
        val url = URL(baseUrl.trimEnd('/') + path)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
    }

    /**
     * 1往復ぶんの定型。応答本文を返し、2xx でなければ投げる。
     *
     * errorStream を読み捨ててから disconnect するのは、読まずに閉じると
     * ソケットが再利用されず、HA が落ちている間に接続が溜まるため。
     *
     * nullOn404 を立てた呼び出しだけは、エンティティが無いことを
     * 通信の失敗と区別して null で返す。
     */
    private fun fetch(
        path: String,
        method: String = "GET",
        body: String? = null,
        nullOn404: Boolean = false,
    ): String? {
        val c = open(path, method)
        try {
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                val detail = runCatching {
                    c.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (code == 404 && nullOn404) return null
                throw HaException(code, detail)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching(block) }

    /**
     * 接続テスト。設定画面から呼ぶ。
     * 成功なら HA が返すメッセージ ("API running.")、失敗なら理由を返す。
     */
    suspend fun ping(): Result<String> = io {
        JSONObject(fetch("/api/")!!).optString("message", "OK")
    }

    /** 見つからない (404) ときだけ null を成功として返す。通信の失敗とは区別する。 */
    suspend fun state(entityId: String): Result<EntityState?> = io {
        val text = fetch("/api/states/${enc(entityId)}", nullOn404 = true)
            ?: return@io null
        val o = JSONObject(text)
        EntityState(
            entityId = o.optString("entity_id"),
            state = o.optString("state"),
            attributes = o.optJSONObject("attributes") ?: JSONObject()
        )
    }

    /**
     * 天気予報。
     * HA 2024.4 以降 forecast は属性から外され、サービス呼び出しで取る形になった。
     */
    suspend fun forecast(entityId: String, type: String = "daily"): Result<List<ForecastEntry>> = io {
        val text = fetch(
            path = "/api/services/weather/get_forecasts?return_response=true",
            method = "POST",
            body = JSONObject().put("entity_id", entityId).put("type", type).toString(),
        )!!
        val svc = JSONObject(text).optJSONObject("service_response") ?: return@io emptyList()
        val ent = svc.optJSONObject(entityId) ?: return@io emptyList()
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
    }

    /** センサーの推移。外気温グラフ用。 */
    suspend fun history(entityId: String, hours: Int): Result<List<TrendPoint>> = io {
        val start = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS).toString()
        val path = "/api/history/period/${enc(start)}" +
            "?filter_entity_id=${enc(entityId)}&minimal_response&significant_changes_only"
        val outer = JSONArray(fetch(path)!!)
        if (outer.length() == 0) return@io emptyList()
        val series = outer.getJSONArray(0)
        (0 until series.length()).mapNotNull { i ->
            val o = series.getJSONObject(i)
            val v = o.optString("state").toDoubleOrNull() ?: return@mapNotNull null
            val t = runCatching {
                Instant.parse(o.optString("last_changed").ifBlank { o.optString("last_updated") })
            }.getOrNull() ?: return@mapNotNull null
            TrendPoint(t, v)
        }.sortedBy { it.at }
    }

    /** 直近の予定。時計の隣に出す用途なので件数は絞って呼ぶ。 */
    suspend fun calendarEvents(entityId: String, days: Int = 7): Result<List<CalendarEvent>> = io {
        val start = ZonedDateTime.now()
        val end = start.plusDays(days.toLong())
        val path = "/api/calendars/${enc(entityId)}" +
            "?start=${enc(start.toInstant().toString())}&end=${enc(end.toInstant().toString())}"
        val arr = JSONArray(fetch(path)!!)
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
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 8_000
    }
}
