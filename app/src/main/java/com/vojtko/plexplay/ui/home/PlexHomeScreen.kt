package com.vojtko.plexplay.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vojtko.plexplay.player.PlexPlaybackMedia

@Composable
fun PlexHomeScreen(
    modifier: Modifier = Modifier,
    onSignOutClick: () -> Unit,
    onPlayMedia: (PlexPlaybackMedia) -> Unit,
    onSessionExpired: () -> Unit,
    isMixedRealityMode: Boolean,
    onMixedRealityModeChange: (Boolean) -> Unit,
    viewModel: PlexHomeViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.requiresLogin) {
        if (uiState.requiresLogin) {
            onSessionExpired()
        }
    }

    if (uiState.requiresLogin) {
        PlexHomeLoading(modifier = modifier)
        return
    }

    when {
        uiState.isLoading && uiState.content == null -> PlexHomeLoading(modifier = modifier)
        uiState.content != null -> PlexHomeContentScreen(
            modifier = modifier,
            content = uiState.content,
            selectedCategory = uiState.selectedCategory,
            selectedCategoryItems = uiState.selectedCategoryItems,
            browseBreadcrumbs = uiState.browseBreadcrumbs,
            isCategoryLoading = uiState.isCategoryLoading,
            inlineErrorMessage = uiState.errorMessage,
            rollbackServer = uiState.rollbackServer,
            onDismissError = viewModel::dismissError,
            onReturnToPreviousServer = viewModel::returnToPreviousServer,
            onCategorySelected = viewModel::selectCategory,
            onBrowseBack = viewModel::browseBack,
            onOpenLibraryItem = viewModel::openLibraryItem,
            onSignOutClick = onSignOutClick,
            onRefreshClick = viewModel::refresh,
            onServerSelected = viewModel::selectServer,
            isMixedRealityMode = isMixedRealityMode,
            onMixedRealityModeChange = onMixedRealityModeChange,
            onPlayMedia = onPlayMedia
        )
        uiState.errorMessage != null -> PlexHomeError(
            modifier = modifier,
            message = uiState.errorMessage,
            onRetry = viewModel::refresh,
            onSignOutClick = onSignOutClick,
            rollbackServer = uiState.rollbackServer,
            onReturnToPreviousServer = viewModel::returnToPreviousServer
        )
    }
}

@Composable
private fun PlexHomeLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading your Plex libraries...")
        }
    }
}

