package dev.shino3.echopanel.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Home Assistant のダッシュボードをそのまま埋め込む。
 * SwitchBot の操作も温湿度も HA 側が持っているので、ここは表示に徹する。
 * 端末側に認証情報を持たない方針(SELinux Permissive のため)とも整合する。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPanel(url: String, modifier: Modifier = Modifier) {
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
        }
    )
}
