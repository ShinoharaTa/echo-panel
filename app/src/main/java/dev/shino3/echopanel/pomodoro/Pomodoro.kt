package dev.shino3.echopanel.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * ポモドーロの状態。
 *
 * 重要な設計判断: 残り時間をカウントダウンして持たない。
 * 「終了時刻(endAt)」だけを保存し、残りは常に endAt - now で計算する。
 * こうしておくと、スクリーンセーバーに覆われても、アプリが殺されても、
 * 端末がスリープしても、復帰した瞬間に正しい残り時間が出る。
 */
enum class Phase { WORK, BREAK }

data class PomodoroState(
    val phase: Phase,
    val running: Boolean,
    /** 動作中のみ有効。RTC(壁時計)ミリ秒。 */
    val endAt: Long,
    /** 一時停止中のみ有効。残りミリ秒。 */
    val pausedLeft: Long,
    val completedWorkSessions: Int,
) {
    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        if (running) (endAt - now).coerceAtLeast(0L) else pausedLeft

    fun totalMs(): Long = Pomodoro.durationOf(phase)
}

object Pomodoro {
    private const val PREFS = "pomodoro"
    private const val K_PHASE = "phase"
    private const val K_RUNNING = "running"
    private const val K_END_AT = "end_at"
    private const val K_PAUSED_LEFT = "paused_left"
    private const val K_DONE = "done_sessions"

    const val ACTION_FIRE = "dev.shino3.echopanel.POMODORO_FIRE"
    const val EXTRA_PHASE = "phase"

    var workMs: Long = 25 * 60 * 1000L
        private set
    var breakMs: Long = 5 * 60 * 1000L
        private set

    fun durationOf(phase: Phase): Long = if (phase == Phase.WORK) workMs else breakMs

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): PomodoroState {
        val p = prefs(c)
        val phase = if (p.getString(K_PHASE, "WORK") == "BREAK") Phase.BREAK else Phase.WORK
        val running = p.getBoolean(K_RUNNING, false)
        val endAt = p.getLong(K_END_AT, 0L)
        val paused = p.getLong(K_PAUSED_LEFT, durationOf(phase))
        val done = p.getInt(K_DONE, 0)

        // 動作中のまま終了時刻を過ぎている = 鳴動を取りこぼした場合。
        // 次フェーズの待機状態に落として整合を取る。
        if (running && endAt <= System.currentTimeMillis()) {
            val next = if (phase == Phase.WORK) Phase.BREAK else Phase.WORK
            val newDone = if (phase == Phase.WORK) done + 1 else done
            save(c, PomodoroState(next, false, 0L, durationOf(next), newDone))
            return load(c)
        }
        return PomodoroState(phase, running, endAt, paused, done)
    }

    private fun save(c: Context, s: PomodoroState) {
        prefs(c).edit()
            .putString(K_PHASE, s.phase.name)
            .putBoolean(K_RUNNING, s.running)
            .putLong(K_END_AT, s.endAt)
            .putLong(K_PAUSED_LEFT, s.pausedLeft)
            .putInt(K_DONE, s.completedWorkSessions)
            .apply()
    }

    fun start(c: Context) {
        val s = load(c)
        if (s.running) return
        val left = if (s.pausedLeft > 0) s.pausedLeft else durationOf(s.phase)
        val endAt = System.currentTimeMillis() + left
        save(c, s.copy(running = true, endAt = endAt, pausedLeft = 0L))
        schedule(c, endAt, s.phase)
    }

    fun pause(c: Context) {
        val s = load(c)
        if (!s.running) return
        val left = s.remainingMs()
        cancel(c)
        save(c, s.copy(running = false, endAt = 0L, pausedLeft = left))
    }

    fun reset(c: Context) {
        val s = load(c)
        cancel(c)
        save(c, s.copy(running = false, endAt = 0L, pausedLeft = durationOf(s.phase)))
    }

    fun switchPhase(c: Context, phase: Phase) {
        val s = load(c)
        cancel(c)
        save(c, s.copy(phase = phase, running = false, endAt = 0L, pausedLeft = durationOf(phase)))
    }

    /** 鳴動後に次フェーズへ送る。AlarmReceiver から呼ばれる。 */
    fun advance(c: Context) {
        val s = load(c)
        val next = if (s.phase == Phase.WORK) Phase.BREAK else Phase.WORK
        val done = if (s.phase == Phase.WORK) s.completedWorkSessions + 1 else s.completedWorkSessions
        save(c, PomodoroState(next, false, 0L, durationOf(next), done))
    }

    private fun pendingIntent(c: Context, phase: Phase): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_PHASE, phase.name)
        }
        return PendingIntent.getBroadcast(
            c, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(c: Context, endAt: Long, phase: Phase) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(c, phase)
        // Doze 下でも確実に発火させる。端末は常時給電なので電池影響は考えなくてよい。
        val triggerElapsed = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis())
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
    }

    private fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(c, Phase.WORK))
        am.cancel(pendingIntent(c, Phase.BREAK))
    }
}
