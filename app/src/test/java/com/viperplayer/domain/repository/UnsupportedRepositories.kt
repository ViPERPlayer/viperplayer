package com.viperplayer.domain.repository

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SongWithStats
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.model.StatPeriod
import com.viperplayer.domain.model.StatsSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Throwing base fakes for the large repository interfaces, for focused ViewModel unit tests: subclass
 * one via interface delegation (`class MyFake : XRepository by UnsupportedXRepository()`) and override
 * only the handful of members the test exercises. Every un-overridden member throws, so a test that
 * accidentally depends on more surface fails loudly instead of silently passing.
 */
open class UnsupportedMediaLibraryRepository : MediaLibraryRepository {
    override fun getArtist(mediaId: MediaId): Flow<Artist?> = TODO()
    override fun getAllLikedArtists(): Flow<List<Artist>> = TODO()
    override fun getAllSavedArtists(): Flow<List<Artist>> = TODO()
    override suspend fun saveArtist(artist: ArtistDetail) = TODO()
    override suspend fun setArtistLiked(mediaId: MediaId, isLiked: Boolean) = TODO()
    override suspend fun setArtistSaved(mediaId: MediaId, isSaved: Boolean) = TODO()
    override fun getAlbum(mediaId: MediaId): Flow<Album?> = TODO()
    override fun getAllLikedAlbums(): Flow<List<Album>> = TODO()
    override fun getAllSavedAlbums(): Flow<List<Album>> = TODO()
    override fun getAllDownloadedAlbums(): Flow<List<Album>> = TODO()
    override suspend fun saveAlbum(album: Album) = TODO()
    override suspend fun setAlbumLiked(mediaId: MediaId, isLiked: Boolean) = TODO()
    override suspend fun setAlbumSaved(mediaId: MediaId, isSaved: Boolean) = TODO()
    override suspend fun setAlbumDownloaded(mediaId: MediaId, isDownloaded: Boolean) = TODO()
    override fun getSong(mediaId: MediaId): Flow<Song?> = TODO()
    override fun getAllLikedSongs(): Flow<List<Song>> = TODO()
    override fun getAllSavedSongs(): Flow<List<Song>> = TODO()
    override fun getSongsByDateAdded(): Flow<List<Song>> = TODO()
    override fun getAllDownloadedSongs(): Flow<List<Song>> = TODO()
    override fun likedSongCount(): Flow<Int> = TODO()
    override fun downloadedSongCount(): Flow<Int> = TODO()
    override suspend fun saveSong(song: Song) = TODO()
    override suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean) = TODO()
    override suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean) = TODO()
    override suspend fun setSongDownloaded(
        mediaId: MediaId,
        isDownloaded: Boolean,
        downloadPath: String?) = TODO()
    override suspend fun incrementSongPlayCount(mediaId: MediaId) = TODO()
    override suspend fun recordListenedTime(mediaId: MediaId, durationListenedMs: Long) = TODO()
    override fun getMostPlayedSongs(period: StatPeriod, limit: Int): Flow<List<SongWithStats>> = TODO()
    override fun getStatsSummary(period: StatPeriod): Flow<StatsSummary> = TODO()
    override fun getHistory(limit: Int): Flow<List<HistoryEntry>> = TODO()
    override suspend fun getFirstPlayTimestamp(): Long? = TODO()
    override suspend fun clearHistory() = TODO()
    override suspend fun pruneHistory(cutoffMillis: Long) = TODO()
    override fun getPlaylist(mediaId: MediaId): Flow<Playlist?> = TODO()
    override fun getAllLikedPlaylists(): Flow<List<Playlist>> = TODO()
    override fun getAllSavedPlaylists(): Flow<List<Playlist>> = TODO()
    override fun getLikedSongsPlaylist(): Flow<Playlist> = TODO()
    override fun getLocalPlaylists(): Flow<List<Playlist>> = TODO()
    override suspend fun createLocalPlaylist(name: String): MediaId = TODO()
    override suspend fun renamePlaylist(mediaId: MediaId, newName: String) = TODO()
    override suspend fun addSongToPlaylist(playlistId: MediaId, song: Song) = TODO()
    override suspend fun removeSongFromPlaylist(playlistId: MediaId, songId: MediaId) = TODO()
    override suspend fun reorderPlaylistSongs(playlistId: MediaId, fromIndex: Int, toIndex: Int) = TODO()
    override suspend fun savePlaylist(playlist: Playlist) = TODO()
    override suspend fun setPlaylistLiked(mediaId: MediaId, isLiked: Boolean) = TODO()
    override suspend fun setPlaylistSaved(mediaId: MediaId, isSaved: Boolean) = TODO()
    override suspend fun isPlaylistSaved(mediaId: MediaId): Boolean = TODO()
    override suspend fun setPlaylistDownloaded(mediaId: MediaId, isDownloaded: Boolean) = TODO()
    override suspend fun scanLocalFiles() = TODO()
    override suspend fun exportPlaylistToM3u(mediaId: MediaId): String? = TODO()
    override suspend fun importPlaylistFromM3u(playlistName: String, content: String): M3uImportResult = TODO()
}

