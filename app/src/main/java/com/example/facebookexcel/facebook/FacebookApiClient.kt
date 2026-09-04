package com.example.facebookexcel.facebook

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class FacebookResult(
    val title: String,
    val time: String,
    val image: String
)

class FacebookApiClient {
    private val client = OkHttpClient()

    /*
     * Uses Facebook Graph API.
     * Put a valid Graph API access token in the app Settings field.
     *
     * The API can only return fields permitted by the token and by
     * Facebook's current permissions/policies.
     */
    fun load(url: String, accessToken: String): Result<FacebookResult> {
        if (accessToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Chưa nhập Facebook Graph API Access Token"))
        }

        if (!url.contains("facebook.com", ignoreCase = true) &&
            !url.contains("fb.watch", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Link không phải Facebook"))
        }

        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val fields = "message,story,created_time,full_picture,permalink_url"
            val endpoint =
                "https://graph.facebook.com/v23.0/?id=$encodedUrl&fields=$fields&access_token=${URLEncoder.encode(accessToken, "UTF-8")}"

            val request = Request.Builder().url(endpoint).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("Facebook API HTTP ${response.code}: $body")
                    )
                }

                val root = JSONObject(body)
                val data = if (root.has("data")) root.optJSONObject("data") ?: root else root

                val title = firstNonBlank(
                    data.optString("message"),
                    data.optString("story"),
                    data.optString("name"),
                    data.optString("description")
                )

                val time = data.optString("created_time")
                val image = data.optString("full_picture")

                Result.success(
                    FacebookResult(
                        title = title,
                        time = time,
                        image = image
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() } ?: ""
}
