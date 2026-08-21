package dev.shino3.echopanel.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.shino3.echopanel.astro.Celestial
import java.io.File
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 画面全体の背景。Google Nest Hub の Ambient モードと同じ考え方で作る。
 *
 * - 背景は明るくてよい。その代わり文字の下にスクリムを敷いて可読性を守る
 *   (Nest Hub が写真の上に時計を載せられるのはこれをやっているため)
 * - 一定間隔で絵が切り替わる。切り替えはクロスフェード
 * - 写真を置けば写真スライドショー、無ければ天気と時刻に連動した空を描く
 *
 * カードは自分の位置に対応するブラー版の切片を敷くので、
 * API 30 に backdrop blur が無くてもガラス越しに見える。
 */
class BackdropState {
    var sharp by mutableStateOf<ImageBitmap?>(null)
    var blurred by mutableStateOf<ImageBitmap?>(null)
    var prevSharp by mutableStateOf<ImageBitmap?>(null)
    var prevBlurred by mutableStateOf<ImageBitmap?>(null)

    /** 0=前の絵, 1=今の絵。クロスフェードの進み具合 */
    val fade = Animatable(1f)

    suspend fun push(s: ImageBitmap, b: ImageBitmap, animate: Boolean) {
        prevSharp = sharp
        prevBlurred = blurred
        sharp = s
        blurred = b
        if (animate && prevSharp != null) {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(1400, easing = LinearEasing))
        } else {
            fade.snapTo(1f)
        }
    }
}

val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

object BackdropRenderer {

