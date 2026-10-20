package com.viperplayer.portable.android

import android.content.Context
import com.viperplayer.plugin.author.SourceProvider
import com.viperplayer.plugin.author.StreamResponse
import com.viperplayer.plugin.model.Album
import com.viperplayer.plugin.model.Artist
import com.viperplayer.plugin.model.BrowseCategory
import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.HomeContent
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.Page
import com.viperplayer.plugin.model.PageRequest
import com.viperplayer.plugin.model.Playlist
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import com.viperplayer.plugin.model.SearchRequest
import com.viperplayer.plugin.model.SearchResult
import com.viperplayer.plugin.model.SearchSuggestions
import com.viperplayer.plugin.model.Song
import com.viperplayer.plugin.model.SourceCapabilities
import com.viperplayer.plugin.model.StreamSource
import com.viperplayer.plugin.model.UrlStream
import com.viperplayer.plugin.protocol.Codec
import com.viperplayer.portable.PluginPackage
import com.viperplayer.portable.PortableManifest
import com.viperplayer.portable.PortablePluginException
import com.viperplayer.portable.QuickJsPlugin
import com.viperplayer.portable.UrlConnectionPluginHttp
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Reuses the existing SDK/Binder transport; the actual provider executes JavaScript. */
class PortableSourceProvider(private val runtime: QuickJsPlugin) : SourceProvider {
    val manifest: PortableManifest get() = runtime.plugin.manifest

    val capabilities: SourceCapabilities get() = manifest.methods.let { methods ->
        SourceCapabilities(
            search = "search" in methods,
            searchSuggestions = "getSearchSuggestions" in methods,
            browse = "getBrowseCategories" in methods && "getCategoryContents" in methods,
            library = false,
            libraryWrite = false,
            playlists = "getPlaylist" in methods && "getPlaylistSongs" in methods,
            artistDetails = "getArtist" in methods,
            albumDetails = "getAlbum" in methods,
            home = "getHome" in methods,
            radio = "getRelatedSongs" in methods,
            canSeek = true,
            supportsOffline = false,
        )
    }

    override suspend fun search(request: SearchRequest): SearchResult =
        call("search", Codec.json.encodeToJsonElement(request))
    override suspend fun getSearchSuggestions(query: String): SearchSuggestions =
        call("getSearchSuggestions", args("query" to JsonPrimitive(query)))
    override suspend fun getSong(id: String): Song = call("getSong", id(id))
    override suspend fun getAlbum(id: String): Album = call("getAlbum", id(id))
    override suspend fun getArtist(id: String): Artist = call("getArtist", id(id))
    override suspend fun getPlaylist(id: String): Playlist = call("getPlaylist", id(id))
    override suspend fun getArtistSongs(artistId: String, page: PageRequest): Page<Song> =
        call("getArtistSongs", idPage(artistId, page))
    override suspend fun getArtistAlbums(artistId: String, page: PageRequest): Page<Album> =
        call("getArtistAlbums", idPage(artistId, page))
    override suspend fun getPlaylistSongs(playlistId: String, page: PageRequest): Page<Song> =
        call("getPlaylistSongs", idPage(playlistId, page))
    override suspend fun getRelatedSongs(songId: String): Page<Song> = call("getRelatedSongs", id(songId))
    override suspend fun getBrowseCategories(page: PageRequest): Page<BrowseCategory> =
        call("getBrowseCategories", args("page" to Codec.json.encodeToJsonElement(page)))
    override suspend fun getCategoryContents(categoryId: String, page: PageRequest): SearchResult =
        call("getCategoryContents", idPage(categoryId, page))
    override suspend fun getHome(): HomeContent = call("getHome", JsonObject(emptyMap()))

    override suspend fun resolveStream(songId: String, type: MediaType, maxBitrateKbps: Int?): StreamResponse {
        val source = call<StreamSource>("resolveStream", args(
            "id" to JsonPrimitive(songId),
            "type" to JsonPrimitive(type.name),
            "maxBitrateKbps" to (maxBitrateKbps?.let(::JsonPrimitive) ?: JsonNull),
        ))
        return when (source) {
            is UrlStream -> StreamResponse.url(
                https(source.url), source.mimeType, source.headers, source.replayGainDb,
                source.peakAmplitude, source.albumReplayGainDb, source.albumPeakAmplitude,
            )
            is HlsStream -> StreamResponse.hls(
                https(source.url), source.drm, source.replayGainDb, source.peakAmplitude,
                source.albumReplayGainDb, source.albumPeakAmplitude,
            )
            is DashStream -> StreamResponse.dash(
                source.manifest, source.manifestUrl?.let(::https), source.drm, source.replayGainDb,
                source.peakAmplitude, source.albumReplayGainDb, source.albumPeakAmplitude,
            )
            else -> throw PluginException(PluginErrorCode.UNSUPPORTED, "Portable plugins support URL, HLS and DASH streams")
        }
    }

    private suspend inline fun <reified T> call(method: String, args: JsonElement): T = try {
        Codec.json.decodeFromJsonElement(runtime.invoke(method, args))
    } catch (error: CancellationException) {
        throw error
    } catch (error: PortablePluginException) {
        val code = when (error.code) {
            "NETWORK", "TIMEOUT" -> PluginErrorCode.NETWORK
            "AUTH_REQUIRED" -> PluginErrorCode.AUTH_REQUIRED
            "NOT_FOUND" -> PluginErrorCode.NOT_FOUND
            "NOT_AVAILABLE" -> PluginErrorCode.NOT_AVAILABLE
            "RATE_LIMITED" -> PluginErrorCode.RATE_LIMITED
            "INVALID_REQUEST", "PERMISSION_DENIED" -> PluginErrorCode.INVALID_REQUEST
            "UNSUPPORTED" -> PluginErrorCode.UNSUPPORTED
            "PLAYBACK" -> PluginErrorCode.PLAYBACK
            else -> PluginErrorCode.INTERNAL
        }
        throw PluginException(code, error.message, error.retryable, error)
    }

    private fun https(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri?.scheme != "https" || uri.host == null || uri.rawUserInfo != null) {
            throw PluginException(PluginErrorCode.INVALID_REQUEST, "Portable stream URL must use HTTPS")
        }
        return value
    }

    private fun id(value: String) = args("id" to JsonPrimitive(value))
    private fun idPage(value: String, page: PageRequest) = args(
        "id" to JsonPrimitive(value), "page" to Codec.json.encodeToJsonElement(page),
    )
    private fun args(vararg values: Pair<String, JsonElement>) = JsonObject(mapOf(*values))

    companion object {
        /** Bundled source only. Directory is supplied by application code, never a plugin. */
        fun fromAssets(context: Context, directory: String): PortableSourceProvider {
            require(directory.matches(Regex("[a-zA-Z0-9_/-]+")) && ".." !in directory)
            val manifestText = context.assets.open("$directory/manifest.json").use { input ->
                val bytes = input.readBytes()
                require(bytes.size <= 16_384) { "Manifest is too large" }
                bytes.toString(Charsets.UTF_8)
            }
            val manifest = PluginPackage.json.decodeFromString<PortableManifest>(manifestText)
            manifest.validate()
            val source = context.assets.open("$directory/${manifest.entry}").bufferedReader().use { it.readText() }
            return PortableSourceProvider(QuickJsPlugin(PluginPackage(manifest, source), UrlConnectionPluginHttp(),
                browserFactory = AndroidPluginBrowser(context)))
        }
    }
}
