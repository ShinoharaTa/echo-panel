package dev.shino3.echopanel.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.shino3.echopanel.astro.Celestial
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 画面全体の背景。
 *
 * 静的レイヤー(ドット方眼 + 天体の弧 + 太陽/月 + 晴夜の星)を1枚の Bitmap に描き、
 * 縮小→拡大の2パスで「ブラー版」も作る。カードは自分の位置に対応する
 * ブラー版の切片を敷くことで、API 30 に backdrop blur が無くてもガラス越しに見える。
 * 太陽/月は1分に1回しか動かないので、再生成コストは実質ゼロ。
 */
class BackdropState {
    var sharp by mutableStateOf<ImageBitmap?>(null)
    var blurred by mutableStateOf<ImageBitmap?>(null)
}

val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

object BackdropRenderer {

    fun render(w: Int, h: Int, condition: String?, now: ZonedDateTime): Pair<Bitmap, Bitmap> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.BLACK)

        drawDotGrid(c, w, h)

        val sky = Celestial.skyPos(now.toLocalTime())
        drawArc(c, w, h)
        val (px, py) = arcPoint(w, h, sky.t)
        if (sky.isDay) {
            drawSun(c, px, py, w * 0.012f)
        } else {
            if (isClear(condition)) drawStars(c, w, h)
            drawMoon(c, px, py, w * 0.011f, Celestial.moonPhase(now.toInstant()))
        }

        return bmp to fakeBlur(bmp)
    }

    /** 縮小→拡大の2パス。RenderScript を使わずに済み、方眼と線の背景には十分なボケ量 */
    private fun fakeBlur(src: Bitmap): Bitmap {
        val w1 = (src.width / 10).coerceAtLeast(1)
        val h1 = (src.height / 10).coerceAtLeast(1)
        val s1 = Bitmap.createScaledBitmap(src, w1, h1, true)
        val s2 = Bitmap.createScaledBitmap(s1, (w1 / 2).coerceAtLeast(1), (h1 / 2).coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(s2, src.width, src.height, true)
    }

    private fun drawDotGrid(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0EFFFFFF }
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

    private fun drawSun(c: Canvas, x: Float, y: Float, r: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFA94D }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CFFA94D.toInt() }
        c.drawCircle(x, y, r * 2.2f, glow)
        c.drawCircle(x, y, r, core)
    }

    /**
     * 月齢つきの月。
     * 照っている側の外周半円 + 明暗境界の半楕円で満ち欠けを作る。
     * phase: 0=新月, 0.25=上弦(右半分), 0.5=満月, 0.75=下弦(左半分)
     */
    private fun drawMoon(c: Canvas, x: Float, y: Float, r: Float, phase: Float) {
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x17FFFFFF }
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x7DDCE4F0.toInt() }
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

    val color = if (snowy) Color(0x26FFFFFF) else Color(0x1F5CB8FF)
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