    /** 写真スライドショーに使う画像。ここに置いたものが順に出る */
    fun photoDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "wallpapers")

    fun photoFiles(ctx: Context): List<File> =
        photoDir(ctx).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * 写真を1枚読み、画面サイズにセンタークロップする。
     * 5.5インチに等倍で載せる必要はないので、読み込み時点で間引いて省メモリにする。
     */
    fun renderPhoto(file: File, w: Int, h: Int): Pair<Bitmap, Bitmap>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val src = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out to fakeBlur(out)
    }

    /**
     * 空を描く。時刻帯 (夜明け/昼/夕暮れ/夜) と天気で配色が決まり、
     * variant で同じ時間帯の中の別バリエーションに切り替わる (スライドショー)。
     */
    fun render(
        w: Int,
        h: Int,
        condition: String?,
        now: ZonedDateTime,
        variant: Int,
        brightness: Float,
    ): Pair<Bitmap, Bitmap> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val sky = skyFor(now, condition, variant, brightness)
        drawSky(c, w, h, sky, variant)
        drawDotGrid(c, w, h)

        val pos = Celestial.skyPos(now.toLocalTime())
        drawArc(c, w, h)
        val (px, py) = arcPoint(w, h, pos.t)
        // 曇り/雨/雪の日に太陽が煌々と出ていると絵が矛盾する。
        // 時刻の手がかりとして残しつつ、雲に隠れた分だけ薄くする
        val veil = when {
            isRainy(condition) || isSnowy(condition) -> 0.22f
            condition in setOf("cloudy", "fog") -> 0.40f
            condition == "partlycloudy" -> 0.75f
            else -> 1f
        }
        if (pos.isDay) {
            drawSun(c, px, py, w * 0.012f, veil)
        } else {
            if (isClear(condition)) drawStars(c, w, h)
            drawMoon(c, px, py, w * 0.011f, Celestial.moonPhase(now.toInstant()), veil)
        }
        drawVignette(c, w, h)

        return bmp to fakeBlur(bmp)
    }

    // ---- 配色 ------------------------------------------------------------

    private data class Sky(val top: Int, val mid: Int, val bot: Int)

    private fun hex(v: Long): Int = v.toInt()

    /**
     * 時刻帯ごとに3案。数字は「黒よりは明るいが、白文字が乗る」帯に収めてある。
     * ここを明るくしすぎると、せっかく上げた小さい字のコントラストが死ぬ。
     */
    private fun palettes(hour: Int): List<Sky> = when (hour) {
        in 4..6 -> listOf(                        // 夜明け
            Sky(hex(0xFF201B3A), hex(0xFF573A55), hex(0xFF8A5540)),
            Sky(hex(0xFF1A1B38), hex(0xFF4C3A5E), hex(0xFF95644A)),
            Sky(hex(0xFF241D3E), hex(0xFF5E3F52), hex(0xFF7E5A4E)),
        )
        in 7..15 -> listOf(                       // 昼
            Sky(hex(0xFF0A2440), hex(0xFF14456A), hex(0xFF236785)),
            Sky(hex(0xFF0C2A44), hex(0xFF17507A), hex(0xFF2E7893)),
            Sky(hex(0xFF102A45), hex(0xFF255070), hex(0xFF5E6E75)),
        )
        in 16..18 -> listOf(                      // 夕暮れ
            Sky(hex(0xFF141C40), hex(0xFF4A3358), hex(0xFF96543F)),
            Sky(hex(0xFF181A38), hex(0xFF573650), hex(0xFFA05F3E)),
            Sky(hex(0xFF0E1836), hex(0xFF3E3560), hex(0xFF7C4A44)),
        )
        else -> listOf(                           // 夜。ここは意図して暗いままにする
            Sky(hex(0xFF060B1E), hex(0xFF0D1832), hex(0xFF152744)),
            Sky(hex(0xFF07091C), hex(0xFF101C38), hex(0xFF1B2F4E)),
            Sky(hex(0xFF050A1A), hex(0xFF0A152E), hex(0xFF122340)),
        )
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int): Int {
            val x = (a shr sh) and 0xFF
            val y = (b shr sh) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun scale(c: Int, f: Float): Int {
        fun ch(sh: Int) = (((c shr sh) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun skyFor(now: ZonedDateTime, condition: String?, variant: Int, brightness: Float): Sky {
        val list = palettes(now.hour)
        val base = list[((variant % list.size) + list.size) % list.size]

        // 天気で色味を寄せる。曇りは灰へ、雨は更に沈めて青へ、雪は白っぽく持ち上げる
        val (target, amount, gain) = when {
            isRainy(condition) -> Triple(hex(0xFF1B2833), 0.60f, 0.85f)
            isSnowy(condition) -> Triple(hex(0xFF5C6A78), 0.45f, 1.10f)
            condition in setOf("cloudy", "fog") -> Triple(hex(0xFF39404A), 0.50f, 0.95f)
            else -> Triple(0, 0f, 1f)
        }
        val f = brightness.coerceIn(0.2f, 1.6f) * gain
        fun mix(c: Int) = scale(if (amount > 0f) blend(c, target, amount) else c, f)
        return Sky(mix(base.top), mix(base.mid), mix(base.bot))
    }

    private fun drawSky(c: Canvas, w: Int, h: Int, sky: Sky, variant: Int) {
        // バリエーションごとにグラデーションの角度を少し振る。同じ配色でも絵が変わる
        val tilt = when (((variant % 3) + 3) % 3) {
            0 -> 0.00f
            1 -> 0.18f
            else -> -0.14f
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                w * (0.5f - tilt), 0f,
                w * (0.5f + tilt), h.toFloat(),
                intArrayOf(sky.top, sky.mid, sky.bot),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** 四隅を軽く沈める。視線が中央に残り、端に置いた文字も浮つかない */
    private fun drawVignette(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.5f, maxOf(w, h) * 0.72f,
                intArrayOf(0x00000000, 0x00000000, 0x4D000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** 縮小→拡大の2パス。RenderScript を使わずに済み、背景には十分なボケ量 */
    private fun fakeBlur(src: Bitmap): Bitmap {
        val w1 = (src.width / 10).coerceAtLeast(1)
        val h1 = (src.height / 10).coerceAtLeast(1)
        val s1 = Bitmap.createScaledBitmap(src, w1, h1, true)
        val s2 = Bitmap.createScaledBitmap(s1, (w1 / 2).coerceAtLeast(1), (h1 / 2).coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(s2, src.width, src.height, true)
    }

    private fun drawDotGrid(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0AFFFFFF }
        val step = w / 40f              // 960px なら 24px 間隔
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                c.drawCircle(x, y, w * 0.001f + 0.6f, p)
                x += step
            }
            y += step
        }
    }

    /** 弧のパラメトリック座標。t=0 左端, t=1 右端。 */
    private fun arcPoint(w: Int, h: Int, t: Float): Pair<Float, Float> {
        val x = w * (0.05f + 0.90f * t)
        val y = h * 0.92f - sin(t * PI).toFloat() * h * 0.70f
        return x to y
    }

    private fun drawArc(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1AFFFFFF
            style = Paint.Style.STROKE
            strokeWidth = w * 0.0016f + 0.8f
            pathEffect = DashPathEffect(floatArrayOf(w * 0.0022f, w * 0.014f), 0f)
        }
        val path = Path()
        var first = true
        var i = 0
        while (i <= 60) {
            val (x, y) = arcPoint(w, h, i / 60f)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            i++
        }
        c.drawPath(path, p)
    }

    private fun alpha(argb: Int, f: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * f.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    private fun drawSun(c: Canvas, x: Float, y: Float, r: Float, veil: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0x3EFFC880, veil) }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0xB3FFE0A8.toInt(), veil) }
        c.drawCircle(x, y, r * 2.6f, glow)
        c.drawCircle(x, y, r, core)
    }

    /**
     * 月齢つきの月。
     * 照っている側の外周半円 + 明暗境界の半楕円で満ち欠けを作る。
     * phase: 0=新月, 0.25=上弦(右半分), 0.5=満月, 0.75=下弦(左半分)
     */
    private fun drawMoon(c: Canvas, x: Float, y: Float, r: Float, phase: Float, veil: Float) {
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0x17FFFFFF, veil) }
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0x9EE8EEF8.toInt(), veil) }
        c.drawCircle(x, y, r, dark)   // 影側も薄く残す(輪郭が分かる)

        // 新月付近は照面が無い
        val illum = (1 - cos(2 * PI * phase)) / 2.0
        if (illum < 0.03) return

        val k = cos(2 * PI * phase).toFloat()  // +1 新月 → -1 満月
        val waxing = phase < 0.5f              // 満ちる途中 = 右が照る
        val outer = RectF(x - r, y - r, x + r, y + r)
        val term = RectF(x - r * abs(k), y - r, x + r * abs(k), y + r)

        val path = Path()
        if (waxing) {
            path.arcTo(outer, -90f, 180f)                       // 右外周: 上→下
            path.arcTo(term, 90f, if (k > 0) 180f else -180f)   // 境界: 下→上
        } else {
            path.arcTo(outer, 90f, 180f)                        // 左外周: 下→上
            path.arcTo(term, -90f, if (k > 0) 180f else -180f)  // 境界: 上→下
        }
        path.close()
        c.drawPath(path, lit)
    }

    private fun drawStars(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(20260820)   // 固定シード: 星座は毎晩同じ
        repeat(26) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.62f
            p.color = if (rnd.nextFloat() < 0.3f) 0x59FFFFFF else 0x30FFFFFF
            c.drawCircle(x, y, w * 0.0007f + rnd.nextFloat() * 1.2f, p)
        }
    }

    fun isClear(condition: String?): Boolean =
        condition in setOf("sunny", "clear-night", "partlycloudy", null, "")

    fun isRainy(condition: String?): Boolean =
        condition in setOf("rainy", "pouring", "lightning-rainy")

    fun isSnowy(condition: String?): Boolean =
        condition in setOf("snowy", "snowy-rainy")
}