open class UnsupportedPluginRepository : PluginRepository {
    override val discoveredPlugins: Flow<List<PluginInfo>> get() = TODO()
    override val connectedPlugins: Flow<List<Plugin>> get() = TODO()
    override val disabledPlugins: Flow<Set<String>> get() = TODO()
    override val pendingActions: Flow<List<PluginPendingAction>> get() = TODO()
    override suspend fun refreshPluginStatuses() = TODO()
    override suspend fun refreshPlugins() = TODO()
    override suspend fun enablePlugin(pluginId: String): Result<Unit> = TODO()
    override suspend fun disablePlugin(pluginId: String): Result<Unit> = TODO()
    override suspend fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> = TODO()
    override suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int): Result<SearchResult> = TODO()
    override suspend fun getHomeContent(): Result<List<Pair<String, HomeContent>>> = TODO()
    override suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int): Result<PagedResult<BrowseCategory>> = TODO()
    override suspend fun getCategoryContents(
        pluginId: String,
        categoryId: String,
        cursor: String?,
        limit: Int): Result<SearchResult> = TODO()
    override suspend fun filterSection(
        pluginId: String,
        sectionId: String,
        filterKey: String
    ): Result<SearchResult> = TODO()
    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int): Result<PagedResult<Song>> = TODO()
    override suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int): Result<PagedResult<Album>> = TODO()
    override suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int): Result<PagedResult<Artist>> = TODO()
    override suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int): Result<PagedResult<Playlist>> = TODO()
    override suspend fun getLibrarySongs(
        pluginId: String,
        cursor: String?,
        limit: Int): Result<PagedResult<Song>> = TODO()
    override suspend fun getLibraryAlbums(
        pluginId: String,
        cursor: String?,
        limit: Int): Result<PagedResult<Album>> = TODO()
    override suspend fun getLibraryArtists(
        pluginId: String,
        cursor: String?,
        limit: Int): Result<PagedResult<Artist>> = TODO()
    override suspend fun getLibraryPlaylists(
        pluginId: String,
        cursor: String?,
        limit: Int): Result<PagedResult<Playlist>> = TODO()
    override suspend fun getSong(mediaId: MediaId): Result<Song> = TODO()
    override suspend fun getAlbum(mediaId: MediaId): Result<Album> = TODO()
    override suspend fun getArtist(mediaId: MediaId): Result<ArtistDetail> = TODO()
    override suspend fun getPlaylist(mediaId: MediaId): Result<Playlist> = TODO()
    override suspend fun getLyrics(song: Song): Result<Lyrics?> = TODO()
    override suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int): Result<PagedResult<Song>> = TODO()
    override suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String?,
        limit: Int): Result<PagedResult<Album>> = TODO()
    override suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String?,
        limit: Int): Result<PagedResult<Song>> = TODO()
    override suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> = TODO()
}

