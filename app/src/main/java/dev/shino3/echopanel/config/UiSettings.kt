package dev.shino3.echopanel.config

import android.content.Context

/**
 * 見た目のうち、端末の前で切り替えたいもの。
 *
 * 配色は部屋の明るさと照明の色で決まるので、置き場所を変えたときや
 * 夜になったときにその場で切り替えられないと意味がない。
 * panels.json を Mac から push し直すのでは間に合わないため、
 * HA の接続設定と同じく端末内部に持ち、panels.json の同名キーより優先する。
 *
 * 空文字は「未設定」で、panels.json の値にフォールバックする。
 */
data class UiSettings(
    val theme: String = "",
) {
    val isEmpty: Boolean get() = theme.isBlank()

    companion object {
        private const val PREFS = "ui_settings"
        private const val K_THEME = "theme"

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(c: Context): UiSettings =
            UiSettings(theme = prefs(c).getString(K_THEME, "") ?: "")

        fun save(c: Context, s: UiSettings) {
            prefs(c).edit().putString(K_THEME, s.theme.trim()).apply()
        }

        fun clear(c: Context) {
            prefs(c).edit().clear().apply()
        }
    }
}
