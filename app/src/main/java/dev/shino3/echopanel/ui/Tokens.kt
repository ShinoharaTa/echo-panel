package dev.shino3.echopanel.ui

import androidx.compose.ui.graphics.Color
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

    // 文字
    val fg = Color(0xFFF2F2F2)
    val fgMuted = Color(0xFF8A8A8A)
    val fgFaint = Color(0xFF4A4A4A)

    // 間隔
    val gapXs = 4.dp
    val gapS = 8.dp
    val gapM = 16.dp
    val gapL = 24.dp
    val edge = 20.dp

    // 文字サイズ
    val displaySize = 108.sp
    val timerSize = 96.sp
    val titleSize = 20.sp
    val bodySize = 15.sp
    val labelSize = 12.sp

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
