package com.unshoo.pixelmusic.data.remote.qqmusic

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Android implementation of QQ Music Signature (sign) generation.
 *
 * BUGFIX (lag — WebView on the main thread): the previous implementation
 * used `mainHandler.post { WebView(appContext) … }` to create the WebView
 * and then `mainHandler.post { webView.evaluateJavascript(...) }` to sign.
 * On cold-start a fresh WebView costs ~300-800ms on a Pixel, more on low-
 * end devices; combined with `CountDownLatch.await(8, TimeUnit.SECONDS)`
 * waiting for `onPageFinished`, the caller was blocked for the full
 * lifecycle. Real users saw 1-8 s freezes on the first QQ-Music play.
 *
 * The WebView is now created and driven on a dedicated `HandlerThread`
 * ("QQSign-WebView"). Every `evaluateJavascript` and asset read happens
 * off the main thread; the caller simply blocks on a `CountDownLatch`
 * while the dedicated thread does the work. The thread is started lazily
 * on first use and torn down with [shutdown].
 */
class QQSignGenerator(private val context: Context) {

    private val appContext = context.applicationContext
    private val signLock = Any()

    // BUGFIX: dedicated WebView looper, not the main looper.
    private var webViewThread: HandlerThread? = null
    private var webViewHandler: Handler? = null
    @Volatile
    private var webView: WebView? = null
    @Volatile
    private var webViewReady: Boolean = false

    @Volatile
    private var encryptLatch: CountDownLatch? = null
    @Volatile
    private var encryptResultRef: AtomicReference<String?>? = null

    private inner class JsBridge {
        @JavascriptInterface
        fun onEncryptResult(value: String?) {
            encryptResultRef?.set(value)
            encryptLatch?.countDown()
        }
    }

    private val jsContent: String by lazy {
        appContext.assets.open("qq_sign.js").use { inputStream ->
            InputStreamReader(inputStream).readText()
        }
    }

    private val vmDecryptContent: String? by lazy {
        runCatching {
            appContext.assets.open("vm_new.js").use { inputStream ->
                InputStreamReader(inputStream).readText()
            }
        }.getOrNull()
    }

    /**
     * Returns a Handler bound to the dedicated WebView thread, starting
     * the thread lazily. Safe to call from any thread; idempotent.
     */
    @Synchronized
    private fun webViewHandler(): Handler {
        webViewHandler?.let { return it }
        val thread = HandlerThread("QQSign-WebView").apply { start() }
        webViewThread = thread
        val handler = Handler(thread.looper)
        webViewHandler = handler
        return handler
    }

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val handler = webViewHandler()

