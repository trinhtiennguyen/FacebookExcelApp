package com.example.facebookexcel.facebook

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Loads Facebook Reel/share/watch links.
 *
 * Strategy:
 * 1) Resolve public metadata directly with OkHttp.
 * 2) If Facebook requires the logged-in session, resolve the page through WebView.
 *    WebView uses the same CookieManager as the optional Facebook login screen.
 * 3) If an access token is supplied, use Graph API as a final fallback.
 */
data class FacebookResult(
    val title: String,
    val time: String,
    val image: String,
    val resolvedUrl: String = ""
)

class FacebookApiClient {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(context: Context, url: String, accessToken: String): Result<FacebookResult> {
        val cleanUrl = url.trim()
        if (!isFacebookUrl(cleanUrl)) {
            return Result.failure(IllegalArgumentException("Link không phải Facebook"))
        }

        // First try without requiring a Facebook token.
        val direct = withContext(Dispatchers.IO) { loadDirect(cleanUrl) }
        if (direct.isSuccess && direct.getOrNull()?.hasUsefulData() == true) {
            return direct
        }

        // Try the logged-in WebView session. This is important for share/v links
        // whose target is visible to the user's Facebook session.
        val web = try {
            loadWithWebView(context, cleanUrl)
        } catch (e: Exception) {
            Result.failure<FacebookResult>(e)
        }
        if (web.isSuccess && web.getOrNull()?.hasUsefulData() == true) {
            return web
        }

        // Optional Graph API fallback.
        if (accessToken.isNotBlank()) {
            val graph = withContext(Dispatchers.IO) { loadGraph(cleanUrl, accessToken.trim()) }
            if (graph.isSuccess && graph.getOrNull()?.hasUsefulData() == true) {
                return graph
            }
            val graphError = graph.exceptionOrNull()?.message.orEmpty()
            if (graphError.isNotBlank()) {
                return Result.failure(IllegalStateException(graphError))
            }
        }

        val webError = web.exceptionOrNull()?.message.orEmpty()
        val directError = direct.exceptionOrNull()?.message.orEmpty()
        val message = when {
            webError.contains("đăng nhập", true) -> webError
            webError.isNotBlank() -> webError
            directError.isNotBlank() -> directError
            else -> "Không lấy được dữ liệu Reel. Nếu Reel yêu cầu đăng nhập, hãy bấm Cài đặt → Đăng nhập Facebook trong app rồi LOAD lại."
        }
        return Result.failure(IllegalStateException(message))
    }

    private fun isFacebookUrl(url: String): Boolean =
        url.contains("facebook.com", true) || url.contains("fb.watch", true)

    private fun FacebookResult.hasUsefulData(): Boolean =
        title.isNotBlank() || time.isNotBlank() || image.isNotBlank() || resolvedUrl.isNotBlank()

