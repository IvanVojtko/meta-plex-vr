package com.vojtko.plexplay.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlexLoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlexAuthRepository(
        store = PlexAuthStore(application),
        api = PlexAuthApi()
    )

    private val _uiState = MutableStateFlow(PlexLoginUiState(isLoading = true))
    val uiState: StateFlow<PlexLoginUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var restoreSessionJob: Job? = null

    init {
        val hasSavedToken = repository.hasSavedToken()
        _uiState.value = PlexLoginUiState(
            isLoading = hasSavedToken,
            isLoggedIn = false,
            statusMessage = if (hasSavedToken) "Restoring saved Plex session..." else null
        )

        restoreSessionJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.validateSavedToken()
                }
            }.onSuccess { result ->
                when (result) {
                    PlexSavedTokenValidationResult.Valid -> {
                        _uiState.value = PlexLoginUiState(
                            isLoading = false,
                            isLoggedIn = true,
                            statusMessage = "Connected to Plex."
                        )
                    }
                    PlexSavedTokenValidationResult.Invalid -> {
                        _uiState.value = PlexLoginUiState(
                            isLoading = false,
                            isLoggedIn = false,
                            statusMessage = null
                        )
                    }
                    PlexSavedTokenValidationResult.Unreachable -> {
                        _uiState.value = PlexLoginUiState(
                            isLoading = false,
                            isLoggedIn = false,
                            errorMessage = if (hasSavedToken) {
                                "Could not restore the saved Plex session. Try signing in again."
                            } else {
                                null
                            }
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.value = PlexLoginUiState(
                    isLoading = false,
                    isLoggedIn = false,
                    errorMessage = error.message ?: "Could not verify the saved Plex session."
                )
            }
        }
    }

    fun beginLogin(onAuthUrlReady: (String) -> Unit) {
        if (_uiState.value.isLoading) {
            return
        }

        restoreSessionJob?.cancel()
        pollingJob?.cancel()
        repository.clearSavedToken()
        viewModelScope.launch {
            _uiState.value = PlexLoginUiState(
                isLoading = true,
                isLoggedIn = false,
                pinCode = null,
                statusMessage = "Requesting a Plex sign-in code...",
                errorMessage = null
            )

            runCatching {
                withContext(Dispatchers.IO) { repository.createPin() }
            }.onSuccess { pin ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pinCode = pin.code,
                    statusMessage = "Continue sign-in in the browser."
                )
                onAuthUrlReady(repository.buildAuthUrl(pin.code))
                startPolling(pin)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unable to start Plex sign-in."
                )
            }
        }
    }

    fun signOut() {
        restoreSessionJob?.cancel()
        pollingJob?.cancel()
        repository.signOut()
        _uiState.value = PlexLoginUiState(
            isLoading = false,
            statusMessage = "Disconnected from Plex."
        )
    }

    private fun startPolling(pin: PlexPin) {
        pollingJob = viewModelScope.launch {
            val maxAttempts = pin.expiresIn.coerceAtLeast(60)
            repeat(maxAttempts) {
                delay(1_000)
                val result = runCatching {
                    withContext(Dispatchers.IO) { repository.checkPin(pin.id, pin.code) }
                }

                result.onSuccess { status ->
                    val authToken = status.authToken
                    if (!authToken.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            statusMessage = "Finishing Plex authorization...",
                            errorMessage = null
                        )

                        when (
                            withContext(Dispatchers.IO) {
                                repository.confirmAndSaveAuthToken(authToken)
                            }
                        ) {
                            PlexSavedTokenValidationResult.Valid -> {
                                _uiState.value = PlexLoginUiState(
                                    isLoading = false,
                                    isLoggedIn = true,
                                    statusMessage = "Connected to Plex."
                                )
                                return@launch
                            }
                            PlexSavedTokenValidationResult.Invalid,
                            PlexSavedTokenValidationResult.Unreachable -> {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    statusMessage = "Waiting for Plex authorization to complete..."
                                )
                            }
                        }
                    }
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Could not verify Plex sign-in."
                    )
                    return@launch
                }
            }

            _uiState.value = _uiState.value.copy(
                errorMessage = "The Plex sign-in code expired. Try again.",
                statusMessage = null
            )
        }
    }
}

data class PlexLoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val pinCode: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
