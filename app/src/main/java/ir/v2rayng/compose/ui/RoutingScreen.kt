package ir.v2rayng.compose.ui

import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings

/**
 * The routing page is raw HTML from the panel and is rendered as it arrives. Nothing on this
 * screen belongs to the app: when the admin has written nothing, the page is completely empty.
 */
@Composable
fun RoutingScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    val html = state.routing?.html?.trim().orEmpty()
    if (html.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val ctx = LocalContext.current
    val bg = MaterialTheme.colorScheme.background.toArgb()
    val view = remember { routingWebView(ctx) }
    DisposableEffect(view) {
        onDispose { view.destroy() }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { view },
        update = { wv ->
            if (wv.tag as? String != html) {
                wv.setBackgroundColor(bg)
                wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                wv.tag = html
            }
        },
    )
}

private fun routingWebView(ctx: android.content.Context): WebView = WebView(ctx).apply {
    // the admin's page is text only: no scripts, no files, no storage, nothing to attack from it
    settings.javaScriptEnabled = false
    settings.domStorageEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mediaPlaybackRequiresUserGesture = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                openUrl(ctx, url)
            }
            return true
        }
    }
}
