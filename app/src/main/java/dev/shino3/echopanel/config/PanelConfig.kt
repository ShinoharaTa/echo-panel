package dev.shino3.echopanel.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 画面構成は JSON で外に出す。
 *
 * 5.5インチのタッチでレイアウトを編集するのは現実的でないので、
 * Mac から adb push で差し替えて即反映する運用にしている。
 *
 *   adb push panels.json /sdcard/Android/data/dev.shino3.echopanel/files/panels.json
 *
 * このパスは root なしで書き込める。ファイルの更新時刻を監視して自動で読み直す。
 */

enum class Dir { ROW, COLUMN }

/**
 * レイアウトの節点。タイルそのものか、さらに分割するグループのどちらか。
 * weight で 2:1 のような比率を表す。2分割も3分割も入れ子もこれで足りる。
 */
sealed class Node {
    abstract val weight: Float

    data class Tile(
        override val weight: Float,
        val type: String,
        val props: JSONObject,
    ) : Node() {
        fun str(key: String, def: String = ""): String = props.optString(key, def)
        fun bool(key: String, def: Boolean = false): Boolean = props.optBoolean(key, def)
        fun int(key: String, def: Int = 0): Int = props.optInt(key, def)
        fun float(key: String, def: Float = 0f): Float =
            props.optDouble(key, def.toDouble()).toFloat()

        // ---- 全タイル共通の表示調整。JSON にそのまま書ける ----

        /** 文字サイズの倍率。0.5〜3.0。既定 1.0 */
        val scale: Float get() = float("scale", 1f).coerceIn(0.5f, 3f)

        /** 背景枠を塗るか。false で素の黒地 */
        val filled: Boolean get() = bool("filled", true)

        /** 見出し行を出すか */
        val showLabel: Boolean get() = bool("showLabel", true)

        /** 見出し。showLabel=false なら null */
        fun labelOrNull(def: String): String? =
            if (showLabel) str("label", def) else null
    }

    data class Group(
        override val weight: Float,
        val dir: Dir,
        val children: List<Node>,
    ) : Node()
}

data class Page(val name: String, val root: Node)

data class PanelConfig(
    val haUrl: String,
    val haToken: String,
    val workMinutes: Int,
    val breakMinutes: Int,
    /** true で HA を呼ばず作り物のデータを流す。レイアウト調整用 */
    val mock: Boolean,
    /** mock 時の天気 condition。背景の雨/雪/星の確認に使う */
    val mockCondition: String,
    /** 背景レイヤーが参照する weather エンティティ */
    val weatherEntity: String,
    val pages: List<Page>,
) {
    val haConfigured: Boolean get() = mock || (haUrl.isNotBlank() && haToken.isNotBlank())

    companion object {
        private const val FILE = "panels.json"

        fun file(c: Context): File = File(c.getExternalFilesDir(null), FILE)

        fun load(c: Context): PanelConfig {
            val f = file(c)
            if (!f.exists()) {
                runCatching { f.parentFile?.mkdirs(); f.writeText(DEFAULT_JSON) }
            }
            val text = runCatching { f.readText() }.getOrNull().takeIf { !it.isNullOrBlank() }
                ?: DEFAULT_JSON
            return runCatching { parse(text) }.getOrElse { parse(DEFAULT_JSON) }
        }

        fun parse(text: String): PanelConfig {
            val o = JSONObject(text)
            val pages = mutableListOf<Page>()
            val arr = o.optJSONArray("pages") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                pages += Page(
                    name = p.optString("name", "page$i"),
                    root = parseNode(p.getJSONObject("layout"))
                )
            }
            return PanelConfig(
                haUrl = o.optString("haUrl", ""),
                haToken = o.optString("haToken", ""),
                workMinutes = o.optInt("pomodoroWorkMinutes", 25),
                breakMinutes = o.optInt("pomodoroBreakMinutes", 5),
                mock = o.optBoolean("mock", false),
                mockCondition = o.optString("mockCondition", "partlycloudy"),
                weatherEntity = o.optString("weatherEntity", ""),
                pages = pages.ifEmpty { parse(DEFAULT_JSON).pages }
            )
        }

        private fun parseNode(o: JSONObject): Node {
            val weight = o.optDouble("weight", 1.0).toFloat().coerceAtLeast(0.01f)
            val children = o.optJSONArray("children")
            return if (children != null) {
                val dir = if (o.optString("dir", "row").lowercase() == "column") Dir.COLUMN else Dir.ROW
                val list = (0 until children.length()).map { parseNode(children.getJSONObject(it)) }
                Node.Group(weight, dir, list)
            } else {
                Node.Tile(
                    weight = weight,
                    type = o.optString("type", "unknown"),
                    props = o
                )
            }
        }

        /**
         * 初回起動時に書き出される既定構成。
         * 1枚目が 2:1 の 2分割、右側をさらに縦2分割した入れ子の例になっている。
         */
        val DEFAULT_JSON: String = """
{
  "haUrl": "http://10.1.111.145:8123",
  "haToken": "",
  "weatherEntity": "",

  "pomodoroWorkMinutes": 25,
  "pomodoroBreakMinutes": 5,

  "_comment": "weight で比率を指定する。children があればグループ、無ければタイル。",
  "_tiles": ["clock", "pomodoro", "weather.now", "weather.forecast", "sensor.trend", "calendar", "ha.web", "blank"],
  "_style": "全タイル共通: scale(文字倍率 0.5-3), filled(枠の塗り), showLabel(見出し), label(見出し文字)",

  "pages": [
    {
      "name": "ホーム",
      "layout": {
        "dir": "row",
        "children": [
          {
            "weight": 2,
            "dir": "column",
            "children": [
              { "weight": 2.4, "type": "clock", "seconds": false, "showDate": true },
              { "weight": 1, "type": "sensor.trend", "entity": "", "hours": 24, "label": "外気温", "scale": 0.8, "filled": false }
            ]
          },
          {
            "weight": 1,
            "dir": "column",
            "children": [
              { "weight": 1.2, "type": "weather.now", "entity": "", "showLabel": false },
              { "weight": 1, "type": "weather.forecast", "entity": "", "count": 3, "showLabel": false, "scale": 0.9 },
              { "weight": 1, "type": "calendar", "entity": "", "count": 3, "showLabel": false }
            ]
          }
        ]
      }
    },
    {
      "name": "ポモドーロ",
      "layout": { "dir": "row", "children": [ { "weight": 1, "type": "pomodoro" } ] }
    },
    {
      "name": "天気詳細",
      "layout": {
        "dir": "column",
        "children": [
          { "weight": 1, "type": "weather.forecast", "entity": "", "count": 5 },
          { "weight": 1, "type": "sensor.trend", "entity": "", "hours": 24, "label": "外気温" }
        ]
      }
    },
    {
      "name": "Home Assistant",
      "layout": { "dir": "row", "children": [ { "weight": 1, "type": "ha.web" } ] }
    }
  ]
}
        """.trimIndent()
    }
}
