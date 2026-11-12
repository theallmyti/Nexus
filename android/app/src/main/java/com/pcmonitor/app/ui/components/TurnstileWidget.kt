package com.pcmonitor.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// Cloudflare Turnstile bot check for sign-up. The widget page is served by the Convex deployment
// (convex/convex/http.ts /turnstile), whose hostname is registered on the widget; the token comes
// back through the `NexusTurnstile` JavaScript bridge and is verified server-side in auth:signUp.
private const val TURNSTILE_PAGE = "https://tidy-newt-65.convex.site/turnstile"
private val ALLOWED_HOSTS = setOf("tidy-newt-65.convex.site", "challenges.cloudflare.com")

/**
 * @param resetSignal bump after every sign-up attempt: a token can be redeemed only once.
 * @param onToken a fresh token, or null when the last one expired or the check failed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileWidget(
    resetSignal: Int,
    onToken: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnToken by rememberUpdatedState(onToken)
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Only the widget page and Cloudflare's challenge may load; any other top-level
                // navigation is refused, so the bridge is never exposed to another site.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !isAllowed(request.url)
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onToken(token: String) {
                        val t = token.takeIf { it.isNotBlank() && it.length <= 2048 }
                        post { currentOnToken(t) }
                    }

                    @JavascriptInterface
                    fun onExpired(@Suppress("UNUSED_PARAMETER") ignored: String) {
                        post { currentOnToken(null) }
                    }

                    @JavascriptInterface
                    fun onError(@Suppress("UNUSED_PARAMETER") code: String) {
                        post { currentOnToken(null) }
                    }
                }, "NexusTurnstile")
                loadUrl(TURNSTILE_PAGE)
                webView = this
            }
        },
        onRelease = { it.destroy() }
    )

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            currentOnToken(null)
            webView?.evaluateJavascript("window.nexusReset && window.nexusReset()", null)
        }
    }
}

private fun isAllowed(uri: Uri): Boolean = uri.scheme == "https" && uri.host in ALLOWED_HOSTS
