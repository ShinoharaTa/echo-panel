package dev.shino3.echopanel.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.shino3.echopanel.ui.tiles.TileFrame
import dev.shino3.echopanel.ui.tiles.TileNotice

/**
 * Home Assistant のダッシュボードをそのまま埋め込む。
 * SwitchBot の操作も温湿度も HA 側が持っているので、ここは表示に徹する。
 * 端末側に認証情報を持たない方針(SELinux Permissive のため)とも整合する。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPanel(url: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) {
        TileFrame { TileNotice("haUrl 未設定") }
        return
    }

    // url をキーにして composition ごと作り直す。設定画面で URL を変えたとき、
    // 古い WebView が残って前の画面を映し続けるのを防ぐ。
    key(url) {
        AndroidView(
            modifier = modifier.fillMaxSize().background(T.bg),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(url)
                }
            },
            // Pager は離れたページを捨てるので、ページを送るたびに WebView が
            // 生成され続ける。破棄しないと JS のタイマーも WebView 自身も
            // residual に残り、24時間動かす端末ではじわじわ効いてくる。
            onRelease = { web ->
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            }
        )
    }
}
