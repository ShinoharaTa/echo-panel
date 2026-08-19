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

    /**
     * 「どの endAt を消化済みか」。
     * 鳴動を進めるのは AlarmReceiver と load() の取りこぼし修復の2経路あり、
     * 発火の瞬間に両方が走ると休憩を飛ばしてしまう。
     * 同じ endAt に対しては一度しか進めないことでこれを防ぐ。
     */
    private const val K_CONSUMED = "consumed_end_at"

    const val ACTION_FIRE = "dev.shino3.echopanel.POMODORO_FIRE"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_END_AT = "end_at"

    var workMs: Long = 25 * 60 * 1000L
        private set
    var breakMs: Long = 5 * 60 * 1000L
        private set

    fun configure(workMinutes: Int, breakMinutes: Int) {
        workMs = workMinutes.coerceIn(1, 240) * 60_000L
        breakMs = breakMinutes.coerceIn(1, 240) * 60_000L
    }

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
            advanceOnce(c, endAt)
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

    /**
     * 鳴動に対応する 1 回だけフェーズを進める。
     *
     * AlarmReceiver と load() の修復処理の両方から呼ばれるが、
     * 同じ endAt について二度は進まない。これが無いと発火の瞬間に
     * 両経路が走って休憩を丸ごと飛ばす (実機で発生した)。
     */
    fun advanceOnce(c: Context, endAt: Long) {
        val p = prefs(c)
        if (endAt != 0L && p.getLong(K_CONSUMED, -1L) == endAt) return

        val phase = if (p.getString(K_PHASE, "WORK") == "BREAK") Phase.BREAK else Phase.WORK
        val done = p.getInt(K_DONE, 0)
        val next = if (phase == Phase.WORK) Phase.BREAK else Phase.WORK
        val newDone = if (phase == Phase.WORK) done + 1 else done

        p.edit().putLong(K_CONSUMED, endAt).apply()
        save(c, PomodoroState(next, false, 0L, durationOf(next), newDone))
    }

    private fun pendingIntent(c: Context, phase: Phase, endAt: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_PHASE, phase.name)
            putExtra(EXTRA_END_AT, endAt)
        }
        return PendingIntent.getBroadcast(
            c, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(c: Context, endAt: Long, phase: Phase) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(c, phase, endAt)
        // Doze 下でも確実に発火させる。端末は常時給電なので電池影響は考えなくてよい。
        val triggerElapsed = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis())
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
    }

    private fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // PendingIntent の同一性は extras を見ないので、これで予約は消える
        am.cancel(pendingIntent(c, Phase.WORK, 0L))
    }
}
