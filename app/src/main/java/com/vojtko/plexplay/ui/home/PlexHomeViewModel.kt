package com.vojtko.plexplay.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vojtko.plexplay.auth.PlexAuthStore
import com.vojtko.plexplay.plex.PlexAuthenticationException
import com.vojtko.plexplay.plex.PlexLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlexHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlexLibraryRepository(application)
    private val authStore = PlexAuthStore(application)

    private val _uiState = MutableStateFlow(PlexHomeUiState(isLoading = true))
    val uiState: StateFlow<PlexHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                rollbackServer = null
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHomeContent() }
            }.onSuccess { content ->
                _uiState.value = PlexHomeUiState(
                    isLoading = false,
                    content = content,
                    selectedCategory = content.categories.firstOrNull().orEmpty()
                )
            }.onFailure { error ->
                if (error is PlexAuthenticationException) {
                    authStore.clearAuthToken()
                    _uiState.value = PlexHomeUiState(
                        isLoading = false,
                        requiresLogin = true,
                        errorMessage = error.message ?: "Your Plex session expired."
                    )
                } else {
                    val hadSavedServer = authStore.getSelectedServerId() != null
                    if (hadSavedServer) {
                        authStore.clearSelectedServerId()
                        runCatching {
                            withContext(Dispatchers.IO) { repository.loadHomeContent() }
                        }.onSuccess { content ->
                            _uiState.value = PlexHomeUiState(
                                isLoading = false,
                                content = content,
                                selectedCategory = content.categories.firstOrNull().orEmpty(),
                                errorMessage = "Saved Plex server was unavailable. Switched to ${content.serverName}."
                            )
                        }.onFailure { fallbackError ->
                            _uiState.value = PlexHomeUiState(
                                isLoading = false,
                                errorMessage = fallbackError.message ?: "Could not load Plex libraries."
                            )
                        }
                    } else {
                        _uiState.value = PlexHomeUiState(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load Plex libraries."
                        )
                    }
                }
            }
        }
    }

    fun selectServer(serverId: String) {
        val previousState = _uiState.value
        val previousContent = previousState.content ?: return
        if (previousContent.selectedServerId == serverId) {
            return
        }
        val previousServer = previousContent.availableServers.firstOrNull {
            it.id == previousContent.selectedServerId
        }?.let {
            ServerRollbackState(serverId = it.id, serverName = it.name)
        } ?: ServerRollbackState(
            serverId = previousContent.selectedServerId,
            serverName = previousContent.serverName
        )
        _uiState.value = previousState.copy(
            isLoading = true,
            errorMessage = null,
            rollbackServer = null,
            selectedCategory = "Home",
            selectedCategoryItems = emptyList(),
            browseBreadcrumbs = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadHomeContent(serverId = serverId, persistSelection = false)
                }
            }.onSuccess { content ->
                repository.saveSelectedServer(content.selectedServerId)
                _uiState.value = PlexHomeUiState(
                    isLoading = false,
                    content = content,
                    selectedCategory = content.categories.firstOrNull().orEmpty()
                )
            }.onFailure { error ->
                if (error is PlexAuthenticationException) {
                    authStore.clearAuthToken()
                    _uiState.value = PlexHomeUiState(
                        isLoading = false,
                        requiresLogin = true,
                        errorMessage = error.message ?: "Your Plex session expired."
                    )
                } else {
                    _uiState.value = previousState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Could not connect to the selected Plex server.",
                        rollbackServer = previousServer
                    )
                }
            }
        }
    }

    fun returnToPreviousServer() {
        val rollbackServer = _uiState.value.rollbackServer ?: return
        repository.saveSelectedServer(rollbackServer.serverId)
        _uiState.value = _uiState.value.copy(errorMessage = null, rollbackServer = null)
        refresh()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, rollbackServer = null)
    }

    fun selectCategory(category: String) {
        val content = _uiState.value.content ?: run {
            _uiState.value = _uiState.value.copy(selectedCategory = category)
            return
        }
        if (category == "Home") {
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                selectedCategoryItems = emptyList(),
                browseBreadcrumbs = emptyList(),
                isCategoryLoading = false,
                errorMessage = null
            )
            return
        }
        if (content.libraries.none { it.title == category }) {
            _uiState.value = _uiState.value.copy(selectedCategory = category)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                selectedCategoryItems = emptyList(),
                browseBreadcrumbs = listOf(PlexBrowseCrumb(title = category, browseKey = null)),
                isCategoryLoading = true,
                errorMessage = null
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.loadLibraryItems(category) }
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    selectedCategory = category,
                    selectedCategoryItems = items,
                    browseBreadcrumbs = listOf(PlexBrowseCrumb(title = category, browseKey = null)),
                    isCategoryLoading = false
                )
            }.onFailure { error ->
                if (error is PlexAuthenticationException) {
                    authStore.clearAuthToken()
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        requiresLogin = true,
                        errorMessage = error.message ?: "Your Plex session expired."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        errorMessage = error.message ?: "Could not load Plex library."
                    )
                }
            }
        }
    }

    fun openLibraryItem(item: PlexMediaItem) {
        val browseKey = item.browseKey ?: return
        val currentCategory = _uiState.value.selectedCategory
        val currentBreadcrumbs = _uiState.value.browseBreadcrumbs
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCategoryLoading = true,
                errorMessage = null
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.loadBrowseItems(browseKey) }
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    selectedCategory = currentCategory,
                    selectedCategoryItems = items,
                    browseBreadcrumbs = currentBreadcrumbs + PlexBrowseCrumb(
                        title = item.title,
                        browseKey = browseKey
                    ),
                    isCategoryLoading = false
                )
            }.onFailure { error ->
                if (error is PlexAuthenticationException) {
                    authStore.clearAuthToken()
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        requiresLogin = true,
                        errorMessage = error.message ?: "Your Plex session expired."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        errorMessage = error.message ?: "Could not load Plex library items."
                    )
                }
            }
        }
    }

    fun browseBack() {
        val breadcrumbs = _uiState.value.browseBreadcrumbs
        val category = _uiState.value.selectedCategory
        if (category == "Home" || breadcrumbs.size <= 1) {
            return
        }
        val target = breadcrumbs[breadcrumbs.lastIndex - 1]
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCategoryLoading = true,
                errorMessage = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    if (target.browseKey == null) repository.loadLibraryItems(category)
                    else repository.loadBrowseItems(target.browseKey)
                }
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    selectedCategoryItems = items,
                    browseBreadcrumbs = breadcrumbs.dropLast(1),
                    isCategoryLoading = false
                )
            }.onFailure { error ->
                if (error is PlexAuthenticationException) {
                    authStore.clearAuthToken()
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        requiresLogin = true,
                        errorMessage = error.message ?: "Your Plex session expired."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCategoryLoading = false,
                        errorMessage = error.message ?: "Could not load Plex library items."
                    )
                }
            }
        }
    }
}

data class PlexHomeUiState(
    val isLoading: Boolean = false,
    val content: PlexHomeContent? = null,
    val selectedCategory: String = "",
    val selectedCategoryItems: List<PlexMediaItem> = emptyList(),
    val browseBreadcrumbs: List<PlexBrowseCrumb> = emptyList(),
    val isCategoryLoading: Boolean = false,
    val errorMessage: String? = null,
    val rollbackServer: ServerRollbackState? = null,
    val requiresLogin: Boolean = false
)

data class ServerRollbackState(
    val serverId: String,
    val serverName: String
)
