package dev.shino3.echopanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

/**
 * 大時計。
 * 液晶とはいえ長時間の固定表示は避けたいので、5分ごとに描画位置を少し動かす。
 */
@Composable
fun ClockPanel(modifier: Modifier = Modifier, showSeconds: Boolean = false) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var jitterX by remember { mutableStateOf(0) }
    var jitterY by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(if (showSeconds) 500L else 5_000L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            jitterX = Random.nextInt(-24, 25)
            jitterY = Random.nextInt(-16, 17)
            delay(5 * 60_000L)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(T.bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x = jitterX.dp, y = jitterY.dp)
        ) {
            Text(
                text = now.format(TIME_FMT) + if (showSeconds) ":%02d".format(now.second) else "",
                color = T.fg,
                fontSize = T.displaySize,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Text(
                text = now.format(DATE_FMT),
                color = T.fgMuted,
                fontSize = T.bodySize,
                textAlign = TextAlign.Center
            )
        }
    }
}
