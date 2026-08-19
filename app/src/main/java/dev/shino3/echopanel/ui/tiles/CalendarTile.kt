package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.ui.T
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)
private val MD: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

/**
 * 直近の予定。
 * Google カレンダーは HA の統合が持っているので、端末に Google アカウントを
 * 置かずに表示できる。GApps を外した構成と噛み合う。
 */
@Composable
fun CalendarTile(tile: Node.Tile, cfg: PanelConfig) {
    val entity = tile.str("entity")
    val count = tile.int("count", 3).coerceIn(1, 8)
    val days = tile.int("days", 7).coerceIn(1, 60)
    val client = remember(cfg.haUrl, cfg.haToken) {
        if (cfg.haConfigured) HaClient(cfg.haUrl, cfg.haToken) else null
    }
    var events by remember { mutableStateOf<List<HaClient.CalendarEvent>>(emptyList()) }

    LaunchedEffect(entity, days, client) {
        if (client == null || entity.isBlank()) return@LaunchedEffect
        while (true) {
            events = client.calendarEvents(entity, days)
            delay(5 * 60_000L)
        }
    }

    TileFrame(label = tile.labelOrNull("予定"), filled = tile.filled) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() -> TileNotice("entity 未設定")
            events.isEmpty() -> TileNotice("予定なし")
            else -> {
                val s = tile.scale
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    events.take(count).forEach { e ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = whenLabel(e),
                                color = T.fgMuted,
                                fontSize = (11 * s).sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = e.summary,
                                color = T.fg,
                                fontSize = (13 * s).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun whenLabel(e: HaClient.CalendarEvent): String = runCatching {
    if (e.allDay) {
        val d = LocalDate.parse(e.start)
        if (d == LocalDate.now()) "終日" else d.format(MD)
    } else {
        val t = ZonedDateTime.parse(e.start)
        if (t.toLocalDate() == LocalDate.now()) t.format(HM) else t.format(MD) + " " + t.format(HM)
    }
}.getOrElse { "—" }
