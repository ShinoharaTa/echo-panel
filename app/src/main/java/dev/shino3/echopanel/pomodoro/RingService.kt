package dev.shino3.echopanel.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import dev.shino3.echopanel.MainActivity
import dev.shino3.echopanel.R

/**
 * ポモドーロ終了時の鳴動。
 * フォアグラウンドサービスにしてあるので、スクリーンセーバー表示中でも
 * 別アプリが前面でも確実に鳴る。一定時間で自動停止する。
 */
class RingService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val finished = intent?.getStringExtra(EXTRA_FINISHED_PHASE) ?: Phase.WORK.name
        val title = if (finished == Phase.WORK.name) "作業終了" else "休憩終了"
        val text = if (finished == Phase.WORK.name) "5分の休憩へ" else "25分の作業へ"

        startForeground(NOTIF_ID, buildNotification(title, text))
        ring()

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, RING_DURATION_MS)
        return START_NOT_STICKY
    }

    private fun ring() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@RingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            // 音源が無い端末でも落とさない。振動だけで通知する。
        }

        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 400, 200, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vib?.vibrate(pattern, -1)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "ポモドーロ", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, RingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        player?.run { if (isPlaying) stop(); release() }
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FINISHED_PHASE = "finished_phase"
        const val ACTION_STOP = "dev.shino3.echopanel.RING_STOP"
        private const val CHANNEL = "pomodoro"
        private const val NOTIF_ID = 41
        private const val RING_DURATION_MS = 30_000L
    }
}
