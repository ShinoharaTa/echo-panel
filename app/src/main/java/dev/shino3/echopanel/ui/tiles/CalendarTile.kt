package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.data.MockData
import dev.shino3.echopanel.ui.T
import kotlinx.coroutines.delay
import java.time.Duration
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
 *
 * 揃えの方針: 時刻欄は固定幅で右寄せ。行によって「10:00」と「8/20 15:30」の
 * 幅が違っても件名の頭が縦に揃う。
 * 直近の時刻付き予定 1 件には「あとN分」のカウントダウンを右端に出す。
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
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    LaunchedEffect(entity, days, client, cfg.mock) {
        if (cfg.mock) { events = MockData.calendar(); return@LaunchedEffect }
        if (client == null || entity.isBlank()) return@LaunchedEffect
        while (true) {
            events = client.calendarEvents(entity, days)
            delay(5 * 60_000L)
        }
    }
    // カウントダウンの時計。30秒刻みで十分
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000L)
        }
    }

    TileFrame(label = tile.labelOrNull("予定"), filled = tile.filled) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() && !cfg.mock -> TileNotice("entity 未設定")
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
                                fontSize = (T.labelSize.value * s).sp,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier
                                    .width((64 * s).dp)
                                    .padding(end = 6.dp)
                            )
                            Text(
                                text = e.summary,
                                color = T.fg,
                                fontSize = (T.bodySize.value * s).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // 12時間以内に始まる時刻付き予定にはすべてカウントダウン
                            val st = if (e.allDay) null else startOf(e)
                            val mins = st?.let { Duration.between(now, it).toMinutes() }
                            if (mins != null && mins >= 0 && mins < 12 * 60) {
                                Text(
                                    text = countdownLabel(now, st),
                                    color = when {
                                        mins < 5 -> T.accAlert
                                        mins < 15 -> T.accHot
                                        else -> T.fgMuted
                                    },
                                    fontSize = (T.labelSize.value * s).sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startOf(e: HaClient.CalendarEvent): ZonedDateTime? =
    runCatching { ZonedDateTime.parse(e.start) }.getOrNull()

/** 「あと42分」「あと1時間5分」「あと3日」。過ぎたら出さない前提 */
private fun countdownLabel(now: ZonedDateTime, start: ZonedDateTime): String {
    val d = Duration.between(now, start)
    val mins = d.toMinutes()
    return when {
        mins < 1 -> "まもなく"
        mins < 60 -> "あと${mins}分"
        mins < 24 * 60 -> {
            val h = mins / 60
            val m = mins % 60
            if (m == 0L) "あと${h}時間" else "あと${h}時間${m}分"
        }
        else -> "あと${d.toDays()}日"
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
