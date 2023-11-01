package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.JsBridge
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.util.KLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.setValue
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

/**
 * Created By Kevin Zou On 2023/9/5
 */

/**
 * iOS implementation of [IWebView]
 */
class IOSWebView(
    private val wkWebView: WKWebView,
    override var scope: CoroutineScope,
    override var jsBridge: JsBridge,
) : IWebView {
    init {
        init()
    }

    override fun canGoBack() = wkWebView.canGoBack

    override fun canGoForward() = wkWebView.canGoForward

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        KLogger.d { "Load url: $url" }
        val request =
            NSMutableURLRequest.requestWithURL(
                URL = NSURL(string = url),
            )
        additionalHttpHeaders.all { (key, value) ->
            request.setValue(
                value = value,
                forHTTPHeaderField = key,
            )
            true
        }
        wkWebView.loadRequest(
            request = request,
        )
    }

    override fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        if (html == null) {
            KLogger.e {
                "LoadHtml: html is null"
            }
            return
        }
        wkWebView.loadHTMLString(
            string = html,
            baseURL = baseUrl?.let { NSURL.URLWithString(it) },
        )
    }

    override suspend fun loadHtmlFile(fileName: String) {
        val res = NSBundle.mainBundle.resourcePath + "/compose-resources/assets/" + fileName
        val url = NSURL.fileURLWithPath(res)
        wkWebView.loadFileURL(url, url)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun postUrl(
        url: String,
        postData: ByteArray,
    ) {
        val request =
            NSMutableURLRequest(
                uRL = NSURL(string = url),
            )
        request.apply {
            HTTPMethod = "POST"
            HTTPBody =
                memScoped {
                    NSData.create(bytes = allocArrayOf(postData), length = postData.size.toULong())
                }
        }
        wkWebView.loadRequest(request = request)
    }

    override fun goBack() {
        wkWebView.goBack()
    }

    override fun goForward() {
        wkWebView.goForward()
    }

    override fun reload() {
        wkWebView.reload()
    }

    override fun stopLoading() {
        wkWebView.stopLoading()
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        wkWebView.evaluateJavaScript(script) { result, error ->
            if (error != null) {
                KLogger.e { "evaluateJavaScript error: $error" }
                callback?.invoke(error.localizedDescription())
            } else {
                KLogger.i { "evaluateJavaScript result: $result" }
                callback?.invoke(result?.toString() ?: "")
            }
        }
    }

    override fun injectBridge(jsBridge: JsBridge) {
        val userController =
            WKUserContentController().apply {
                addScriptMessageHandler(
                    object : WKScriptMessageHandlerProtocol, NSObject() {
                        override fun userContentController(
                            userContentController: WKUserContentController,
                            didReceiveScriptMessage: WKScriptMessage,
                        ) {
                            KLogger.d { "didReceiveScriptMessage: $didReceiveScriptMessage" }
                            val body = didReceiveScriptMessage.body
                            val method = didReceiveScriptMessage.name
                            if (body is String) {
                                val message = Json.decodeFromString<JsMessage>(body)
                                jsBridge.dispatch(message)
                            }
                        }
                    },
                    "jsBridge",
                )
            }
        wkWebView.configuration.userContentController = userController
    }

    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }
}
