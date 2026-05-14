package com.vojtko.plexplay.auth

class PlexAuthRepository(
    private val store: PlexAuthStore,
    private val api: PlexAuthApi
) {
    fun getOrCreateClientIdentifier(): String = store.getOrCreateClientIdentifier()

    fun getSavedToken(): String? = store.getAuthToken()

    fun hasSavedToken(): Boolean = !store.getAuthToken().isNullOrBlank()

    fun validateSavedToken(): PlexSavedTokenValidationResult {
        val token = store.getAuthToken() ?: return PlexSavedTokenValidationResult.Invalid
        val clientId = store.getOrCreateClientIdentifier()
        return when (api.validateToken(clientId, token)) {
            PlexTokenValidationResult.Valid -> PlexSavedTokenValidationResult.Valid
            PlexTokenValidationResult.Invalid -> {
                store.clearAuthToken()
                PlexSavedTokenValidationResult.Invalid
            }
            is PlexTokenValidationResult.Error -> PlexSavedTokenValidationResult.Unreachable
        }
    }

    fun createPin(): PlexPin {
        return api.createPin(store.getOrCreateClientIdentifier())
    }

    fun clearSavedToken() {
        store.clearAuthToken()
    }

    fun buildAuthUrl(pinCode: String): String {
        return api.buildAuthUrl(store.getOrCreateClientIdentifier(), pinCode)
    }

    fun checkPin(pinId: Long, code: String): PlexPinStatus {
        return api.checkPin(store.getOrCreateClientIdentifier(), pinId, code)
    }

    fun confirmAndSaveAuthToken(token: String): PlexSavedTokenValidationResult {
        val clientId = store.getOrCreateClientIdentifier()
        return when (api.validateAccountAccess(clientId, token)) {
            PlexTokenValidationResult.Valid -> {
                store.saveAuthToken(token)
                PlexSavedTokenValidationResult.Valid
            }
            PlexTokenValidationResult.Invalid -> PlexSavedTokenValidationResult.Invalid
            is PlexTokenValidationResult.Error -> PlexSavedTokenValidationResult.Unreachable
        }
    }

    fun signOut() {
        store.clearAuthToken()
    }
}

sealed interface PlexSavedTokenValidationResult {
    data object Valid : PlexSavedTokenValidationResult
    data object Invalid : PlexSavedTokenValidationResult
    data object Unreachable : PlexSavedTokenValidationResult
}
