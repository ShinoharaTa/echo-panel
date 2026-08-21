package dev.shino3.echopanel

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.pomodoro.Pomodoro
import dev.shino3.echopanel.ui.BackdropRenderer
import dev.shino3.echopanel.ui.BackdropState
import dev.shino3.echopanel.ui.LocalBackdrop
import dev.shino3.echopanel.ui.LocalPageNav
import dev.shino3.echopanel.ui.PrecipLayer
import dev.shino3.echopanel.ui.RenderNode
import dev.shino3.echopanel.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

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

@Composable
fun PanelRoot() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(PanelConfig.load(ctx)) }

    // panels.json の更新時刻を見て自動で読み直す。
    // Mac から adb push しただけで画面が変わるので、レイアウトの試行錯誤が速い。
    LaunchedEffect(Unit) {
        var stamp = PanelConfig.file(ctx).lastModified()
        while (true) {
            delay(3_000L)
            val now = PanelConfig.file(ctx).lastModified()
            if (now != stamp) {
                stamp = now
                cfg = PanelConfig.load(ctx)
            }
        }
    }

    // 作業/休憩の長さも panels.json から与える
    LaunchedEffect(cfg.workMinutes, cfg.breakMinutes) {
        Pomodoro.configure(cfg.workMinutes, cfg.breakMinutes)
    }

    val pageCount = cfg.pages.size.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    val scope = rememberCoroutineScope()

    // ---- 背景: 天気 condition の取得(mock はそのまま、実データは10分毎) ----
    var condition by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cfg.mock, cfg.mockCondition, cfg.weatherEntity, cfg.haUrl, cfg.haToken) {
        if (cfg.mock) { condition = cfg.mockCondition; return@LaunchedEffect }
        if (!cfg.haConfigured || cfg.weatherEntity.isBlank()) { condition = null; return@LaunchedEffect }
        val client = HaClient(cfg.haUrl, cfg.haToken)
        while (true) {
            condition = client.state(cfg.weatherEntity)?.state
            delay(10 * 60_000L)
        }
    }

    // ---- 背景: 静的レイヤーの生成(1分毎。太陽/月が動く) ----
    val backdrop = remember { BackdropState() }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(sizePx, condition) {
        if (sizePx.width <= 0 || sizePx.height <= 0) return@LaunchedEffect
        while (true) {
            val (sharp, blurred) = withContext(Dispatchers.Default) {
                BackdropRenderer.render(sizePx.width, sizePx.height, condition, ZonedDateTime.now())
            }
            backdrop.sharp = sharp.asImageBitmap()
            backdrop.blurred = blurred.asImageBitmap()
            delay(60_000L)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(T.bg)
            .onSizeChanged { sizePx = it }
    ) {
        backdrop.sharp?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        PrecipLayer(condition)

        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalPageNav provides { name ->
                val idx = cfg.pages.indexOfFirst { it.name == name }
                if (idx >= 0) scope.launch { pager.animateScrollToPage(idx) }
            }
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { it }
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        // 下端はページドットの帯として予約(グラフや文字と衝突させない)
                        .padding(start = T.gapTile, end = T.gapTile, top = T.gapTile, bottom = 13.dp)
                ) {
                    cfg.pages.getOrNull(page)?.let { RenderNode(it.root, cfg) }
                }
            }
        }

        // 画面の左右端はページ送り専用レーンにする。
        // ha.web の WebView が横スワイプを全て食ってしまい、ページから
        // 出られなくなるため (実機で発生)。端 24dp だけはこちらが先に取る。
        EdgeSwipeLane(Modifier.align(Alignment.CenterStart)) { d ->
            scope.launch {
                val to = if (d < 0) pager.currentPage + 1 else pager.currentPage - 1
                pager.animateScrollToPage(to.coerceIn(0, pageCount - 1))
            }
        }
        EdgeSwipeLane(Modifier.align(Alignment.CenterEnd)) { d ->
            scope.launch {
                val to = if (d < 0) pager.currentPage + 1 else pager.currentPage - 1
                pager.animateScrollToPage(to.coerceIn(0, pageCount - 1))
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { i ->
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

/** 左右端に置く透明なスワイプ受け。累積 60dp 超の横ドラッグでページを送る。 */
@Composable
private fun EdgeSwipeLane(modifier: Modifier, onSwipe: (Float) -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = { if (kotlin.math.abs(acc) > 60f) onSwipe(acc) }
                ) { _, dragAmount -> acc += dragAmount }
            }
    )
}
