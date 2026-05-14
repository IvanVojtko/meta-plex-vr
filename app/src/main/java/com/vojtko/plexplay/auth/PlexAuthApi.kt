package com.vojtko.plexplay.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PlexAuthApi(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    fun validateAccountAccess(clientId: String, token: String): PlexTokenValidationResult {
        val request = Request.Builder()
            .url("https://plex.tv/api/resources?includeHttps=1")
            .header("Accept", "application/xml")
            .header(HEADER_PRODUCT, PRODUCT_NAME)
            .header(HEADER_CLIENT_ID, clientId)
            .header(HEADER_TOKEN, token)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> PlexTokenValidationResult.Valid
                401 -> PlexTokenValidationResult.Invalid
                else -> PlexTokenValidationResult.Error(response.code)
            }
        }
    }

    fun validateToken(clientId: String, token: String): PlexTokenValidationResult {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/user")
            .header("Accept", "application/json")
            .header(HEADER_PRODUCT, PRODUCT_NAME)
            .header(HEADER_CLIENT_ID, clientId)
            .header(HEADER_TOKEN, token)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> PlexTokenValidationResult.Valid
                401 -> PlexTokenValidationResult.Invalid
                else -> PlexTokenValidationResult.Error(response.code)
            }
        }
    }

    fun createPin(clientId: String): PlexPin {
        val body = FormBody.Builder()
            .add("strong", "true")
            .build()
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins")
            .header("Accept", "application/json")
            .header(HEADER_PRODUCT, PRODUCT_NAME)
            .header(HEADER_CLIENT_ID, clientId)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("PIN request failed with HTTP ${response.code}")
            }

            val json = JSONObject(payload)
            return PlexPin(
                id = json.getLong("id"),
                code = json.getString("code"),
                expiresIn = json.optInt("expiresIn", 600)
            )
        }
    }

    fun checkPin(clientId: String, pinId: Long, code: String): PlexPinStatus {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins/$pinId?code=${encode(code)}")
            .header("Accept", "application/json")
            .header(HEADER_CLIENT_ID, clientId)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("PIN status request failed with HTTP ${response.code}")
            }

            val json = JSONObject(payload)
            return PlexPinStatus(
                authToken = json.optString("authToken").takeIf { it.isNotBlank() },
                expiresIn = json.optInt("expiresIn", 0)
            )
        }
    }

    fun buildAuthUrl(clientId: String, pinCode: String): String {
        return "https://app.plex.tv/auth#?" +
            "clientID=${encode(clientId)}&" +
            "code=${encode(pinCode)}&" +
            "context%5Bdevice%5D%5Bproduct%5D=${encode(PRODUCT_NAME)}"
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val PRODUCT_NAME = "MetaPlexPlay"
        private const val HEADER_CLIENT_ID = "X-Plex-Client-Identifier"
        private const val HEADER_PRODUCT = "X-Plex-Product"
        private const val HEADER_TOKEN = "X-Plex-Token"
    }
}

sealed interface PlexTokenValidationResult {
    data object Valid : PlexTokenValidationResult
    data object Invalid : PlexTokenValidationResult
    data class Error(val code: Int) : PlexTokenValidationResult
}

data class PlexPin(
    val id: Long,
    val code: String,
    val expiresIn: Int
)

data class PlexPinStatus(
    val authToken: String?,
    val expiresIn: Int
)
