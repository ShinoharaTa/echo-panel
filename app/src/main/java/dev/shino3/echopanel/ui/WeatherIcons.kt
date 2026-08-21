package dev.shino3.echopanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 天気アイコン。Canvas に単色シルエットで描く。
 *
 * 画像アセットや絵文字に頼らないので、この ROM のフォント事情に依存せず
 * どのサイズでも滑らかに出る。形は天気アプリで見慣れた記号
 * (太陽・雲・雨脚・雪・稲妻・月) に合わせ、条件名は HA の condition をそのまま受ける。
 */
@Composable
fun WeatherIcon(
    condition: String?,
    size: Dp,
    tint: Color = T.fg,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension
        when (condition) {
            "sunny" -> sun(tint, u, cx = 0.5f, cy = 0.5f, r = 0.24f)
            "clear-night" -> moon(tint, u)
            "partlycloudy" -> {
                sun(tint, u, cx = 0.38f, cy = 0.34f, r = 0.16f)
                cloud(tint, u, dy = 0.10f)
            }
            "cloudy" -> cloud(tint, u)
            "fog" -> lines(tint, u, listOf(0.20f to 0.80f, 0.14f to 0.74f, 0.26f to 0.86f))
            "windy", "windy-variant" -> lines(tint, u, listOf(0.14f to 0.70f, 0.20f to 0.86f, 0.14f to 0.60f))
            "rainy" -> { cloud(tint, u, dy = -0.10f); drops(tint, u, 3, long = false) }
            "pouring" -> { cloud(tint, u, dy = -0.10f); drops(tint, u, 4, long = true) }
            "snowy", "hail" -> { cloud(tint, u, dy = -0.10f); flakes(tint, u) }
            "snowy-rainy" -> {
                cloud(tint, u, dy = -0.10f)
                drops(tint, u, 2, long = false)
                flakeAt(tint, u, 0.5f)
            }
            "lightning", "lightning-rainy", "exceptional" -> {
                cloud(tint, u, dy = -0.10f)
                bolt(tint, u)
            }
            else -> drawLine(
                tint,
                Offset(0.35f * u, 0.5f * u), Offset(0.65f * u, 0.5f * u),
                strokeWidth = 0.08f * u, cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.sun(c: Color, u: Float, cx: Float, cy: Float, r: Float) {
    drawCircle(c, radius = r * u, center = Offset(cx * u, cy * u))
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            c,
            start = Offset(cx * u + dx * (r + 0.07f) * u, cy * u + dy * (r + 0.07f) * u),
            end = Offset(cx * u + dx * (r + 0.17f) * u, cy * u + dy * (r + 0.17f) * u),
            strokeWidth = 0.07f * u,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.moon(c: Color, u: Float) {
    val full = Path().apply { addOval(Rect(center = Offset(0.5f * u, 0.5f * u), radius = 0.30f * u)) }
    val bite = Path().apply { addOval(Rect(center = Offset(0.64f * u, 0.38f * u), radius = 0.27f * u)) }
    drawPath(Path.combine(PathOperation.Difference, full, bite), c)
}

/** 円3つ + 台座の角丸矩形。同色で重ねるとシルエットが雲になる */
private fun DrawScope.cloud(c: Color, u: Float, dy: Float = 0f) {
    val y = dy * u
    drawCircle(c, 0.16f * u, Offset(0.32f * u, 0.56f * u + y))
    drawCircle(c, 0.21f * u, Offset(0.54f * u, 0.46f * u + y))
    drawCircle(c, 0.14f * u, Offset(0.72f * u, 0.58f * u + y))
    drawRoundRect(
        c,
        topLeft = Offset(0.18f * u, 0.52f * u + y),
        size = Size(0.66f * u, 0.20f * u),
        cornerRadius = CornerRadius(0.10f * u)
    )
}

private fun DrawScope.drops(c: Color, u: Float, n: Int, long: Boolean) {
    val xs = when (n) {
        2 -> listOf(0.38f, 0.62f)
        3 -> listOf(0.30f, 0.50f, 0.70f)
        else -> listOf(0.26f, 0.42f, 0.58f, 0.74f)
    }
    val y1 = if (long) 0.97f else 0.89f
    xs.forEach { x ->
        drawLine(
            c,
            Offset(x * u, 0.70f * u), Offset((x - 0.05f) * u, y1 * u),
            strokeWidth = 0.06f * u, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.flakes(c: Color, u: Float) {
    listOf(0.32f, 0.52f, 0.72f).forEach { flakeAt(c, u, it) }
}

private fun DrawScope.flakeAt(c: Color, u: Float, x: Float) {
    drawCircle(c, 0.05f * u, Offset(x * u, 0.83f * u))
}

private fun DrawScope.bolt(c: Color, u: Float) {
    val p = Path().apply {
        moveTo(0.54f * u, 0.60f * u)
        lineTo(0.40f * u, 0.80f * u)
        lineTo(0.50f * u, 0.80f * u)
        lineTo(0.44f * u, 0.98f * u)
        lineTo(0.64f * u, 0.74f * u)
        lineTo(0.53f * u, 0.74f * u)
        close()
    }
    drawPath(p, c)
}

/** 水平線の束。fog と windy をずらしで描き分ける。(x0 to x1) の並びで上から */
private fun DrawScope.lines(c: Color, u: Float, spans: List<Pair<Float, Float>>) {
    val ys = listOf(0.32f, 0.50f, 0.68f)
    spans.forEachIndexed { i, (x0, x1) ->
        drawLine(
            c,
            Offset(x0 * u, ys[i] * u), Offset(x1 * u, ys[i] * u),
            strokeWidth = 0.08f * u, cap = StrokeCap.Round
        )
    }
}