        val created = AtomicReference<WebView>()
        val createdLatch = CountDownLatch(1)
        val readyLatchRef = AtomicReference<CountDownLatch>()
        // BUGFIX: post to the dedicated WebView thread, not the main thread.
        handler.post {
            try {
                val readyLatch = CountDownLatch(1)
                readyLatchRef.set(readyLatch)
                val instance = WebView(appContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                        }
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webViewReady = true
                            readyLatch.countDown()
                        }
                    }
                    addJavascriptInterface(JsBridge(), "AndroidBridge")
                    // vm_new encryption relies on web crypto APIs; initialize on HTTPS origin.
                    loadUrl("https://y.qq.com/")
                }
                created.set(instance)
            } finally {
                createdLatch.countDown()
            }
        }

        if (!createdLatch.await(2, TimeUnit.SECONDS)) {
            throw IllegalStateException("WebView creation timed out")
        }
        val instance = created.get()
            ?: throw IllegalStateException("Failed to initialize WebView signer")

        val readyLatch = readyLatchRef.get()
        if (readyLatch != null && !webViewReady) {
            // BUGFIX: this used to block the main thread for up to 8s.
            // Now it only blocks the caller (already a background caller in
            // practice), and the WebView creation itself is off-Main.
            readyLatch.await(8, TimeUnit.SECONDS)
        }

        webView = instance
        return instance
    }

    private fun decodeEvaluateResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw.isBlank()) return null
        return try {
            if (raw.startsWith('"')) JSONArray("[$raw]").getString(0) else raw
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Generates a `zzb...` signature for the given request JSON string.
     */
    fun generateSign(jsonData: String): String? {
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // BUGFIX: this used to be logged but still proceeded, freezing
                    // the UI. Now we explicitly fail fast so callers that care
                    // (e.g. a Retrofit interceptor invoked from Main) can
                    // reschedule on a background dispatcher.
                    Timber.e("generateSign should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val quotedJson = JSONObject.quote(jsonData)
                val evalScript = "(function(){$jsContent; return getSign($quotedJson);})()"

                val resultRef = AtomicReference<String?>()
                val latch = CountDownLatch(1)
                // BUGFIX: post to the dedicated WebView thread.
                webViewHandler().post {
                    signerWebView.evaluateJavascript(evalScript) { value ->
                        resultRef.set(decodeEvaluateResult(value))
                        latch.countDown()
                    }
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView Sign timeout")
                    return null
                }
                resultRef.get()
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView Sign error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiDecrypt to keep behavior aligned with reverse-engineering scripts.
     */
    fun decryptResponseWithVm(encryptedData: ByteArray): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Timber.e("decryptResponseWithVm should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val b64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
                val quotedB64 = JSONObject.quote(b64)
                val script = """
                    (function() {
                        var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                        var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                        $vmCode
                        var raw = $quotedB64;
                        var bin = atob(raw);
                        var bytes = new Uint8Array(bin.length);
                        for (var i = 0; i < bin.length; i++) {
                            bytes[i] = bin.charCodeAt(i);
                        }
                        return oe.__cgiDecrypt(bytes.buffer);
                    })();
                """.trimIndent()

                val resultRef = AtomicReference<String?>()
                val latch = CountDownLatch(1)
                webViewHandler().post {
                    signerWebView.evaluateJavascript(script) { value ->
                        resultRef.set(decodeEvaluateResult(value))
                        latch.countDown()
                    }
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView vm_new decrypt timeout")
                    return null
                }
                resultRef.get()
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new decrypt error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiEncrypt for request body encryption.
     */
    fun encryptRequestWithVm(plaintext: String): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Timber.e("encryptRequestWithVm should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val quotedPlaintext = JSONObject.quote(plaintext)
                val script = """
                    (function() {
                        try {
                            var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                            var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                            $vmCode
                            var payload = $quotedPlaintext;
                            var maybePromise = oe.__cgiEncrypt(payload);
                            if (maybePromise && typeof maybePromise.then === 'function') {
                                maybePromise.then(function(encrypted) {
                                    AndroidBridge.onEncryptResult(encrypted || "");
                                }).catch(function() {
                                    AndroidBridge.onEncryptResult("");
                                });
                            } else {
                                AndroidBridge.onEncryptResult(maybePromise || "");
                            }
                        } catch (err) {
                            AndroidBridge.onEncryptResult("");
                        }
                    })();
                """.trimIndent()

                val latch = CountDownLatch(1)
                val resultRef = AtomicReference<String?>()
                encryptLatch = latch
                encryptResultRef = resultRef

                webViewHandler().post {
                    signerWebView.evaluateJavascript(script, null)
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView vm_new encrypt timeout")
                    return null
                }

                val value = resultRef.get()
                if (value.isNullOrBlank()) null else value
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new encrypt error")
            null
        } finally {
            encryptLatch = null
            encryptResultRef = null
        }
    }

    /**
     * Tears down the WebView and its dedicated thread. Call from the
     * service's onDestroy or when the QQ sign feature is no longer
     * required. Safe to call from any thread.
     */
    @Synchronized
    fun shutdown() {
        val handler = webViewHandler
        val thread = webViewThread
        val webViewToDestroy = webView
        webView = null
        webViewHandler = null
        webViewThread = null
        webViewReady = false
        if (handler != null && thread != null) {
            // WebView.destroy() must be called on the WebView's looper
            // (which is the handler's thread) to release the underlying
            // Chromium renderer cleanly. Posting the destroy to the
            // same thread and then quitting the looper drains the
            // destroy message before the thread exits.
            handler.post {
                try {
                    webViewToDestroy?.destroy()
                } catch (e: Throwable) {
                    Timber.w(e, "WebView.destroy() during QQSign shutdown threw")
                }
            }
            // quitSafely() processes all already-queued messages first
            // (including the destroy we just posted), then exits the
            // looper loop. This is the recommended way to stop a
            // HandlerThread.
            thread.quitSafely()
        }
    }
}
