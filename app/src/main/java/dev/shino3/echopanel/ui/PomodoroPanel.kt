package dev.shino3.echopanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.shino3.echopanel.pomodoro.Phase
import dev.shino3.echopanel.pomodoro.Pomodoro
import dev.shino3.echopanel.pomodoro.PomodoroState
import kotlinx.coroutines.delay

@Composable
fun PomodoroPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(Pomodoro.load(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 表示は endAt からの逆算なので、ここは単に描き直しの契機でしかない。
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            state = Pomodoro.load(ctx)
            delay(500L)
        }
    }

    val remaining = state.remainingMs(now)
    val total = state.totalMs().coerceAtLeast(1L)
    val progress = 1f - (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(T.gapS),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhaseTab("作業 ${Pomodoro.workMs / 60_000}", state.phase == Phase.WORK) {
                Pomodoro.switchPhase(ctx, Phase.WORK); state = Pomodoro.load(ctx)
            }
            Spacer(Modifier.width(T.gapS))
            PhaseTab("休憩 ${Pomodoro.breakMs / 60_000}", state.phase == Phase.BREAK) {
                Pomodoro.switchPhase(ctx, Phase.BREAK); state = Pomodoro.load(ctx)
            }
            Spacer(Modifier.width(T.gapM))
            Text(
                text = "完了 ${state.completedWorkSessions}",
                color = T.fgFaint,
                fontSize = T.labelSize
            )
        }

        Spacer(Modifier.height(T.gapM))

        Text(
            text = formatRemaining(remaining),
            color = T.fg,
            fontSize = T.timerSize,
            fontWeight = T.displayWeight
        )

        Spacer(Modifier.height(T.gapS))
        ProgressBar(progress)
        Spacer(Modifier.height(T.gapL))

        Row(horizontalArrangement = Arrangement.spacedBy(T.gapS)) {
            ActionButton(if (state.running) "一時停止" else "開始", primary = true) {
                if (state.running) Pomodoro.pause(ctx) else Pomodoro.start(ctx)
                state = Pomodoro.load(ctx)
            }
            ActionButton("リセット") {
                Pomodoro.reset(ctx); state = Pomodoro.load(ctx)
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(T.line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(T.fg)
        )
    }
}

@Composable
private fun PhaseTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) T.fg else T.fgFaint,
        fontSize = T.labelSize,
        modifier = Modifier
            .clip(RoundedCornerShape(T.radius))
            .background(if (selected) T.surfaceHi else T.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = T.gapM, vertical = T.gapXs)
    )
}

@Composable
private fun ActionButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (primary) T.bg else T.fg,
        fontSize = T.bodySize,
        modifier = Modifier
            .clip(RoundedCornerShape(T.radius))
            .background(if (primary) T.fg else T.surface)
            .border(1.dp, if (primary) T.fg else T.line, RoundedCornerShape(T.radius))
            .clickable(onClick = onClick)
            .padding(horizontal = T.gapL, vertical = T.gapS)
    )
}