/**
 * 背景と中身のあいだに敷く幕。
 *
 * 背景を明るくすると小さい灰色の字が沈むので、Nest Hub と同じように
 * 文字が乗る側だけを暗く戻す。上下を濃く、中央を薄くした縦グラデーション。
 * ガラスカードは自前で 60% 黒を敷いているため、ここは箱なしの文字のための幕。
 */
@Composable
fun BackdropScrim(strength: Float, modifier: Modifier = Modifier) {
    if (strength <= 0.01f) return
    val s = strength.coerceIn(0f, 1f)
    ComposeCanvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.62f * s),
                0.45f to Color.Black.copy(alpha = 0.34f * s),
                1.0f to Color.Black.copy(alpha = 0.70f * s),
            )
        )
    }
}

/**
 * 雨/雪のアニメレイヤー。カードより下に置くので、カード裏では実質見えない
 * (=カード裏を避けて描く必要がない)。
 */
@Composable
fun PrecipLayer(condition: String?, modifier: Modifier = Modifier) {
    val rainy = BackdropRenderer.isRainy(condition)
    val snowy = BackdropRenderer.isSnowy(condition)
    if (!rainy && !snowy) return

    val trans = rememberInfiniteTransition(label = "precip")
    val t by trans.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (snowy) 3600 else 1300, easing = LinearEasing), RepeatMode.Restart),
        label = "fall"
    )

    val color = if (snowy) Color(0x33FFFFFF) else Color(0x2E9CC8E8)
    ComposeCanvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (snowy) {
            // 雪: ゆっくり落ちる粒
            val rnd = Random(7)
            repeat(38) { i ->
                val bx = rnd.nextFloat() * w
                val sp = 0.6f + rnd.nextFloat() * 0.7f
                val y = ((rnd.nextFloat() + t * sp) % 1f) * (h + 20f) - 10f
                val sway = sin((t * 6f + i) * PI).toFloat() * 6f
                drawCircle(color, radius = 1.6f + rnd.nextFloat() * 1.4f, center = Offset(bx + sway, y))
            }
        } else {
            // 雨: 斜めの雨脚が流れ落ちる
            val spacing = w / 22f
            val slant = h * 0.28f
            var i = -2
            while (i < 24) {
                val phaseOff = (t * spacing * 2f)
                val x = i * spacing + phaseOff % (spacing * 2f)
                drawLine(
                    color = color,
                    start = Offset(x + slant, -12f),
                    end = Offset(x, h + 12f),
                    strokeWidth = 1.6f
                )
                i++
            }
        }
    }
}
