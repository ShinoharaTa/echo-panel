package dev.shino3.echopanel.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.ZonedDateTime

/**
 * 配色の一式。
 *
 * テーマを増やしてもタイル側は一切触らない。画面の文法
 * (下地はモノクロ、有彩色は閾値を超えたときだけ灯る) はテーマをまたいで
 * 変えないので、テーマが決めるのは「どの明るさの下地に、どの色で意味を
 * 灯すか」だけになる。増やすときは [Themes.all] に1つ足す。
 *
 * 寸法と字の役割 (T の sp/dp) はテーマに入れない。画面が 787 x 394dp
 * しかなく、そこに収まる寸法は1通りしか無いため。
 */
@Immutable
data class Palette(
    /** panels.json と設定画面で指す名前 */
    val id: String,
    /** 設定画面に出す表示名 */
    val label: String,
    /** その配色をいつ使うか。設定画面の説明に出す */
    val note: String,

    // 面
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,

    // 文字。最弱の fgFaint でもコントラスト比 4:1 を割らない明るさにする。
    // 割ると机の距離から読めなくなる (旧 fgFaint 0xFF4A4A4A で確認済み)
    val fg: Color,
    val fgMuted: Color,
    val fgFaint: Color,

    // ガラスカード
    val glass: Color,
    val glassSolid: Color,
    val glassEdge: Color,

    // 意味色 (閾値制。装飾には使わない)
    val accHot: Color,
    val accCold: Color,
    val accAlert: Color,

    /**
     * 背景の上に敷く幕の色。
     * 明るい下地のテーマでは白で戻さないと、暗い写真の上で字が沈む。
     */
    val scrim: Color,

    /**
     * 幕の濃さに掛ける倍率。panels.json の `backdropScrim` に乗る。
     *
     * 文字色のコントラストはガラスカードの面を基準に決めてあるが、
     * 主役 (時計・気温) と `filled: false` のタイルは背景の上に直接載る。
     * 明るいテーマの黒文字は、空の中間調の上では card 上より不利になるので、
     * そのぶん幕を余分に敷いて背景側を退かせる。
     */
    val scrimScale: Float = 1f,
)

object Themes {

    /** 既定。黒地・白文字。開発中ずっとこれで詰めてきた配色 */
    val Standard = Palette(
        id = "dark",
        label = "標準",
        note = "黒地に白。昼夜どちらでも破綻しない",
        bg = Color(0xFF000000),
        surface = Color(0xFF141414),
        surfaceHi = Color(0xFF1F1F1F),
        line = Color(0xFF2E2E2E),
        fg = Color(0xFFF2F2F2),      // 約 18:1
        fgMuted = Color(0xFFA9A9A9), // 約 8:1
        fgFaint = Color(0xFF767676), // 約 4.3:1
        glass = Color(0x99121212),
        glassSolid = Color(0xE6141414),
        glassEdge = Color(0x14FFFFFF),
        accHot = Color(0xFFFFA94D),
        accCold = Color(0xFF5CB8FF),
        accAlert = Color(0xFFFF6B6B),
        scrim = Color.Black,
    )

    /**
     * 夜。寝室で点けっぱなしにしても眩しくない明るさまで全体を落とす。
     * 意味色も彩度を下げる。暗い部屋では飽和した色だけが浮いて見えるため。
     */
    val Night = Palette(
        id = "night",
        label = "夜",
        note = "寝室向け。全体を落として意味色も鈍らせる",
        bg = Color(0xFF000000),
        surface = Color(0xFF0E0E0E),
        surfaceHi = Color(0xFF161616),
        line = Color(0xFF242424),
        fg = Color(0xFFCFCFCF),      // 約 11:1
        fgMuted = Color(0xFF8A8A8A), // 約 5.3:1
        fgFaint = Color(0xFF757575), // 約 4.2:1
        glass = Color(0xB30A0A0A),
        glassSolid = Color(0xF00C0C0C),
        glassEdge = Color(0x0DFFFFFF),
        accHot = Color(0xFFC98A45),
        accCold = Color(0xFF5E93C4),
        accAlert = Color(0xFFC25A5A),
        scrim = Color.Black,
    )

    /**
     * 昼。明るい部屋で黒地の画面が「消えている」ように見えるのを避ける。
     * 幕は白で敷く。暗い写真を背景にしたとき、黒で戻すと字が沈むため。
     */
    val Daylight = Palette(
        id = "light",
        label = "昼",
        note = "明るい部屋向け。白地に黒。幕も白で敷く",
        bg = Color(0xFFEDEDED),
        surface = Color(0xFFFFFFFF),
        surfaceHi = Color(0xFFE4E4E4),
        line = Color(0xFFD0D0D0),
        // 背景の上に直接載る字もあるので、暗いテーマより一段深くする
        fg = Color(0xFF1A1A1A),      // 約 15:1 (白カード上)
        fgMuted = Color(0xFF3C3C3C), // 約 10:1
        fgFaint = Color(0xFF5A5A5A), // 約 6.4:1
        glass = Color(0xB3F4F4F4),
        glassSolid = Color(0xF2F4F4F4),
        glassEdge = Color(0x1A000000),
        accHot = Color(0xFFB35A06),
        accCold = Color(0xFF0B63B8),
        accAlert = Color(0xFFBC2530),
        scrim = Color.White,
        scrimScale = 1.7f,
    )

    /**
     * 暖色。電球色の照明の下では白い字が青く浮く。下地ごと温度を寄せる。
     */
    val Warm = Palette(
        id = "warm",
        label = "暖色",
        note = "電球色の部屋向け。白の青浮きを抑える",
        bg = Color(0xFF14110D),
        surface = Color(0xFF1C1813),
        surfaceHi = Color(0xFF262019),
        line = Color(0xFF332B21),
        fg = Color(0xFFF5EDDF),      // 約 16:1
        fgMuted = Color(0xFFB0A48F), // 約 8:1
        fgFaint = Color(0xFF7D7364), // 約 4.1:1
        glass = Color(0x991A150E),
        glassSolid = Color(0xE61C1813),
        glassEdge = Color(0x14FFF2DD),
        accHot = Color(0xFFFFB259),
        accCold = Color(0xFF7FC4FF),
        accAlert = Color(0xFFFF8A7A),
        scrim = Color.Black,
    )

    val all = listOf(Standard, Night, Daylight, Warm)

    /** panels.json / 設定画面で選べる id。auto は時刻で昼と夜を入れ替える */
    const val AUTO = "auto"

    /** 夜のテーマに入れ替える時刻。日の出入りではなく固定でよい (部屋の照明の話なので) */
    private val DAY_HOURS = 6..17

    fun resolve(id: String, at: ZonedDateTime): Palette = when (id) {
        AUTO -> if (at.hour in DAY_HOURS) Daylight else Night
        else -> all.firstOrNull { it.id == id } ?: Standard
    }
}
