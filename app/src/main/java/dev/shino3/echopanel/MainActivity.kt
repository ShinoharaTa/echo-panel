package dev.shino3.echopanel

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.shino3.echopanel.ui.ClockPanel
import dev.shino3.echopanel.ui.PomodoroPanel
import dev.shino3.echopanel.ui.T
import dev.shino3.echopanel.ui.WebPanel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goImmersive()
        setContent { PanelRoot() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    /**
     * ステータスバーとナビゲーションバーを隠す。
     * Android 11 で policy_control が削除されたため、アプリ側でこれをやる以外に手が無い。
     * 787 x 394dp のうち 78px 分が戻ってくる。
     */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // API 30 では WindowInsetsController が正規の手段だが、単体では
        // 実機で剥がれることがあった (Echo Show / cronos で確認)。
        // 旧 systemUiVisibility の STICKY と併用すると安定する。
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // レイアウト確定後に一度掛け直す。onCreate だけだと効かない場合がある。
        window.decorView.postDelayed({ goImmersive() }, 300L)
    }
}

private const val HA_URL = "http://10.1.111.145:8123"
private const val PAGE_COUNT = 3

@Composable
fun PanelRoot() {
    val pager = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })

    Box(Modifier.fillMaxSize().background(T.bg)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            // WebView ページは横スワイプを取り合うので、そこだけ端からのスワイプに任せる
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> ClockPanel()
                1 -> PomodoroPanel()
                else -> WebPanel(HA_URL)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(PAGE_COUNT) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pager.currentPage) 5.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) T.fg else T.fgFaint)
                )
            }
        }
    }
}
