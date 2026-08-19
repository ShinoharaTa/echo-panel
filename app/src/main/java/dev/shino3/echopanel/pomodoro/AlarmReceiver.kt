package dev.shino3.echopanel.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Pomodoro.ACTION_FIRE) return
        val phase = intent.getStringExtra(Pomodoro.EXTRA_PHASE) ?: Phase.WORK.name

        // 次フェーズへ送ってから鳴らす。
        // 先に状態を確定させておくと、鳴動中にアプリを開いても表示が矛盾しない。
        Pomodoro.advance(context)

        val svc = Intent(context, RingService::class.java).apply {
            putExtra(RingService.EXTRA_FINISHED_PHASE, phase)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
