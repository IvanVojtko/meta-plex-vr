package com.vojtko.plexplay.plex

import android.content.Context
import android.util.Xml
import androidx.compose.ui.graphics.Color
import com.vojtko.plexplay.auth.PlexAuthStore
import com.vojtko.plexplay.player.PlexPlaybackMedia
import com.vojtko.plexplay.ui.home.PlexBrowseCrumb
import com.vojtko.plexplay.ui.home.PlexHomeContent
import com.vojtko.plexplay.ui.home.PlexLibraryItem
import com.vojtko.plexplay.ui.home.PlexMediaItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder

class PlexLibraryRepository(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val authStore = PlexAuthStore(context)

    fun loadHomeContent(): PlexHomeContent {
        val accountToken = authStore.getAuthToken()
            ?: throw IllegalStateException("Missing Plex auth token.")
        val clientId = authStore.getOrCreateClientIdentifier()
        val server = discoverServers(accountToken, clientId).firstOrNull()
            ?: throw IllegalStateException("No Plex Media Server was found for this account.")
        val sections = fetchSections(server, clientId)
        val movieSection = sections.firstOrNull { it.type == "movie" }
        val showSection = sections.firstOrNull { it.type == "show" }

        val categories = buildList {
            add("Home")
            addAll(sections.map { it.title })
        }

        return PlexHomeContent(
            serverName = server.name,
            categories = categories,
            libraries = sections.map { PlexLibraryItem(title = it.title, type = it.type) },
            continueWatching = fetchContinueWatching(server, clientId, count = 10),
            recentMovies = movieSection?.let {
                fetchRecentlyAddedMovies(server, clientId, it.id, count = 10)
            }.orEmpty(),
            recentShows = showSection?.let {
                fetchRecentlyAddedShows(server, clientId, it.id, count = 10)
            }.orEmpty()
        )
    }

    fun loadLibraryItems(category: String, count: Int = 60): List<PlexMediaItem> {
        val session = createSession()
        val server = session.server
        val clientId = session.clientId
        val section = fetchSections(server, clientId).firstOrNull { it.title == category }
            ?: return emptyList()
        val json = getJson(
            url = "${server.baseUrl}/library/sections/${section.id}/all?X-Plex-Container-Start=0&X-Plex-Container-Size=$count",
            token = server.accessToken,
            clientId = clientId
        )
        return parseMetadataItems(json, server, limit = count)
    }

    fun loadBrowseItems(browseKey: String, count: Int = 120): List<PlexMediaItem> {
        val session = createSession()
        val server = session.server
        val clientId = session.clientId
        val separator = if (browseKey.contains("?")) "&" else "?"
        val json = getJson(
            url = "${server.baseUrl}$browseKey${separator}X-Plex-Container-Start=0&X-Plex-Container-Size=$count",
            token = server.accessToken,
            clientId = clientId
        )
        return parseMetadataItems(json, server, limit = count)
    }

    private fun createSession(): PlexSession {
        val accountToken = authStore.getAuthToken()
            ?: throw IllegalStateException("Missing Plex auth token.")
        val clientId = authStore.getOrCreateClientIdentifier()
        val server = discoverServers(accountToken, clientId).firstOrNull()
            ?: throw IllegalStateException("No Plex Media Server was found for this account.")
        return PlexSession(server = server, clientId = clientId)
    }

    private fun discoverServers(accountToken: String, clientId: String): List<PlexServer> {
        val request = Request.Builder()
            .url("https://plex.tv/api/resources?includeHttps=1")
            .header("Accept", "application/xml")
            .header(HEADER_CLIENT_ID, clientId)
            .header(HEADER_PRODUCT, PRODUCT_NAME)
            .header(HEADER_TOKEN, accountToken)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw PlexAuthenticationException("Your Plex session expired.")
                }
                throw IllegalStateException("Server discovery failed with HTTP ${response.code}")
            }
            return parseResourcesXml(payload)
        }
    }

    private fun fetchSections(server: PlexServer, clientId: String): List<PlexLibrarySection> {
        val json = getJson(
            url = "${server.baseUrl}/library/sections",
            token = server.accessToken,
            clientId = clientId
        )
        val directories = json.getJSONObject("MediaContainer").optJSONArray("Directory").orEmpty()
        return buildList {
            for (index in 0 until directories.length()) {
                val item = directories.getJSONObject(index)
                val type = item.optString("type")
                if (type == "movie" || type == "show") {
                    add(
                        PlexLibrarySection(
                            id = item.getString("key"),
                            title = item.optString("title", "Library"),
                            type = type
                        )
                    )
                }
            }
        }
    }

    private fun fetchContinueWatching(
        server: PlexServer,
        clientId: String,
        count: Int
    ): List<PlexMediaItem> {
        val json = getJson(
            url = "${server.baseUrl}/hubs/continueWatching?count=$count",
            token = server.accessToken,
            clientId = clientId
        )
        val hubs = json.getJSONObject("MediaContainer").optJSONArray("Hub").orEmpty()
        val items = mutableListOf<PlexMediaItem>()
        for (index in 0 until hubs.length()) {
            val hub = hubs.getJSONObject(index)
            val metadata = hub.optJSONArray("Metadata").orEmpty()
            for (metadataIndex in 0 until metadata.length()) {
                val item = metadata.getJSONObject(metadataIndex)
                items += item.toMediaItem(server)
                if (items.size >= count) {
                    return items.distinctBy { it.id }.take(count)
                }
            }
        }
        return items.distinctBy { it.id }.take(count)
    }

    private fun fetchRecentlyAddedMovies(
        server: PlexServer,
        clientId: String,
        sectionId: String,
        count: Int
    ): List<PlexMediaItem> {
        val json = getJson(
            url = "${server.baseUrl}/library/sections/$sectionId/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=$count",
            token = server.accessToken,
            clientId = clientId
        )
        return parseMetadataItems(json, server, limit = count)
    }

    private fun fetchRecentlyAddedShows(
        server: PlexServer,
        clientId: String,
        sectionId: String,
        count: Int
    ): List<PlexMediaItem> {
        val json = getJson(
            url = "${server.baseUrl}/library/sections/$sectionId/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=$count",
            token = server.accessToken,
            clientId = clientId
        )
        return parseMetadataItems(json, server, limit = count)
    }

    private fun parseMetadataItems(
        json: JSONObject,
        server: PlexServer,
        limit: Int
    ): List<PlexMediaItem> {
        val metadata = json.getJSONObject("MediaContainer").optJSONArray("Metadata").orEmpty()
        return buildList {
            for (index in 0 until minOf(metadata.length(), limit)) {
                add(metadata.getJSONObject(index).toMediaItem(server))
            }
        }
    }

    private fun getJson(url: String, token: String, clientId: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header(HEADER_CLIENT_ID, clientId)
            .header(HEADER_PRODUCT, PRODUCT_NAME)
            .header(HEADER_TOKEN, token)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw PlexAuthenticationException("Your Plex session expired.")
                }
                throw IllegalStateException("Plex request failed with HTTP ${response.code}")
            }
            return JSONObject(payload)
        }
    }

    private fun parseResourcesXml(xml: String): List<PlexServer> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val servers = mutableListOf<PlexServer>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Device") {
                val provides = parser.getAttributeValue(null, "provides").orEmpty()
                if (provides.contains("server")) {
                    val name = parser.getAttributeValue(null, "name").orEmpty()
                    val accessToken = parser.getAttributeValue(null, "accessToken").orEmpty()
                    val owned = parser.getAttributeValue(null, "owned") == "1"
                    val connections = mutableListOf<PlexConnection>()

                    var innerEvent = parser.next()
                    while (!(innerEvent == XmlPullParser.END_TAG && parser.name == "Device")) {
                        if (innerEvent == XmlPullParser.START_TAG && parser.name == "Connection") {
                            connections += PlexConnection(
                                uri = parser.getAttributeValue(null, "uri").orEmpty(),
                                local = parser.getAttributeValue(null, "local") == "1",
                                protocol = parser.getAttributeValue(null, "protocol").orEmpty()
                            )
                        }
                        innerEvent = parser.next()
                    }

                    val bestConnection = connections
                        .sortedWith(
                            compareByDescending<PlexConnection> { it.protocol == "https" }
                                .thenByDescending { it.local }
                        )
                        .firstOrNull()

                    if (name.isNotBlank() && accessToken.isNotBlank() && bestConnection != null) {
                        servers += PlexServer(
                            name = name,
                            baseUrl = bestConnection.uri,
                            accessToken = accessToken,
                            owned = owned
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        return servers.sortedWith(compareByDescending<PlexServer> { it.owned }.thenBy { it.name })
    }

    private fun JSONObject.toMediaItem(server: PlexServer): PlexMediaItem {
        val ratingKey = optString("ratingKey").ifBlank { optString("key") }
        val type = optString("type")
        val title = when (type) {
            "episode" -> optString("grandparentTitle").ifBlank { optString("title") }
            else -> optString("title", "Untitled")
        }
        val meta = when (type) {
            "episode" -> {
                val season = optInt("parentIndex", 0)
                val episode = optInt("index", 0)
                "S${season}E${episode} • ${optString("title")}"
            }
            "movie" -> "Movie${optInt("year").takeIf { it > 0 }?.let { " • $it" }.orEmpty()}"
            "show" -> "TV Show${optInt("childCount").takeIf { it > 0 }?.let { " • $it episodes" }.orEmpty()}"
            else -> type.replaceFirstChar { it.uppercase() }
        }
        val badge = when {
            optLong("viewOffset", 0L) > 0L -> "Continue"
            type == "episode" -> "Episode"
            type == "movie" -> "Movie"
            else -> "New"
        }
        val progressLabel = when {
            optLong("viewOffset", 0L) > 0L && optLong("duration", 0L) > 0L -> {
                val remainingMs = optLong("duration") - optLong("viewOffset")
                formatRemaining(remainingMs)
            }
            optLong("addedAt", 0L) > 0L -> "Added ${relativeAddedLabel(optLong("addedAt"))}"
            else -> ""
        }
        val description = optString("summary").ifBlank {
            optString("tagline").ifBlank { "Available from your Plex library." }
        }
        val posterUrl = buildImageUrl(server, imagePathForPoster(type))
        val backdropUrl = buildImageUrl(
            server,
            optString("art")
                .ifBlank { optString("grandparentArt") }
                .ifBlank { optString("thumb") }
                .ifBlank { optString("grandparentThumb") }
        )
        val streamUrl = buildStreamUrl(server)
        val fallbackStreamUrl = buildTranscodeStreamUrl(server)
        val playbackMedia = streamUrl?.let {
            PlexPlaybackMedia(
                id = ratingKey,
                title = title,
                subtitle = meta,
                streamUrl = it,
                fallbackStreamUrl = fallbackStreamUrl,
                requestHeaders = mapOf(HEADER_TOKEN to server.accessToken)
            )
        }

        return PlexMediaItem(
            id = ratingKey,
            itemType = type,
            title = title,
            meta = meta,
            badge = badge,
            progressLabel = progressLabel,
            description = description,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            accent = accentFor(type, title),
            browseKey = browseKeyFor(type),
            playbackMedia = playbackMedia
        )
    }

    private fun JSONObject.browseKeyFor(type: String): String? {
        return when (type) {
            "show", "season" -> optString("key").ifBlank { null }
            else -> null
        }
    }

    private fun JSONObject.imagePathForPoster(type: String): String {
        return when (type) {
            "episode" -> optString("thumb")
                .ifBlank { optString("grandparentThumb") }
                .ifBlank { optString("parentThumb") }
            "show" -> optString("thumb")
                .ifBlank { optString("art") }
            else -> optString("thumb")
                .ifBlank { optString("art") }
        }
    }

    private fun JSONObject.buildStreamUrl(server: PlexServer): String? {
        val mediaArray = optJSONArray("Media").orEmpty()
        if (mediaArray.length() == 0) return null
        val partArray = mediaArray.optJSONObject(0)?.optJSONArray("Part").orEmpty()
        if (partArray.length() == 0) return null
        val partKey = partArray.optJSONObject(0)?.optString("key").orEmpty()
        if (partKey.isBlank()) return null
        val separator = if (partKey.contains("?")) "&" else "?"
        return "${server.baseUrl}$partKey${separator}X-Plex-Token=${encode(server.accessToken)}"
    }

    private fun JSONObject.buildTranscodeStreamUrl(server: PlexServer): String? {
        val ratingKey = optString("ratingKey").ifBlank { return null }
        val metadataPath = "/library/metadata/$ratingKey"
        val queryParams = linkedMapOf(
            "path" to metadataPath,
            "mediaIndex" to "0",
            "partIndex" to "0",
            "protocol" to "hls",
            "offset" to "0",
            "fastSeek" to "1",
            "directPlay" to "0",
            "directStream" to "1",
            "copyts" to "1",
            "session" to "metaplexplay-$ratingKey",
            "X-Plex-Token" to server.accessToken
        )
        val query = queryParams.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "${server.baseUrl}/video/:/transcode/universal/start.m3u8?$query"
    }

    private fun buildImageUrl(server: PlexServer, path: String): String? {
        if (path.isBlank()) return null
        val separator = if (path.contains("?")) "&" else "?"
        return "${server.baseUrl}$path${separator}X-Plex-Token=${encode(server.accessToken)}"
    }

    private fun accentFor(type: String, seed: String): Color {
        val palette = when (type) {
            "movie" -> listOf(0xFFCE7C2A, 0xFF5E6B80, 0xFFB25C33, 0xFF5F5E7A)
            "episode", "show" -> listOf(0xFF47614D, 0xFF44656A, 0xFF756244, 0xFF60715B)
            else -> listOf(0xFF4A4E62, 0xFF5B4D47, 0xFF82694E)
        }
        val value = palette[kotlin.math.abs(seed.hashCode()) % palette.size]
        return Color(value.toLong())
    }

    private fun formatRemaining(remainingMs: Long): String {
        val totalMinutes = (remainingMs / 60_000).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m left"
        } else {
            "${minutes}m left"
        }
    }

    private fun relativeAddedLabel(addedAtSeconds: Long): String {
        val diffSeconds = (System.currentTimeMillis() / 1000) - addedAtSeconds
        val days = diffSeconds / 86_400
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "this week"
            else -> "recently"
        }
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val PRODUCT_NAME = "MetaPlexPlay"
        private const val HEADER_CLIENT_ID = "X-Plex-Client-Identifier"
        private const val HEADER_PRODUCT = "X-Plex-Product"
        private const val HEADER_TOKEN = "X-Plex-Token"
    }
}

private data class PlexServer(
    val name: String,
    val baseUrl: String,
    val accessToken: String,
    val owned: Boolean
)

private data class PlexConnection(
    val uri: String,
    val local: Boolean,
    val protocol: String
)

private data class PlexSession(
    val server: PlexServer,
    val clientId: String
)

private data class PlexLibrarySection(
    val id: String,
    val title: String,
    val type: String
)

class PlexAuthenticationException(message: String) : IllegalStateException(message)
