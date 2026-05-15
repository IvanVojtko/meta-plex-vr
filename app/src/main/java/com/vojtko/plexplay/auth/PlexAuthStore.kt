package com.vojtko.plexplay.auth

import android.content.Context
import java.util.UUID

class PlexAuthStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getOrCreateClientIdentifier(): String {
        val existing = preferences.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_CLIENT_ID, generated).apply()
        return generated
    }

    fun getAuthToken(): String? = preferences.getString(KEY_AUTH_TOKEN, null)

    fun saveAuthToken(token: String) {
        preferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getSelectedServerId(): String? = preferences.getString(KEY_SELECTED_SERVER_ID, null)

    fun saveSelectedServerId(serverId: String) {
        preferences.edit().putString(KEY_SELECTED_SERVER_ID, serverId).apply()
    }

    fun clearSelectedServerId() {
        preferences.edit().remove(KEY_SELECTED_SERVER_ID).apply()
    }

    fun clearAuthToken() {
        preferences.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_SELECTED_SERVER_ID)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "plex_auth"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
    }
}
