package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.data.MockData
import dev.shino3.echopanel.ui.T
import kotlinx.coroutines.delay

/**
 * センサーの推移。外気温の 24 時間推移を想定している。
 * 目盛りは描かず、最小・最大・現在値の 3 つだけ数字で添える。
 * 5.5インチで軸ラベルを描いても読めないため。
 */
@Composable
fun TrendTile(tile: Node.Tile, cfg: PanelConfig) {
    val entity = tile.str("entity")
    val hours = tile.int("hours", 24).coerceIn(1, 168)
    val client = remember(cfg.haUrl, cfg.haToken) {
        if (cfg.haConfigured) HaClient(cfg.haUrl, cfg.haToken) else null
    }
    var points by remember { mutableStateOf<List<HaClient.TrendPoint>>(emptyList()) }

    LaunchedEffect(entity, hours, client, cfg.mock) {
        if (cfg.mock) { points = MockData.trend(hours); return@LaunchedEffect }
        if (client == null || entity.isBlank()) return@LaunchedEffect
        while (true) {
            points = client.history(entity, hours)
            delay(10 * 60_000L)
        }
    }

    TileFrame(
        label = tile.labelOrNull("推移")?.let { "$it ${hours}h" },
        filled = tile.filled
    ) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() && !cfg.mock -> TileNotice("entity 未設定")
            points.size < 2 -> TileNotice("データ不足")
            else -> {
                val s = tile.scale
                val values = points.map { it.value }
                val min = values.min()
                val max = values.max()
                val span = (max - min).takeIf { it > 0.01 } ?: 1.0

                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "%.1f".format(values.last()),
                            color = T.fg,
                            fontSize = (26 * s).sp
                        )
                        Text(
                            text = "  最低 %.1f  最高 %.1f".format(min, max),
                            color = T.fgFaint,
                            fontSize = (10 * s).sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp))

                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) return@Canvas

                        val path = Path()
                        points.forEachIndexed { i, p ->
                            val x = w * (i.toFloat() / (points.size - 1).toFloat())
                            val y = h - ((p.value - min) / span).toFloat() * h
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = T.fg, style = Stroke(width = 2f))

                        // 現在値の位置に点を打つ
                        val lastX = w
                        val lastY = h - ((values.last() - min) / span).toFloat() * h
                        drawCircle(color = T.fg, radius = 3f, center = Offset(lastX - 2f, lastY))
                    }
                }
            }
        }
    }
}
