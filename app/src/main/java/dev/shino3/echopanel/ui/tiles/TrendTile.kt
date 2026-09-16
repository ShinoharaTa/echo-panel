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
import androidx.compose.runtime.remember
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
import dev.shino3.echopanel.data.rememberFeed
import dev.shino3.echopanel.ui.T

/** 履歴の取得間隔 */
private const val TREND_INTERVAL_MS = 10 * 60_000L

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
    val feed = rememberFeed<List<HaClient.TrendPoint>>("sensor.trend", emptyList(), entity, hours)

    LaunchedEffect(entity, hours, client, cfg.mock) {
        if (cfg.mock) { feed.set(MockData.trend(hours)); return@LaunchedEffect }
        if (client == null || entity.isBlank()) return@LaunchedEffect
        feed.poll(TREND_INTERVAL_MS) { client.history(entity, hours) }
    }

    val points = feed.value
    TileFrame(
        label = tile.labelOrNull("推移")?.let { "$it ${hours}h" },
        filled = tile.filled,
        stale = feed.isStale(TREND_INTERVAL_MS)
    ) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() && !cfg.mock -> TileNotice("entity 未設定")
            points.size < 2 -> TileNotice(
                if (feed.failing && !feed.hasValue) "HA に届かない" else "データ不足"
            )
            else -> {
                val s = tile.scale
                val values = points.map { it.value }
                val min = values.min()
                val max = values.max()
                val span = (max - min).takeIf { it > 0.01 } ?: 1.0

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = "%.1f".format(values.last()),
                            color = T.fg,
                            fontSize = (T.numSize.value * s).sp,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "  最低 %.1f  最高 %.1f".format(min, max),
                            color = T.fgMuted,
                            fontSize = (T.labelSize.value * s).sp,
                            modifier = Modifier.alignByBaseline()
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

                        // 横軸は時刻。HA からは significant_changes_only で取っていて
                        // 点の間隔が一定でないため、添字を x にすると
                        // 「変化が少なかった時間帯」が不当に広がって形が歪む。
                        val t0 = points.first().at.toEpochMilli()
                        val tSpan = (points.last().at.toEpochMilli() - t0)
                            .coerceAtLeast(1L).toFloat()

                        val path = Path()
                        points.forEachIndexed { i, p ->
                            val x = w * ((p.at.toEpochMilli() - t0).toFloat() / tSpan)
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
