package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.ui.PomodoroPanel
import dev.shino3.echopanel.ui.T
import dev.shino3.echopanel.ui.WebPanel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** タイル種別の振り分け。JSON の "type" がここに来る。 */
@Composable
fun RenderTile(tile: Node.Tile, cfg: PanelConfig) {
    when (tile.type) {
        "clock" -> ClockTile(tile)
        "pomodoro" -> PomodoroPanel()
        "weather.now" -> WeatherNowTile(tile, cfg)
        "weather.forecast" -> ForecastTile(tile, cfg)
        "sensor.trend" -> TrendTile(tile, cfg)
        "calendar" -> CalendarTile(tile, cfg)
        "ha.web" -> WebPanel(cfg.haUrl)
        "blank" -> Unit
        else -> TileFrame { TileNotice("未知のタイル: ${tile.type}") }
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

/**
 * 時計。
 * 割り当てられた領域の高さから文字サイズを決めるので、
 * 全画面でも 2:1 の右側でも同じタイルが使える。
 */
@Composable
fun ClockTile(tile: Node.Tile) {
    val seconds = tile.bool("seconds", false)
    val showDate = tile.bool("showDate", true)
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(seconds) {
        while (true) {
            now = LocalDateTime.now()
            delay(if (seconds) 500L else 5_000L)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 高さと幅の両方から上限を掛ける。横に細長い枠でも溢れない。
        // Column に入ると DSL マーカーで maxHeight が見えなくなるので先に取り出す。
        val boxH = maxHeight.value
        val byHeight = boxH * 0.46f
        val byWidth = maxWidth.value / (if (seconds) 4.6f else 3.1f)
        val size = (minOf(byHeight, byWidth) * tile.scale).coerceIn(20f, 200f)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%d:%02d".format(now.hour, now.minute) +
                    if (seconds) ":%02d".format(now.second) else "",
                color = T.fg,
                fontSize = size.sp,
                fontWeight = FontWeight.Light
            )
            if (showDate && boxH > 90f) {
                Text(
                    text = now.format(DATE_FMT),
                    color = T.fgMuted,
                    fontSize = (size * 0.15f).coerceIn(10f, 20f).sp
                )
            }
        }
    }
}