@Composable
private fun PlexHomeError(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit,
    onSignOutClick: () -> Unit,
    rollbackServer: ServerRollbackState?,
    onReturnToPreviousServer: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 580.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Could not load Plex content",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rollbackServer?.let {
                        Button(onClick = onReturnToPreviousServer) {
                            Text("Return to ${it.serverName}")
                        }
                    }
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                    Button(onClick = onSignOutClick) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlexHomeContentScreen(
    modifier: Modifier,
    content: PlexHomeContent,
    selectedCategory: String,
    selectedCategoryItems: List<PlexMediaItem>,
    browseBreadcrumbs: List<PlexBrowseCrumb>,
    isCategoryLoading: Boolean,
    inlineErrorMessage: String?,
    rollbackServer: ServerRollbackState?,
    onDismissError: () -> Unit,
    onReturnToPreviousServer: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onBrowseBack: () -> Unit,
    onOpenLibraryItem: (PlexMediaItem) -> Unit,
    onSignOutClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onServerSelected: (String) -> Unit,
    isMixedRealityMode: Boolean,
    onMixedRealityModeChange: (Boolean) -> Unit,
    onPlayMedia: (PlexPlaybackMedia) -> Unit
) {
    val featuredItem = content.continueWatching.firstOrNull()
        ?: content.recentMovies.firstOrNull()
        ?: content.recentShows.firstOrNull()
    val sectionTitle = if (selectedCategory == "Home") content.serverName else selectedCategory

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(20.dp)
    ) {
        val continueWatching = if (selectedCategory == "Home") {
            content.continueWatching
        } else {
            content.continueWatching.filter { it.meta.contains(selectedCategory, ignoreCase = true) }
        }
        val recentMovies = if (selectedCategory == "Home" || selectedCategory == "Movies") {
            content.recentMovies
        } else {
            emptyList()
        }
        val recentShows = if (selectedCategory == "Home" || selectedCategory == "TV Shows") {
            content.recentShows
        } else {
            emptyList()
        }

        if (maxWidth < 900.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                inlineErrorMessage?.let {
                    PlexInlineError(
                        message = it,
                        rollbackServer = rollbackServer,
                        onDismiss = onDismissError,
                        onReturnToPreviousServer = onReturnToPreviousServer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                CompactHeader(
                    serverName = content.serverName,
                    selectedServerId = content.selectedServerId,
                    availableServers = content.availableServers,
                    onServerSelected = onServerSelected,
                    onRefreshClick = onRefreshClick,
                    onSignOutClick = onSignOutClick,
                    isMixedRealityMode = isMixedRealityMode,
                    onMixedRealityModeChange = onMixedRealityModeChange
                )
                Spacer(modifier = Modifier.height(16.dp))
                CategoryStrip(
                    categories = content.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
                Spacer(modifier = Modifier.height(20.dp))
                HomeContentBody(
                    featuredItem = featuredItem,
                    sectionTitle = sectionTitle,
                    selectedCategory = selectedCategory,
                    selectedCategoryItems = selectedCategoryItems,
                    browseBreadcrumbs = browseBreadcrumbs,
                    isCategoryLoading = isCategoryLoading,
                    onBrowseBack = onBrowseBack,
                    onOpenLibraryItem = onOpenLibraryItem,
                    continueWatching = continueWatching,
                    recentMovies = recentMovies,
                    recentShows = recentShows,
                    onPlayMedia = onPlayMedia
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PlexSidebar(
                    modifier = Modifier.align(Alignment.CenterStart),
                    serverName = content.serverName,
                    selectedServerId = content.selectedServerId,
                    availableServers = content.availableServers,
                    categories = content.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                    onSignOutClick = onSignOutClick,
                    onRefreshClick = onRefreshClick,
                    onServerSelected = onServerSelected,
                    isMixedRealityMode = isMixedRealityMode,
                    onMixedRealityModeChange = onMixedRealityModeChange
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 240.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    inlineErrorMessage?.let {
                        PlexInlineError(
                            message = it,
                            rollbackServer = rollbackServer,
                            onDismiss = onDismissError,
                            onReturnToPreviousServer = onReturnToPreviousServer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    HomeContentBody(
                        featuredItem = featuredItem,
                        sectionTitle = sectionTitle,
                        selectedCategory = selectedCategory,
                        selectedCategoryItems = selectedCategoryItems,
                        browseBreadcrumbs = browseBreadcrumbs,
                        isCategoryLoading = isCategoryLoading,
                        onBrowseBack = onBrowseBack,
                        onOpenLibraryItem = onOpenLibraryItem,
                        continueWatching = continueWatching,
                        recentMovies = recentMovies,
                        recentShows = recentShows,
                        onPlayMedia = onPlayMedia
                    )
                }
            }
        }
    }
}

@Composable
private fun PlexInlineError(
    message: String,
    rollbackServer: ServerRollbackState?,
    onDismiss: () -> Unit,
    onReturnToPreviousServer: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Could not switch Plex server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rollbackServer?.let {
                    Button(onClick = onReturnToPreviousServer) {
                        Text("Return to ${it.serverName}")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun HomeContentBody(
    featuredItem: PlexMediaItem?,
    sectionTitle: String,
    selectedCategory: String,
    selectedCategoryItems: List<PlexMediaItem>,
    browseBreadcrumbs: List<PlexBrowseCrumb>,
    isCategoryLoading: Boolean,
    onBrowseBack: () -> Unit,
    onOpenLibraryItem: (PlexMediaItem) -> Unit,
    continueWatching: List<PlexMediaItem>,
    recentMovies: List<PlexMediaItem>,
    recentShows: List<PlexMediaItem>,
    onPlayMedia: (PlexPlaybackMedia) -> Unit
) {
    if (selectedCategory != "Home") {
        LibraryCategoryContent(
            category = selectedCategory,
            items = selectedCategoryItems,
            breadcrumbs = browseBreadcrumbs,
            isLoading = isCategoryLoading,
            onBrowseBack = onBrowseBack,
            onOpenLibraryItem = onOpenLibraryItem,
            onPlayMedia = onPlayMedia
        )
        return
    }

    if (featuredItem != null) {
        PlexHeroBanner(
            selectedCategory = sectionTitle,
            featuredItem = featuredItem,
            onPlayMedia = onPlayMedia
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (continueWatching.isNotEmpty()) {
        PlexMediaRail(
            title = "Continue Watching",
            items = continueWatching,
            largeCards = true,
            onPlayMedia = onPlayMedia
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
    if (recentMovies.isNotEmpty()) {
        PlexMediaRail(
            title = "Recently Added Movies",
            items = recentMovies,
            onPlayMedia = onPlayMedia
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
    if (recentShows.isNotEmpty()) {
        PlexMediaRail(
            title = "Recently Added TV Shows",
            items = recentShows,
            onPlayMedia = onPlayMedia
        )
    }
    if (continueWatching.isEmpty() && recentMovies.isEmpty() && recentShows.isEmpty()) {
        Text(
            text = "No items found for this section yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryCategoryContent(
    category: String,
    items: List<PlexMediaItem>,
    breadcrumbs: List<PlexBrowseCrumb>,
    isLoading: Boolean,
    onBrowseBack: () -> Unit,
    onOpenLibraryItem: (PlexMediaItem) -> Unit,
    onPlayMedia: (PlexPlaybackMedia) -> Unit
) {
    Text(
        text = category,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (breadcrumbs.size > 1) {
            breadcrumbs.joinToString(" / ") { it.title }
        } else {
            "Full library"
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (breadcrumbs.size > 1) {
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBrowseBack) {
            Text("Back")
        }
    }
    Spacer(modifier = Modifier.height(20.dp))

    if (isLoading) {
        PlexHomeLoading(modifier = Modifier.fillMaxWidth())
        return
    }
    if (items.isEmpty()) {
        Text(
            text = "No items found in this library.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.forEach { item ->
            PlexPosterCard(
                item = item,
                largeCard = false,
                onClick = {
                    when {
                        item.browseKey != null -> onOpenLibraryItem(item)
                        item.playbackMedia != null -> onPlayMedia(item.playbackMedia)
                    }
                }
            )
        }
    }
}

@Composable
private fun CompactHeader(
    serverName: String,
    selectedServerId: String,
    availableServers: List<PlexServerOption>,
    onServerSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onSignOutClick: () -> Unit,
    isMixedRealityMode: Boolean,
    onMixedRealityModeChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "MetaPlexPlay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            ServerSelectorButton(
                serverName = serverName,
                selectedServerId = selectedServerId,
                availableServers = availableServers,
                onServerSelected = onServerSelected,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onMixedRealityModeChange(!isMixedRealityMode) }) {
                    Text(if (isMixedRealityMode) "Black background" else "Mixed reality")
                }
                Button(onClick = onRefreshClick) {
                    Text("Refresh")
                }
                Button(onClick = onSignOutClick) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun CategoryStrip(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(categories, key = { it }) { category ->
            val selected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = category,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PlexSidebar(
    modifier: Modifier = Modifier,
    serverName: String,
    selectedServerId: String,
    availableServers: List<PlexServerOption>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onSignOutClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onServerSelected: (String) -> Unit,
    isMixedRealityMode: Boolean,
    onMixedRealityModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111114),
                        Color(0xFF17181D),
                        Color(0xFF0D0E11)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "MetaPlexPlay",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        ServerSelectorButton(
            serverName = serverName,
            selectedServerId = selectedServerId,
            availableServers = availableServers,
            onServerSelected = onServerSelected,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        categories.forEach { category ->
            val selected = category == selectedCategory
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Showing your live Plex server libraries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onMixedRealityModeChange(!isMixedRealityMode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isMixedRealityMode) "Black background" else "Mixed reality")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onRefreshClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onSignOutClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun ServerSelectorButton(
    serverName: String,
    selectedServerId: String,
    availableServers: List<PlexServerOption>,
    onServerSelected: (String) -> Unit,
    textColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = if (availableServers.size > 1) "$serverName ▼" else serverName,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = availableServers.size > 1) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableServers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (server.id == selectedServerId) "${server.name} • Current" else server.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        if (server.id != selectedServerId) {
                            onServerSelected(server.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlexHeroBanner(
    selectedCategory: String,
    featuredItem: PlexMediaItem,
    onPlayMedia: (PlexPlaybackMedia) -> Unit
) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(featuredItem.accent.copy(alpha = 0.45f))
        ) {
            featuredItem.backdropUrl?.let { backdropUrl ->
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.14f),
                                featuredItem.accent.copy(alpha = 0.52f),
                                Color(0xFF101116)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = selectedCategory.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = featuredItem.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = featuredItem.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    featuredItem.playbackMedia?.let { playable ->
                        Button(onClick = { onPlayMedia(playable) }) {
                            Text("Play")
                        }
                    }
                    CategoryPill(text = featuredItem.meta)
                }
            }
        }
    }
}

@Composable
private fun PlexMediaRail(
    title: String,
    items: List<PlexMediaItem>,
    largeCards: Boolean = false,
    onPlayMedia: (PlexPlaybackMedia) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { item ->
                PlexPosterCard(
                    item = item,
                    largeCard = largeCards,
                    onClick = { item.playbackMedia?.let(onPlayMedia) }
                )
            }
        }
    }
}

@Composable
private fun PlexPosterCard(
    item: PlexMediaItem,
    largeCard: Boolean,
    onClick: () -> Unit
) {
    val cardWidth = if (largeCard) 250.dp else 190.dp
    val cardHeight = if (largeCard) 180.dp else 220.dp
    Card(
        modifier = Modifier
            .width(cardWidth)
            .clickable(enabled = item.playbackMedia != null || item.browseKey != null, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .background(item.accent.copy(alpha = 0.42f))
            ) {
                item.posterUrl?.let { posterUrl ->
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color(0xFF141519).copy(alpha = 0.88f)
                                )
                        )
                    )
                )
                CategoryPill(
                    text = item.badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
                if (item.progressLabel.isNotBlank()) {
                    Text(
                        text = item.progressLabel,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
