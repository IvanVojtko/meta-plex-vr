package com.vojtko.plexplay.ui.home

import androidx.compose.ui.graphics.Color
import com.vojtko.plexplay.player.PlexPlaybackMedia

data class PlexHomeContent(
    val serverName: String,
    val categories: List<String>,
    val libraries: List<PlexLibraryItem>,
    val continueWatching: List<PlexMediaItem>,
    val recentMovies: List<PlexMediaItem>,
    val recentShows: List<PlexMediaItem>
)

data class PlexLibraryItem(
    val title: String,
    val type: String
)

data class PlexMediaItem(
    val id: String,
    val itemType: String,
    val title: String,
    val meta: String,
    val badge: String,
    val progressLabel: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val accent: Color,
    val browseKey: String?,
    val playbackMedia: PlexPlaybackMedia?
)

data class PlexBrowseCrumb(
    val title: String,
    val browseKey: String?
)