    private fun loadDirect(url: String): Result<FacebookResult> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_UA)
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("Facebook HTTP ${response.code}"))
                }
                Result.success(parseHtml(body, response.request.url.toString()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadGraph(url: String, accessToken: String): Result<FacebookResult> {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val fields = "message,story,created_time,full_picture,permalink_url,name,description"
            val endpoint =
                "https://graph.facebook.com/v23.0/?id=$encodedUrl&fields=$fields&access_token=${URLEncoder.encode(accessToken, "UTF-8")}"

            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", MOBILE_UA)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("Facebook Graph API HTTP ${response.code}: $body"))
                }
                val root = JSONObject(body)
                val data = if (root.has("data")) root.optJSONObject("data") ?: root else root
                Result.success(
                    FacebookResult(
                        title = firstNonBlank(
                            data.optString("message"),
                            data.optString("story"),
                            data.optString("name"),
                            data.optString("description")
                        ),
                        time = data.optString("created_time"),
                        image = data.optString("full_picture"),
                        resolvedUrl = data.optString("permalink_url")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadWithWebView(context: Context, url: String): Result<FacebookResult> =
        suspendCancellableCoroutine { continuation ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val webView = WebView(context.applicationContext)
                var finished = false

                fun finish(result: Result<FacebookResult>) {
                    if (finished) return
                    finished = true
                    webView.stopLoading()
                    webView.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = MOBILE_UA
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        if (finished) return
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (finished || view == null) return@postDelayed
                            val script = """
                                (function() {
                                  function meta(name) {
                                    var a = document.querySelector('meta[property="' + name + '"]');
                                    if (!a) a = document.querySelector('meta[name="' + name + '"]');
                                    return a ? (a.content || '') : '';
                                  }
                                  function text(sel) {
                                    var a = document.querySelector(sel);
                                    return a ? (a.innerText || a.textContent || '') : '';
                                  }
                                  var title = meta('og:title') || meta('twitter:title') || document.title || '';
                                  var image = meta('og:image') || meta('twitter:image') || '';
                                  var time = meta('article:published_time') || meta('video:release_date') || meta('og:updated_time') || '';
                                  if (!time) {
                                    var t = document.querySelector('time[datetime]');
                                    if (t) time = t.getAttribute('datetime') || '';
                                  }
                                  var canonical = document.querySelector('link[rel="canonical"]');
                                  var canonicalUrl = canonical ? canonical.href : (location.href || '');
                                  var bodyText = (document.body ? document.body.innerText : '').slice(0, 4000);
                                  return JSON.stringify({title:title, image:image, time:time, url:canonicalUrl, body:bodyText});
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(script) { raw ->
                                try {
                                    val jsonText = JSONObject.quote(raw).let { quoted ->
                                        // evaluateJavascript returns a JSON string literal. Decode it.
                                        org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
                                    }
                                    val obj = JSONObject(jsonText)
                                    val body = obj.optString("body")
                                    if (isLoginPage(body, loadedUrl.orEmpty())) {
                                        finish(Result.failure(IllegalStateException("Facebook yêu cầu đăng nhập trong app. Vào Cài đặt → Đăng nhập Facebook rồi thử LOAD lại.")))
                                    } else {
                                        finish(Result.success(
                                            FacebookResult(
                                                title = obj.optString("title").trim(),
                                                time = obj.optString("time").trim(),
                                                image = obj.optString("image").trim(),
                                                resolvedUrl = obj.optString("url").trim().ifBlank { loadedUrl.orEmpty() }
                                            )
                                        ))
                                    }
                                } catch (e: Exception) {
                                    finish(Result.failure(e))
                                }
                            }
                        }, 1200L)
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            finish(Result.failure(IllegalStateException("Không mở được trang Facebook: ${error?.description ?: "unknown error"}")))
                        }
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                webView.loadUrl(url)

                continuation.invokeOnCancellation {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (!finished) finish(Result.failure(IllegalStateException("Đã hủy LOAD")))
                    }
                }
            }
        }

    private fun isLoginPage(body: String, url: String): Boolean {
        val u = url.lowercase()
        val b = body.lowercase()
        return u.contains("/login") ||
            b.contains("log in to facebook") ||
            b.contains("đăng nhập facebook") ||
            b.contains("create new account")
    }

    private fun parseHtml(html: String, resolvedUrl: String): FacebookResult {
        fun meta(property: String): String {
            val p = Regex("<meta[^>]+(?:property|name)=[\\\"']${Regex.escape(property)}[\\\"'][^>]+content=[\\\"']([^\\\"']*)[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
            val p2 = Regex("<meta[^>]+content=[\\\"']([^\\\"']*)[\\\"'][^>]+(?:property|name)=[\\\"']${Regex.escape(property)}[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
            return p.find(html)?.groupValues?.getOrNull(1).orEmpty().ifBlank { p2.find(html)?.groupValues?.getOrNull(1).orEmpty() }
        }

        val title = firstNonBlank(
            meta("og:title"),
            meta("twitter:title"),
            Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtml).orEmpty()
        )
        val image = firstNonBlank(meta("og:image"), meta("twitter:image"))
        val time = firstNonBlank(meta("article:published_time"), meta("video:release_date"), meta("og:updated_time"))
        return FacebookResult(decodeHtml(title), time, decodeHtml(image), resolvedUrl)
    }

    private fun decodeHtml(value: String): String =
        value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() } ?: ""

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    }
}
