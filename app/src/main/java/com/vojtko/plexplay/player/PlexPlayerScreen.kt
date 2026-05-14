package com.vojtko.plexplay.player

import android.graphics.Color.BLACK
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vojtko.plexplay.PlaybackScreenSize
import java.util.Locale
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlexPlayerScreen(
    media: PlexPlaybackMedia,
    modifier: Modifier = Modifier,
    isImmersiveMode: Boolean,
    screenSize: PlaybackScreenSize,
    isCurvedScreen: Boolean,
    onImmersiveModeChange: (Boolean) -> Unit,
    onScreenSizeChange: (PlaybackScreenSize) -> Unit,
    onCurvedScreenChange: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var playbackError by remember(media.id) { mutableStateOf<String?>(null) }
    var isRetryingWithTranscode by remember(media.id) { mutableStateOf(false) }
    var hasRetriedWithTranscode by remember(media.id) { mutableStateOf(false) }
    var isBuffering by remember(media.id) { mutableStateOf(true) }
    var hasAudioTracks by remember(media.id) { mutableStateOf(false) }
    var hasSubtitleTracks by remember(media.id) { mutableStateOf(false) }
    var currentTracks by remember(media.id) { mutableStateOf(Tracks.EMPTY) }
    var selectorMode by remember(media.id) { mutableStateOf<TrackSelectorMode?>(null) }
    var controlsVisible by remember(media.id) { mutableStateOf(true) }
    var controlsActivityTick by remember(media.id) { mutableStateOf(0) }
    var playerViewRef by remember(media.id) { mutableStateOf<PlayerView?>(null) }
    val exoPlayer = remember(media.id) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(media.requestHeaders)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) {
                            playbackError = null
                        }
                        if (playbackState == Player.STATE_READY) {
                            isRetryingWithTranscode = false
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        currentTracks = tracks
                        hasAudioTracks = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
                        hasSubtitleTracks = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val fallbackStreamUrl = media.fallbackStreamUrl
                        if (!hasRetriedWithTranscode && fallbackStreamUrl != null) {
                            hasRetriedWithTranscode = true
                            isRetryingWithTranscode = true
                            playbackError = null
                            setMediaItem(MediaItem.fromUri(fallbackStreamUrl))
                            prepare()
                            playWhenReady = true
                            return
                        }
                        isRetryingWithTranscode = false
                        playbackError = error.message ?: "Playback failed."
                    }
                })
                setMediaItem(MediaItem.fromUri(media.streamUrl))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(media.id) {
        playbackError = null
        isRetryingWithTranscode = false
        hasRetriedWithTranscode = false
        isBuffering = true
    }

    LaunchedEffect(controlsVisible, selectorMode, isBuffering, playbackError, playerViewRef) {
        val playerView = playerViewRef ?: return@LaunchedEffect
        val shouldShowController =
            controlsVisible || selectorMode != null || isBuffering || playbackError != null
        if (shouldShowController) {
            playerView.showController()
        } else {
            playerView.hideController()
        }
    }

    LaunchedEffect(controlsVisible, controlsActivityTick, selectorMode, isBuffering, playbackError) {
        if (controlsVisible && selectorMode == null && !isBuffering && playbackError == null) {
            delay(3500)
            if (selectorMode == null && !isBuffering && playbackError == null) {
                controlsVisible = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isImmersiveMode) {
                    Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Black,
                            androidx.compose.ui.graphics.Color.Black
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            )
            .padding(24.dp)
            .pointerInput(media.id) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                    controlsActivityTick++
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = exoPlayer
                            useController = true
                            controllerAutoShow = false
                            controllerHideOnTouch = false
                            setBackgroundColor(BLACK)
                            setShutterBackgroundColor(BLACK)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            playerViewRef = this
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerViewRef = playerView
                    }
                )

                if (isBuffering || isRetryingWithTranscode) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp)
                        )
                        if (isRetryingWithTranscode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Retrying with Plex transcoding...",
                                color = Color.White
                            )
                        }
                    }
                }

                if (!controlsVisible && selectorMode == null && playbackError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                controlsVisible = true
                                controlsActivityTick++
                            }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible || selectorMode != null || isBuffering || playbackError != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.66f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = media.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScreenSizeButton(
                                    label = "S",
                                    selected = screenSize == PlaybackScreenSize.Small,
                                    enabled = !isCurvedScreen,
                                    onClick = {
                                        controlsActivityTick++
                                        onScreenSizeChange(PlaybackScreenSize.Small)
                                    }
                                )
                                ScreenSizeButton(
                                    label = "M",
                                    selected = screenSize == PlaybackScreenSize.Medium,
                                    enabled = !isCurvedScreen,
                                    onClick = {
                                        controlsActivityTick++
                                        onScreenSizeChange(PlaybackScreenSize.Medium)
                                    }
                                )
                                ScreenSizeButton(
                                    label = "L",
                                    selected = screenSize == PlaybackScreenSize.Large,
                                    enabled = !isCurvedScreen,
                                    onClick = {
                                        controlsActivityTick++
                                        onScreenSizeChange(PlaybackScreenSize.Large)
                                    }
                                )
                                Button(onClick = {
                                    controlsActivityTick++
                                    onCurvedScreenChange(!isCurvedScreen)
                                }) {
                                    Text(if (isCurvedScreen) "Flat" else "Curved")
                                }
                                Button(
                                    enabled = hasAudioTracks,
                                    onClick = {
                                        controlsActivityTick++
                                        selectorMode = TrackSelectorMode.Audio
                                    }
                                ) {
                                    Text("Audio")
                                }
                                Button(
                                    enabled = hasSubtitleTracks,
                                    onClick = {
                                        controlsActivityTick++
                                        selectorMode = TrackSelectorMode.Subtitles
                                    }
                                ) {
                                    Text("Subtitles")
                                }
                                Button(onClick = {
                                    controlsActivityTick++
                                    onImmersiveModeChange(!isImmersiveMode)
                                }) {
                                    Text(if (isImmersiveMode) "Mixed reality" else "Black background")
                                }
                                Button(onClick = {
                                    controlsActivityTick++
                                    onBackClick()
                                }) {
                                    Text("Back to library")
                                }
                            }
                        }
                    }
                }

                playbackError?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                selectorMode?.let { mode ->
                    TrackSelectorOverlay(
                        title = if (mode == TrackSelectorMode.Audio) "Audio" else "Subtitles",
                        options = buildTrackOptions(
                            tracks = currentTracks,
                            trackType = if (mode == TrackSelectorMode.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                        ),
                        includeOffOption = mode == TrackSelectorMode.Subtitles,
                        onDismiss = { selectorMode = null },
                        onDisable = {
                            val trackType = if (mode == TrackSelectorMode.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                            exoPlayer.setTrackSelectionParameters(
                                exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(trackType, true)
                                    .clearOverridesOfType(trackType)
                                    .build()
                            )
                            selectorMode = null
                        },
                        onSelect = { option ->
                            val trackType = if (mode == TrackSelectorMode.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                            exoPlayer.setTrackSelectionParameters(
                                exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(trackType, false)
                                    .clearOverridesOfType(trackType)
                                    .addOverride(TrackSelectionOverride(option.group, option.trackIndex))
                                    .build()
                            )
                            selectorMode = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenSizeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick
    ) {
        Text(if (selected) "Size $label" else label)
    }
}

@Composable
private fun TrackSelectorOverlay(
    title: String,
    options: List<TrackOption>,
    includeOffOption: Boolean,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (TrackOption) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17181D))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (includeOffOption) {
                    OutlinedButton(onClick = onDisable) {
                        Text("Off")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (options.isEmpty()) {
                    Text(
                        text = "No tracks available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    options.forEach { option ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(option) }
                        ) {
                            Text(option.label)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

private fun buildTrackOptions(
    tracks: Tracks,
    trackType: Int
): List<TrackOption> {
    return tracks.groups
        .filter { it.type == trackType && it.isSupported() }
        .flatMap { group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { index ->
                    TrackOption(
                        group = group.mediaTrackGroup,
                        trackIndex = index,
                        label = trackLabel(group.getTrackFormat(index), trackType, index)
                    )
                }
        }
}

private fun trackLabel(format: Format, trackType: Int, index: Int): String {
    val language = format.language
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
    val label = format.label?.takeIf { it.isNotBlank() }
    return when {
        label != null && language != null && !label.contains(language, ignoreCase = true) -> "$label • $language"
        label != null -> label
        language != null -> language.replaceFirstChar { it.uppercase() }
        trackType == C.TRACK_TYPE_AUDIO -> "Audio ${index + 1}"
        else -> "Subtitle ${index + 1}"
    }
}

private enum class TrackSelectorMode {
    Audio,
    Subtitles
}

private data class TrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String
)

data class PlexPlaybackMedia(
    val id: String,
    val title: String,
    val subtitle: String,
    val streamUrl: String,
    val fallbackStreamUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap()
)
