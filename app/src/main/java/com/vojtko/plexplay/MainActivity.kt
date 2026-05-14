package com.vojtko.plexplay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.spatial.animation.PanelAnimationFeature
import com.meta.spatial.animation.PanelQuadCylinderAnimation
import com.meta.spatial.animation.PanelQuadCylinderAnimationType
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.DataModel
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.Vector3
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.vr.VRFeature
import com.vojtko.plexplay.auth.PlexLoginUiState
import com.vojtko.plexplay.auth.PlexLoginViewModel
import com.vojtko.plexplay.player.PlexPlaybackMedia
import com.vojtko.plexplay.player.PlexPlayerScreen
import com.vojtko.plexplay.ui.home.PlexHomeScreen
import com.vojtko.plexplay.ui.theme.PlexPlayTheme

@OptIn(SpatialSDKExperimentalAPI::class)
class MainActivity : AppSystemActivity() {
    private val loginViewModel by lazy { PlexLoginViewModel(application) }
    private val homeViewModel by lazy { com.vojtko.plexplay.ui.home.PlexHomeViewModel(application) }
    private var mixedRealityEnabled = true
    private var panelEntity: Entity? = null
    private var curvedPlaybackEnabled = false

    override fun registerFeatures(): List<SpatialFeature> {
        val features = mutableListOf<SpatialFeature>(
            VRFeature(this),
            ComposeFeature(),
            PanelAnimationFeature()
        )
        if (BuildConfig.DEBUG) {
            features += CastInputForwardFeature(this)
            features += HotReloadFeature(this)
        }
        return features
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(mixedRealityEnabled)
        if (panelEntity == null) {
            panelEntity = Entity.create(
                listOf(
                    Panel(R.id.panel),
                    Transform(Pose(Vector3(x = 0f, y = 1.45f, z = 1.6f))),
                    Scale(Vector3(1f, 1f, 1f)),
                    Grabbable()
                )
            )
        }
    }

    fun setMixedRealityEnabled(enabled: Boolean) {
        mixedRealityEnabled = enabled
        scene.enablePassthrough(enabled)
    }

    fun setPlaybackScreenSize(sizePreset: PlaybackScreenSize) {
        val scale = when (sizePreset) {
            PlaybackScreenSize.Small -> 0.85f
            PlaybackScreenSize.Medium -> 1.0f
            PlaybackScreenSize.Large -> 1.2f
        }
        panelEntity?.setComponent(Scale(Vector3(scale, scale, 1f)))
    }

    fun setPlaybackScreenCurved(enabled: Boolean) {
        if (curvedPlaybackEnabled == enabled) return
        curvedPlaybackEnabled = enabled
        panelEntity?.setComponent(
            if (enabled) {
                PanelQuadCylinderAnimation(
                    startTime = DataModel.getLocalDataModelTime(),
                    animationType = PanelQuadCylinderAnimationType.QUAD_TO_CYLINDER,
                    targetRadius = 1.6f,
                    durationInMs = 300L
                )
            } else {
                PanelQuadCylinderAnimation(
                    startTime = DataModel.getLocalDataModelTime(),
                    animationType = PanelQuadCylinderAnimationType.CYLINDER_TO_QUAD,
                    durationInMs = 300L
                )
            }
        )
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            ComposeViewPanelRegistration(
                R.id.panel,
                composeViewCreator = { _, ctx ->
                    ComposeView(ctx).apply {
                        setContent {
                            PlexPlayTheme {
                                MainContent(
                                    activity = this@MainActivity,
                                    loginViewModel = loginViewModel,
                                    homeViewModel = homeViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 2.8f, height = 1.575f),
                        style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                        display = DpDisplayOptions(width = 1600f, height = 900f, dpi = 600)
                    )
                }
            )
        )
    }
}

enum class PlaybackScreenSize {
    Small,
    Medium,
    Large
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    activity: MainActivity,
    loginViewModel: PlexLoginViewModel,
    homeViewModel: com.vojtko.plexplay.ui.home.PlexHomeViewModel
) {
    val uiState = loginViewModel.uiState.collectAsStateWithLifecycle().value
    var selectedMedia by remember { mutableStateOf<PlexPlaybackMedia?>(null) }
    var immersivePlayback by remember { mutableStateOf(false) }
    var playbackScreenSize by remember { mutableStateOf(PlaybackScreenSize.Medium) }
    var curvedPlayback by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn, selectedMedia, immersivePlayback) {
        val shouldUseMixedReality = !uiState.isLoggedIn || selectedMedia == null || !immersivePlayback
        activity.setMixedRealityEnabled(shouldUseMixedReality)
    }

