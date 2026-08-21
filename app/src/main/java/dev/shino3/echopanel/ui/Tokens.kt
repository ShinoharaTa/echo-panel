package dev.shino3.echopanel.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 画面が 787 x 394 dp しかないため、値は必ずここに集約する。
 * 配色はモノクロのみ。グラデーションと色相での意味付けは使わない。
 */
object T {
    // 面
    val bg = Color(0xFF000000)
    val surface = Color(0xFF141414)
    val surfaceHi = Color(0xFF1F1F1F)
    val line = Color(0xFF2E2E2E)

    // 文字。ほぼ黒のガラスカードに載るので、最弱の fgFaint でも
    // コントラスト比 4:1 を割らない明るさにする。これより暗くしない。
    // (旧 fgFaint 0xFF4A4A4A は 2.2:1 で、机の距離からは読めなかった)
    val fg = Color(0xFFF2F2F2)      // 約 18:1
    val fgMuted = Color(0xFFA9A9A9) // 約 8:1
    val fgFaint = Color(0xFF767676) // 約 4.3:1

    // 間隔
    val gapXs = 4.dp
    val gapS = 8.dp
    val gapM = 16.dp
    val gapL = 24.dp
    val edge = 20.dp

    // 文字。役割は4段 + 大数字で、これ以外のサイズを直書きしない。
    // タイル側で倍率を掛けるときも基準はここから取る (T.bodySize * scale)。
    val labelSize = 11.sp   // 見出し・曜日・補足。fgFaint / fgMuted と組で使う
    val bodySize = 13.sp    // 文。予定の件名・天気の言葉・ボタン
    val valueSize = 18.sp   // 並びの中の数値。予報の最高気温など
    val numSize = 26.sp     // タイルの主数値。推移の現在値など
    val timerSize = 96.sp   // ポモドーロの残り時間

    // 大数字 (時計・現在気温・タイマー) だけ細くする。
    // 小さい字まで Light にすると 195dpi では線が痩せて読めないので、
    // これより下のサイズは通常ウェイトで統一する。
    val displayWeight = FontWeight.Light

    // 見出し (labelSize + fgFaint) は一番小さく一番暗い組なので、
    // ウェイトで少し立たせて線の細さを補う
    val labelWeight = FontWeight.Medium

    val radius = 10.dp

    /** タイル外側の余白。両側で合わさって 6dp ギャップになる */
    val gapTile = 3.dp

    // ガラスカード
    val glass = Color(0x99121212)      // 60% 黒: ブラー背景の上に敷く
    val glassSolid = Color(0xE6141414) // ブラー画像が無いときのフォールバック
    val glassEdge = Color(0x14FFFFFF)  // 1px の縁ハイライト

    // 意味色 (閾値制。装飾には使わない)
    val accHot = Color(0xFFFFA94D)     // 30°以上 / 予定15分前
    val accCold = Color(0xFF5CB8FF)    // 10°以下 / 降水50%以上
    val accAlert = Color(0xFFFF6B6B)   // 予定5分前
}
