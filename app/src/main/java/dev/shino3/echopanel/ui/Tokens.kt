package dev.shino3.echopanel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 画面が 787 x 394 dp しかないため、値は必ずここに集約する。
 *
 * 寸法と字の役割はここが持ち切りで、配色だけは [Palette] に外出ししてある。
 * 部屋の明るさと照明の色によって要る配色が違う一方、787 x 394dp に収まる
 * 寸法は1通りしか無いため。呼ぶ側は今までどおり `T.fg` と書けばよく、
 * テーマを差し替えると読んでいる画面がそのまま追従する。
 */
object T {
    /** 現在の配色。差し替えると画面全体が追い掛ける */
    var palette: Palette by mutableStateOf(Themes.Standard)

    // 面
    val bg: Color get() = palette.bg
    val surface: Color get() = palette.surface
    val surfaceHi: Color get() = palette.surfaceHi
    val line: Color get() = palette.line

    // 文字。最弱の fgFaint は見出し・ドット・非選択タブなど脇役専用で、
    // データを背負う字には使わない
    val fg: Color get() = palette.fg
    val fgMuted: Color get() = palette.fgMuted
    val fgFaint: Color get() = palette.fgFaint

    // 間隔
    val gapXs = 4.dp
    val gapS = 8.dp
    val gapM = 16.dp
    val gapL = 24.dp
    val edge = 20.dp

    // 文字。役割は4段 + 大数字で、これ以外のサイズを直書きしない。
    // タイル側で倍率を掛けるときも基準はここから取る (T.bodySize * scale)。
    val labelSize = 12.sp   // 見出し・曜日・補足。fgFaint / fgMuted と組で使う
    val bodySize = 14.sp    // 文。予定の件名・天気の言葉・ボタン
    val valueSize = 20.sp   // 並びの中の数値。予報の最高気温など
    val numSize = 28.sp     // タイルの主数値。推移の現在値など

    // 主役の数字 (時計・タイマー・現在気温) は領域の高さ × この係数で自動サイズ。
    // ページごとに係数がずれると主役の格が揃わないので1つに固定する
    val heroFrac = 0.44f

    // 大数字も含めて通常ウェイトで統一する。Light は 195dpi では
    // サイズに対して線が細く、机の距離だと視認性が落ちる (実機比較で決定)。
    val displayWeight = FontWeight.Normal

    // 見出し (labelSize + fgFaint) は一番小さく一番暗い組なので、
    // ウェイトで少し立たせて線の細さを補う
    val labelWeight = FontWeight.Medium

    val radius = 10.dp

    /** タイル外側の余白。両側で合わさって 6dp ギャップになる */
    val gapTile = 3.dp

    // ガラスカード
    val glass: Color get() = palette.glass           // ブラー背景の上に敷く面
    val glassSolid: Color get() = palette.glassSolid // ブラー画像が無いときのフォールバック
    val glassEdge: Color get() = palette.glassEdge   // 1px の縁ハイライト

    // 意味色 (閾値制。装飾には使わない)
    val accHot: Color get() = palette.accHot     // 30°以上 / 予定15分前 / 古い値
    val accCold: Color get() = palette.accCold   // 10°以下 / 降水50%以上
    val accAlert: Color get() = palette.accAlert // 予定5分前

    /** 背景の上に敷く幕の色。明るいテーマでは白で戻す */
    val scrim: Color get() = palette.scrim
}
