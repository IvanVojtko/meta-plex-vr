# MetaPlexVR

MetaPlexVR is an open source Plex movie and TV player for Meta Quest headsets.

It is built as a Meta Spatial SDK application for Horizon OS and is designed around a mixed reality browsing experience with optional immersive playback.

## Status

This project is functional but still early-stage.

What works today:
- Plex sign-in with persistent session restore
- Plex server discovery from a Plex account
- Library browsing for movies and TV
- Continue Watching and Recently Added home rails
- Poster and backdrop artwork
- Playback with ExoPlayer / Media3
- Audio and subtitle track selection
- Mixed reality browsing with optional black-background immersive playback

What is still incomplete:
- Full Plex transcoding/session support
- Robust pagination for very large libraries
- Better TV hierarchy and episode navigation polish
- Multi-server selection UI
- Store-ready release automation polish

## Tech Stack

- Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- OkHttp
- Coil
- Meta Spatial SDK

## Device Target

Supported devices are currently restricted to:
- Quest 2
- Quest Pro
- Quest 3
- Quest 3S

Quest 1 is intentionally not supported.

## Requirements

- Android Studio
- JDK 17
- Meta Quest device
- Plex account
- Access to a Plex Media Server

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/MetaPlexVR.git
cd MetaPlexVR
```

### 2. Open in Android Studio

Open the project root in Android Studio and let Gradle sync.

### 3. Build a debug APK

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on a Quest device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Plex Authentication

MetaPlexVR uses the Plex in the browser sign-in flow.

Current behavior:
- opens Plex authentication in the browser
- stores the Plex token locally
- restores the session on next launch

## Playback Model

Current playback uses direct authenticated media URLs from Plex metadata where possible.

That means:
- direct-play-compatible files work best
- some subtitle/audio/transcode cases may still need a fuller Plex session/transcode implementation

## Mixed Reality Behavior

By default:
- browsing is shown in mixed reality
- playback starts in mixed reality

During playback the user can:
- switch to a black immersive background
- switch between small, medium, and large screen sizes

## Known Limitations

- Some Plex libraries may expose incomplete metadata for artwork or nested TV structure.
- Some media items may require Plex transcoding rather than direct file playback.
- Audio/subtitle selection depends on tracks being visible to ExoPlayer in the current stream.
- Large libraries currently use bounded fetch sizes rather than full lazy paging.

## Project Structure

```text
app/src/main/java/com/vojtko/plexplay/
  auth/        Plex authentication and session storage
  plex/        Plex API and library loading
  player/      Media3 player UI and playback controls
  ui/home/     Home screen, library browsing, TV navigation
  ui/theme/    App theme and colors
```

## Contributing

Contributions are welcome.

Good areas to improve:
- Plex transcoding support
- season / episode UX
- pagination for large libraries
- multi-server support
- better spatial theater mode
- test coverage
- store packaging and CI

If you open a PR, keep changes focused and include build/test notes.

## GitHub Release CI

This repository includes a GitHub Actions workflow at `.github/workflows/release-apk.yml`.

When a GitHub release is published, the workflow will:
- rebuild the Android signing keystore from GitHub Secrets
- generate `keystore.properties`
- run `./gradlew assembleRelease`
- attach the signed APK to the GitHub release

Required repository secrets:
- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

To create the Base64 secret from your keystore:

```bash
base64 -w 0 MetaPlexPlay-release.jks
```

On macOS, use:

```bash
base64 MetaPlexPlay-release.jks | tr -d '\n'
```
