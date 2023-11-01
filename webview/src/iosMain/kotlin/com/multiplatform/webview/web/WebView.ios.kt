package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.setValue
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled

/**
 * iOS WebView implementation.
 */
@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    IOSWebView(
        state = state,
        modifier = modifier,
        captureBackPresses = captureBackPresses,
        navigator = navigator,
        onCreated = onCreated,
        onDispose = onDispose,
    )
}

/**
 * iOS WebView implementation.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun IOSWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    val observer =
        remember {
            WKWebViewObserver(
                state = state,
                navigator = navigator,
            )
        }
    val navigationDelegate = remember { WKNavigationDelegate(state, navigator) }
    val scope = rememberCoroutineScope()

    UIKitView(
        factory = {
            val config =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    defaultWebpagePreferences.allowsContentJavaScript = state.webSettings.isJavaScriptEnabled
                    preferences.apply {
                        setValue(state.webSettings.allowFileAccessFromFileURLs, forKey = "allowFileAccessFromFileURLs")
                        javaScriptEnabled = state.webSettings.isJavaScriptEnabled
                    }
                    setValue(state.webSettings.allowUniversalAccessFromFileURLs, forKey = "allowUniversalAccessFromFileURLs")
                }
            WKWebView(
                frame = CGRectZero.readValue(),
                configuration = config,
            ).apply {
                userInteractionEnabled = captureBackPresses
                allowsBackForwardNavigationGestures = captureBackPresses
                customUserAgent = state.webSettings.customUserAgentString
                this.addObservers(
                    observer = observer,
                    properties =
                        listOf(
                            "estimatedProgress",
                            "title",
                            "URL",
                            "canGoBack",
                            "canGoForward",
                        ),
                )
                this.navigationDelegate = navigationDelegate
                onCreated()
            }.also {
                val iosWebView = IOSWebView(it, scope, state.jsBridge)
                state.webView = iosWebView
            }
        },
        modifier = modifier,
        onRelease = {
            state.webView = null
            it.removeObservers(
                observer = observer,
                properties =
                    listOf(
                        "estimatedProgress",
                        "title",
                        "URL",
                        "canGoBack",
                        "canGoForward",
                    ),
            )
            it.navigationDelegate = null
            onDispose()
        },
    )
}