    LaunchedEffect(selectedMedia, playbackScreenSize) {
        activity.setPlaybackScreenSize(
            if (selectedMedia != null) playbackScreenSize else PlaybackScreenSize.Medium
        )
    }

    LaunchedEffect(selectedMedia, curvedPlayback) {
        activity.setPlaybackScreenCurved(selectedMedia != null && curvedPlayback)
    }

    if (uiState.isLoggedIn) {
        val media = selectedMedia
        if (media != null) {
            PlexPlayerScreen(
                media = media,
                modifier = modifier,
                isImmersiveMode = immersivePlayback,
                screenSize = playbackScreenSize,
                isCurvedScreen = curvedPlayback,
                onImmersiveModeChange = { immersivePlayback = it },
                onScreenSizeChange = {
                    if (curvedPlayback) {
                        curvedPlayback = false
                    }
                    playbackScreenSize = it
                },
                onCurvedScreenChange = {
                    curvedPlayback = it
                    if (it) {
                        playbackScreenSize = PlaybackScreenSize.Medium
                    }
                },
                onBackClick = {
                    immersivePlayback = false
                    playbackScreenSize = PlaybackScreenSize.Medium
                    curvedPlayback = false
                    selectedMedia = null
                }
            )
        } else {
            PlexHomeScreen(
                viewModel = homeViewModel,
                modifier = modifier,
                onSignOutClick = {
                    immersivePlayback = false
                    playbackScreenSize = PlaybackScreenSize.Medium
                    curvedPlayback = false
                    selectedMedia = null
                    loginViewModel.signOut()
                },
                onPlayMedia = {
                    immersivePlayback = false
                    playbackScreenSize = PlaybackScreenSize.Medium
                    curvedPlayback = false
                    selectedMedia = it
                },
                onSessionExpired = {
                    immersivePlayback = false
                    playbackScreenSize = PlaybackScreenSize.Medium
                    curvedPlayback = false
                    selectedMedia = null
                    loginViewModel.signOut()
                }
            )
        }
    } else {
        PlexLoginScreen(
            modifier = modifier,
            uiState = uiState,
            onSignInClick = {
                loginViewModel.beginLogin { authUrl ->
                    immersivePlayback = false
                    playbackScreenSize = PlaybackScreenSize.Medium
                    curvedPlayback = false
                    selectedMedia = null
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                }
            },
            onSignOutClick = loginViewModel::signOut
        )
    }
}

@Composable
private fun PlexLoginScreen(
    modifier: Modifier = Modifier,
    uiState: PlexLoginUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
            )
        ) {
            PlexLoginCard(
                uiState = uiState,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick
            )
        }
    }
}

@Composable
private fun PlexLoginCard(
    uiState: PlexLoginUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (uiState.isLoggedIn) {
                stringResource(R.string.plex_connected_title)
            } else {
                stringResource(R.string.plex_login_title)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = if (uiState.isLoggedIn) {
                stringResource(R.string.plex_connected_description)
            } else {
                stringResource(R.string.plex_login_description)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.88f)
        )
        uiState.pinCode?.let { pinCode ->
            Spacer(modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.plex_pin_label, pinCode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        uiState.statusMessage?.let { message ->
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        }
        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.size(24.dp))
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = if (uiState.isLoggedIn) onSignOutClick else onSignInClick
            ) {
                Text(
                    text = if (uiState.isLoggedIn) {
                        stringResource(R.string.plex_logout_button)
                    } else {
                        stringResource(R.string.plex_login_button)
                    }
                )
            }
        }
    }
}