open class UnsupportedPlayerRepository : PlayerRepository {
    override val playbackState: StateFlow<PlaybackInfo> get() = TODO()
    override val currentSong: StateFlow<Song?> get() = TODO()
    override val duration: StateFlow<Long> get() = TODO()
    override suspend fun getCurrentPosition(): Long = TODO()
    override suspend fun getBufferedPosition(): Long = TODO()
    override val queue: Flow<List<Song>> get() = TODO()
    override suspend fun play(song: Song, context: PlaybackContext?) = TODO()
    override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) = TODO()
    override suspend fun pause() = TODO()
    override suspend fun resume() = TODO()
    override suspend fun togglePlayPause() = TODO()
    override suspend fun stop() = TODO()
    override suspend fun skipToNext() = TODO()
    override suspend fun skipToPrevious() = TODO()
    override suspend fun playFromQueue(index: Int) = TODO()
    override suspend fun seekTo(positionMs: Long) = TODO()
    override suspend fun addToQueue(song: Song) = TODO()
    override suspend fun playNext(song: Song) = TODO()
    override suspend fun duplicateInQueue(index: Int) = TODO()
    override suspend fun removeFromQueue(index: Int) = TODO()
    override suspend fun reorderQueue(fromIndex: Int, toIndex: Int) = TODO()
    override suspend fun clearQueue() = TODO()
    override suspend fun setShuffle(enabled: Boolean) = TODO()
    override suspend fun setRepeatMode(mode: RepeatMode) = TODO()
    override val playbackSpeed: StateFlow<Float> get() = TODO()
    override val playbackPitch: StateFlow<Float> get() = TODO()
    override suspend fun setPlaybackSpeed(speed: Float) = TODO()
    override suspend fun setPlaybackPitch(pitch: Float) = TODO()
    override suspend fun getAudioFormat(): AudioFormat? = TODO()
    override suspend fun playRemote(
        mediaId: MediaId,
        title: String,
        artist: String,
        artworkUrl: String,
        playWhenReady: Boolean,
    ) = TODO()
    override fun setFollowerMode(enabled: Boolean) = TODO()
}

