package dev.shino3.echopanel.dream

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.shino3.echopanel.pomodoro.Phase
import dev.shino3.echopanel.pomodoro.Pomodoro
import kotlin.random.Random

/**
 * 自前のスクリーンセーバー。
 *
 * これを持つことで OS 標準の時計スクリーンセーバーを置き換えられる。
 * 加えて、ポモドーロ動作中は残り時間もここに出す。
 * 「タイマー中にスクリーンセーバーが覆いかぶさって残りが見えない」という
 * Web 実装では避けられない問題が、これで消える。
 */
class PanelDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var stack: LinearLayout
    private lateinit var timerView: TextView

    private val tick = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val jitter = object : Runnable {
        override fun run() {
            stack.translationX = Random.nextInt(-40, 41).toFloat()
            stack.translationY = Random.nextInt(-28, 29).toFloat()
            handler.postDelayed(this, 5 * 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // タップで抜ける
        isFullscreen = true
        isScreenBright = true   // 画面は点けたまま。卓上時計として使うため。

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val time = TextClock(this).apply {
            format12Hour = null
            format24Hour = "H:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 104f)
            // アプリ内の大時計 (FontWeight.Light) と同じ細さに揃える
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = null
            format24Hour = "M月d日(E)"
            setTextColor(Color.parseColor("#A9A9A9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        }
        timerView = TextView(this).apply {
            setTextColor(Color.parseColor("#A9A9A9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }

        stack.addView(time)
        stack.addView(date)
        stack.addView(timerView)
        root.addView(stack)
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
        handler.post(jitter)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(jitter)
        super.onDreamingStopped()
    }

    private fun updateTimer() {
        val s = Pomodoro.load(this)
        if (!s.running) {
            timerView.visibility = android.view.View.GONE
            return
        }
        val left = (s.remainingMs() + 999) / 1000
        val label = if (s.phase == Phase.WORK) "作業" else "休憩"
        timerView.text = "%s %d:%02d".format(label, left / 60, left % 60)
        timerView.visibility = android.view.View.VISIBLE
    }
}
