package dev.shino3.echopanel.config

import android.content.Context

/**
 * 端末の設定画面から入れる Home Assistant の接続情報。
 *
 * panels.json は外部ストレージ (/sdcard) にあり adb で誰でも読めるので、
 * トークンはアプリ内部の SharedPreferences に置く。
 * ここに値があれば panels.json の同名キーより優先する。
 * 空欄なら panels.json の値にフォールバックするので、既存の運用は壊れない。
 */
data class HaSettings(
    val haUrl: String = "",
    val haToken: String = "",
    val weatherEntity: String = "",
) {
    val isEmpty: Boolean get() = haUrl.isBlank() && haToken.isBlank() && weatherEntity.isBlank()

    companion object {
        private const val PREFS = "ha_settings"
        private const val K_URL = "haUrl"
        private const val K_TOKEN = "haToken"
        private const val K_WEATHER = "weatherEntity"

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(c: Context): HaSettings {
            val p = prefs(c)
            return HaSettings(
                haUrl = p.getString(K_URL, "") ?: "",
                haToken = p.getString(K_TOKEN, "") ?: "",
                weatherEntity = p.getString(K_WEATHER, "") ?: "",
            )
        }

        fun save(c: Context, s: HaSettings) {
            prefs(c).edit()
                .putString(K_URL, s.haUrl.trim().trimEnd('/'))
                .putString(K_TOKEN, s.haToken.trim())
                .putString(K_WEATHER, s.weatherEntity.trim())
                .apply()
        }

        fun clear(c: Context) {
            prefs(c).edit().clear().apply()
        }
    }
}