open class UnsupportedSettingsRepository : SettingsRepository {
    override val dynamicThemeMode: Flow<DynamicThemeMode> get() = TODO()
    override suspend fun setDynamicThemeMode(mode: DynamicThemeMode) = TODO()
    override val themeMode: Flow<ThemeMode> get() = TODO()
    override suspend fun setThemeMode(mode: ThemeMode) = TODO()
    override val pureBlack: Flow<Boolean> get() = TODO()
    override suspend fun setPureBlack(enabled: Boolean) = TODO()
    override val accentColor: Flow<Int?> get() = TODO()
    override suspend fun setAccentColor(argb: Int?) = TODO()
    override val audioQuality: Flow<AudioQuality> get() = TODO()
    override suspend fun setAudioQuality(quality: AudioQuality) = TODO()
    override val historyDuration: Flow<HistoryDuration> get() = TODO()
    override suspend fun setHistoryDuration(duration: HistoryDuration) = TODO()
    override val skipSilence: Flow<Boolean> get() = TODO()
    override suspend fun setSkipSilence(enabled: Boolean) = TODO()
    override val replayGainEnabled: Flow<Boolean> get() = TODO()
    override suspend fun setReplayGainEnabled(enabled: Boolean) = TODO()
    override val replayGainPreampDb: Flow<Float> get() = TODO()
    override suspend fun setReplayGainPreampDb(preampDb: Float) = TODO()
    override val replayGainAlbumMode: Flow<Boolean> get() = TODO()
    override suspend fun setReplayGainAlbumMode(enabled: Boolean) = TODO()
    override val replayGainMode: Flow<ReplayGainMode> get() = TODO()
    override suspend fun setReplayGainMode(mode: ReplayGainMode) = TODO()
    override val replayGainUntaggedPreampDb: Flow<Float> get() = TODO()
    override suspend fun setReplayGainUntaggedPreampDb(preampDb: Float) = TODO()
    override val replayGainDrcEnabled: Flow<Boolean> get() = TODO()
    override suspend fun setReplayGainDrcEnabled(enabled: Boolean) = TODO()
    override val replayGainPostAmpDb: Flow<Float> get() = TODO()
    override suspend fun setReplayGainPostAmpDb(postAmpDb: Float) = TODO()
    override val dspBypass: Flow<Boolean> get() = TODO()
    override suspend fun setDspBypass(enabled: Boolean) = TODO()
    override val autoLoadMore: Flow<Boolean> get() = TODO()
    override suspend fun setAutoLoadMore(enabled: Boolean) = TODO()
    override val crossfadeDurationSeconds: Flow<Int> get() = TODO()
    override suspend fun setCrossfadeDurationSeconds(seconds: Int) = TODO()
    override val showExplicitContent: Flow<Boolean> get() = TODO()
    override suspend fun setShowExplicitContent(enabled: Boolean) = TODO()
    override val maxSongCacheSize: Flow<Long> get() = TODO()
    override suspend fun setMaxSongCacheSize(size: Long) = TODO()
    override val maxImageCacheSize: Flow<Long> get() = TODO()
    override suspend fun setMaxImageCacheSize(size: Long) = TODO()
    override val lyricsFontSize: Flow<LyricsFontSize> get() = TODO()
    override suspend fun setLyricsFontSize(size: LyricsFontSize) = TODO()
    override val lyricsAlignment: Flow<LyricsAlignment> get() = TODO()
    override suspend fun setLyricsAlignment(alignment: LyricsAlignment) = TODO()
    override val lyricsFontWeight: Flow<LyricsFontWeight> get() = TODO()
    override suspend fun setLyricsFontWeight(weight: LyricsFontWeight) = TODO()
    override val lyricsHighlightColor: Flow<LyricsHighlightColor> get() = TODO()
    override suspend fun setLyricsHighlightColor(color: LyricsHighlightColor) = TODO()
    override val lyricsActiveLineScale: Flow<Float> get() = TODO()
    override suspend fun setLyricsActiveLineScale(scale: Float) = TODO()
    override val lyricsAutoScroll: Flow<Boolean> get() = TODO()
    override suspend fun setLyricsAutoScroll(enabled: Boolean) = TODO()
    override val lyricsTapToSeek: Flow<Boolean> get() = TODO()
    override suspend fun setLyricsTapToSeek(enabled: Boolean) = TODO()
    override val lyricsDimInactiveLines: Flow<Boolean> get() = TODO()
    override suspend fun setLyricsDimInactiveLines(enabled: Boolean) = TODO()
    override val lyricsShowTranslationByDefault: Flow<Boolean> get() = TODO()
    override suspend fun setLyricsShowTranslationByDefault(enabled: Boolean) = TODO()
    override val lyricsShowRomanizationByDefault: Flow<Boolean> get() = TODO()
    override suspend fun setLyricsShowRomanizationByDefault(enabled: Boolean) = TODO()
    override val lyricsSettings: Flow<LyricsSettings> get() = TODO()
    override fun sortOrder(view: SortView): Flow<SortOrder> = TODO()
    override suspend fun setSortOrder(view: SortView, order: SortOrder) = TODO()
    override val libraryTabsConfig: Flow<LibraryTabsConfig> get() = TODO()
    override suspend fun setLibraryTabsConfig(config: LibraryTabsConfig) = TODO()
    override val homeSignInCardDismissed: Flow<Boolean> get() = TODO()
    override suspend fun setHomeSignInCardDismissed(dismissed: Boolean) = TODO()
}
