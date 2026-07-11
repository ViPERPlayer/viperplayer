package com.viperplayer.data.backup

import com.viperplayer.data.local.dao.AlbumDao
import com.viperplayer.data.local.dao.ArtistDao
import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.PlaylistDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.ViperPresetDao
import com.viperplayer.data.local.entity.PlaylistEntity
import com.viperplayer.data.local.entity.PlaylistSongCrossRef
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.ViperDdcCoeffEntity
import com.viperplayer.data.local.entity.ViperPresetEntity
import com.viperplayer.data.local.entity.relation.ViperPresetWithCoeffs
import com.viperplayer.domain.model.BackupBundle
import com.viperplayer.domain.model.BackupDdcCoeff
import com.viperplayer.domain.model.BackupPlaylist
import com.viperplayer.domain.model.BackupSettings
import com.viperplayer.domain.model.BackupSong
import com.viperplayer.domain.model.BackupViperPreset
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers the user's library + settings + ViPER presets into a [BackupBundle] and restores it back.
 *
 * All work happens at the DAO / repository level so both export and restore are resilient: a
 * malformed or absent field on restore is wrapped in [runCatching] and skipped rather than crashing
 * the whole operation.
 *
 * Limitations:
 * - Only DB-level data is captured. On-disk binaries (downloaded audio, cached artwork, ViPER
 *   DDC/IR files) are NOT copied — only their references. File paths in presets
 *   ([BackupViperPreset.viperDdcSelectedFile] / [convolverImpulseResponse]) may be stale after a
 *   restore onto a different device.
 * - Restore recreates presets as fresh rows keyed by name (auto-generated id / device association
 *   are not preserved).
 */
@Singleton
class BackupManager @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val crossRefDao: CrossRefDao,
    private val viperPresetDao: ViperPresetDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun serialize(bundle: BackupBundle): String = json.encodeToString(bundle)

    fun deserialize(jsonString: String): BackupBundle = json.decodeFromString(jsonString)

    /** Gather liked + saved songs, playlists, settings and presets into a bundle. */
    suspend fun export(): BackupBundle {
        val likedEntities = songDao.getAllLiked().first()
        val savedEntities = songDao.getAllSaved().first()

        // Union liked + saved, deduped by row id.
        val songEntities = (likedEntities + savedEntities).distinctBy { it.id }
        val backupSongs = songEntities.map { it.toBackupSong() }

        val likedSongIds = likedEntities.map { it.mediaId().toString() }
        val savedSongIds = savedEntities.map { it.mediaId().toString() }

        val playlists = playlistDao.getAll().first().map { playlist ->
            val songIds = runCatching { crossRefDao.getSongIdsForPlaylist(playlist.id) }
                .getOrDefault(emptyList())
            val playlistSongs = songIds.mapNotNull { songId ->
                runCatching { songDao.getByIdSync(songId)?.toBackupSong() }.getOrNull()
            }
            BackupPlaylist(
                pluginId = playlist.pluginId,
                sourceId = playlist.sourceId,
                name = playlist.name,
                description = playlist.description,
                artworkUrl = playlist.artworkUrl,
                ownerName = playlist.ownerName,
                isLiked = playlist.isLiked,
                isSaved = playlist.isSaved,
                songs = playlistSongs,
            )
        }

        val presets = viperPresetDao.getAllPresets().first().map { it.toBackupPreset() }

        return BackupBundle(
            version = BackupBundle.CURRENT_VERSION,
            exportedAtMs = System.currentTimeMillis(),
            likedSongIds = likedSongIds,
            savedSongIds = savedSongIds,
            songs = backupSongs,
            playlists = playlists,
            settings = exportSettings(),
            viperPresets = presets,
        )
    }

    /**
     * Write the bundle back into the database + settings. Each item is restored independently and
     * failures are swallowed so a single bad row can't abort the whole restore.
     */
    suspend fun restore(bundle: BackupBundle) {
        // Songs first so playlist cross-refs can resolve them.
        bundle.songs.forEach { song ->
            runCatching { upsertBackupSong(song) }
        }

        // Playlists + their track ordering.
        bundle.playlists.forEach { playlist ->
            runCatching { restorePlaylist(playlist) }
        }

        runCatching { restoreSettings(bundle.settings) }

        bundle.viperPresets.forEach { preset ->
            runCatching { restorePreset(preset) }
        }
    }

    // ---- Songs -------------------------------------------------------------------------------

    /** Upsert a song row and apply its liked/saved flags. Returns the local row id, or null. */
    private suspend fun upsertBackupSong(song: BackupSong): Long? {
        if (song.sourceId.isBlank()) return null
        val existing = songDao.getByMediaId(song.pluginId, song.sourceId)
        val songId = if (existing != null) {
            existing.id
        } else {
            val entity = SongEntity(
                pluginId = song.pluginId,
                sourceId = song.sourceId,
                title = song.title,
                durationMs = song.durationMs,
                artworkUrl = song.artworkUrl,
                isExplicit = song.isExplicit,
                isVideo = song.isVideo,
                isLiked = song.isLiked,
                isSaved = song.isSaved,
            )
            val inserted = songDao.insert(entity)
            if (inserted == -1L) {
                songDao.getByMediaId(song.pluginId, song.sourceId)?.id
            } else {
                inserted
            }
        }
        // Ensure flags are applied even for a pre-existing row.
        if (song.isLiked) songDao.updateLiked(song.pluginId, song.sourceId, true)
        if (song.isSaved) songDao.updateSaved(song.pluginId, song.sourceId, true)
        return songId
    }

    // ---- Playlists ---------------------------------------------------------------------------

    private suspend fun restorePlaylist(playlist: BackupPlaylist) {
        if (playlist.sourceId.isBlank()) return
        val existing = playlistDao.getByMediaId(playlist.pluginId, playlist.sourceId)
        val entity = PlaylistEntity(
            id = existing?.id ?: 0L,
            pluginId = playlist.pluginId,
            sourceId = playlist.sourceId,
            name = playlist.name,
            description = playlist.description,
            artworkUrl = playlist.artworkUrl,
            ownerName = playlist.ownerName,
            songCount = playlist.songs.size,
            isLiked = playlist.isLiked,
            isSaved = playlist.isSaved,
        )
        val playlistId = playlistDao.insert(entity)

        crossRefDao.deletePlaylistSongs(playlistId)
        playlist.songs.forEachIndexed { index, song ->
            runCatching {
                val songId = upsertBackupSong(song) ?: return@runCatching
                crossRefDao.insertPlaylistSong(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = songId,
                        position = index,
                    )
                )
            }
        }
    }

    // ---- Settings ----------------------------------------------------------------------------

    private suspend fun exportSettings(): BackupSettings = BackupSettings(
        themeMode = settingsRepository.themeMode.first().name,
        dynamicThemeMode = settingsRepository.dynamicThemeMode.first().name,
        pureBlack = settingsRepository.pureBlack.first(),
        audioQuality = settingsRepository.audioQuality.first().name,
        historyDuration = settingsRepository.historyDuration.first().name,
        skipSilence = settingsRepository.skipSilence.first(),
        replayGainEnabled = settingsRepository.replayGainEnabled.first(),
        replayGainPreampDb = settingsRepository.replayGainPreampDb.first(),
        replayGainAlbumMode = settingsRepository.replayGainAlbumMode.first(),
        dspBypass = settingsRepository.dspBypass.first(),
        autoLoadMore = settingsRepository.autoLoadMore.first(),
        showExplicitContent = settingsRepository.showExplicitContent.first(),
        maxSongCacheSize = settingsRepository.maxSongCacheSize.first(),
        maxImageCacheSize = settingsRepository.maxImageCacheSize.first(),
    )

    private suspend fun restoreSettings(settings: BackupSettings) {
        settings.themeMode?.let { name ->
            runCatching { settingsRepository.setThemeMode(ThemeMode.valueOf(name)) }
        }
        settings.dynamicThemeMode?.let { name ->
            runCatching { settingsRepository.setDynamicThemeMode(DynamicThemeMode.valueOf(name)) }
        }
        settings.pureBlack?.let { runCatching { settingsRepository.setPureBlack(it) } }
        settings.audioQuality?.let { name ->
            runCatching { settingsRepository.setAudioQuality(AudioQuality.valueOf(name)) }
        }
        settings.historyDuration?.let { name ->
            runCatching { settingsRepository.setHistoryDuration(HistoryDuration.valueOf(name)) }
        }
        settings.skipSilence?.let { runCatching { settingsRepository.setSkipSilence(it) } }
        settings.replayGainEnabled?.let {
            runCatching { settingsRepository.setReplayGainEnabled(it) }
        }
        settings.replayGainPreampDb?.let {
            runCatching { settingsRepository.setReplayGainPreampDb(it) }
        }
        settings.replayGainAlbumMode?.let {
            runCatching { settingsRepository.setReplayGainAlbumMode(it) }
        }
        settings.dspBypass?.let { runCatching { settingsRepository.setDspBypass(it) } }
        settings.autoLoadMore?.let { runCatching { settingsRepository.setAutoLoadMore(it) } }
        settings.showExplicitContent?.let {
            runCatching { settingsRepository.setShowExplicitContent(it) }
        }
        settings.maxSongCacheSize?.let {
            runCatching { settingsRepository.setMaxSongCacheSize(it) }
        }
        settings.maxImageCacheSize?.let {
            runCatching { settingsRepository.setMaxImageCacheSize(it) }
        }
    }

    // ---- ViPER presets -----------------------------------------------------------------------

    private suspend fun restorePreset(preset: BackupViperPreset) {
        val deviceType = runCatching {
            DynamicSystemDeviceType.valueOf(preset.dynamicSystemDeviceType)
        }.getOrDefault(DynamicSystemDeviceType.entries.first())

        val entity = ViperPresetEntity(
            name = preset.name,
            deviceId = preset.deviceId,
            enabled = preset.enabled,
            masterLimiterOutputGain = preset.masterLimiterOutputGain,
            masterLimiterOutputPan = preset.masterLimiterOutputPan,
            masterLimiterThresholdLimit = preset.masterLimiterThresholdLimit,
            spectrumExtensionEnabled = preset.spectrumExtensionEnabled,
            spectrumExtensionStrength = preset.spectrumExtensionStrength,
            fieldSurroundEnabled = preset.fieldSurroundEnabled,
            fieldSurroundStrength = preset.fieldSurroundStrength,
            fieldSurroundMidImageStrength = preset.fieldSurroundMidImageStrength,
            differentialSurroundEnabled = preset.differentialSurroundEnabled,
            differentialSurroundDelay = preset.differentialSurroundDelay,
            dynamicSystemEnabled = preset.dynamicSystemEnabled,
            dynamicSystemDeviceType = deviceType,
            dynamicSystemBassStrength = preset.dynamicSystemBassStrength,
            tubeSimulatorEnabled = preset.tubeSimulatorEnabled,
            viperBassEnabled = preset.viperBassEnabled,
            viperBassMode = preset.viperBassMode,
            viperBassFrequency = preset.viperBassFrequency,
            viperBassGain = preset.viperBassGain,
            viperClarityEnabled = preset.viperClarityEnabled,
            viperClarityMode = preset.viperClarityMode,
            viperClarityGain = preset.viperClarityGain,
            auditorySystemProtectionEnabled = preset.auditorySystemProtectionEnabled,
            auditorySystemProtectionLevel = preset.auditorySystemProtectionLevel,
            analogXEnabled = preset.analogXEnabled,
            analogXLevel = preset.analogXLevel,
            speakerOptimizationEnabled = preset.speakerOptimizationEnabled,
            viperDdcEnabled = preset.viperDdcEnabled,
            viperDdcSelectedFile = preset.viperDdcSelectedFile,
            iirEqualizerEnabled = preset.iirEqualizerEnabled,
            iirEqualizerBandCount = preset.iirEqualizerBandCount,
            iirEqualizerPreset = preset.iirEqualizerPreset,
            iirEqualizerBandGains = preset.iirEqualizerBandGains,
            playbackGainEnabled = preset.playbackGainEnabled,
            playbackGainStrength = preset.playbackGainStrength,
            playbackGainMaxGain = preset.playbackGainMaxGain,
            playbackGainOutputThreshold = preset.playbackGainOutputThreshold,
            convolverEnabled = preset.convolverEnabled,
            convolverImpulseResponse = preset.convolverImpulseResponse,
            convolverCrossChannel = preset.convolverCrossChannel,
            fetCompressorEnabled = preset.fetCompressorEnabled,
            fetCompressorThreshold = preset.fetCompressorThreshold,
            fetCompressorRatio = preset.fetCompressorRatio,
            fetCompressorKnee = preset.fetCompressorKnee,
            fetCompressorAutoKnee = preset.fetCompressorAutoKnee,
            fetCompressorGain = preset.fetCompressorGain,
            fetCompressorAutoGain = preset.fetCompressorAutoGain,
            fetCompressorAttack = preset.fetCompressorAttack,
            fetCompressorAutoAttack = preset.fetCompressorAutoAttack,
            fetCompressorRelease = preset.fetCompressorRelease,
            fetCompressorAutoRelease = preset.fetCompressorAutoRelease,
            fetCompressorKneeMulti = preset.fetCompressorKneeMulti,
            fetCompressorMaxAttack = preset.fetCompressorMaxAttack,
            fetCompressorMaxRelease = preset.fetCompressorMaxRelease,
            fetCompressorCrest = preset.fetCompressorCrest,
            fetCompressorAdapt = preset.fetCompressorAdapt,
            fetCompressorNoClip = preset.fetCompressorNoClip,
            headphoneSurroundEnabled = preset.headphoneSurroundEnabled,
            headphoneSurroundLevel = preset.headphoneSurroundLevel,
            reverberationEnabled = preset.reverberationEnabled,
            reverberationRoomSize = preset.reverberationRoomSize,
            reverberationWidth = preset.reverberationWidth,
            reverberationDamp = preset.reverberationDamp,
            reverberationWet = preset.reverberationWet,
            reverberationDry = preset.reverberationDry,
        )
        val presetId = viperPresetDao.insertPreset(entity)
        if (preset.ddcCoeffs.isNotEmpty()) {
            val coeffs = preset.ddcCoeffs.map { c ->
                ViperDdcCoeffEntity(
                    presetId = presetId,
                    sampleRate = c.sampleRate,
                    index = c.index,
                    value = c.value,
                )
            }
            viperPresetDao.insertCoeffs(coeffs)
        }
    }

    // ---- Mappers -----------------------------------------------------------------------------

    private fun SongEntity.mediaId() = MediaId(pluginId, sourceId)

    private suspend fun SongEntity.toBackupSong(): BackupSong {
        val artistNames = runCatching {
            crossRefDao.getArtistIdsForSong(id).mapNotNull { artistId ->
                artistDao.getByIdSync(artistId)?.name
            }
        }.getOrDefault(emptyList())
        val albumName = albumId?.let { aId ->
            runCatching { albumDao.getByIdSync(aId)?.name }.getOrNull()
        }
        return BackupSong(
            pluginId = pluginId,
            sourceId = sourceId,
            title = title,
            artistNames = artistNames,
            albumName = albumName,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            isExplicit = isExplicit,
            isVideo = isVideo,
            isLiked = isLiked,
            isSaved = isSaved,
        )
    }

    private fun ViperPresetWithCoeffs.toBackupPreset(): BackupViperPreset {
        val p = preset
        return BackupViperPreset(
            name = p.name,
            deviceId = p.deviceId,
            enabled = p.enabled,
            masterLimiterOutputGain = p.masterLimiterOutputGain,
            masterLimiterOutputPan = p.masterLimiterOutputPan,
            masterLimiterThresholdLimit = p.masterLimiterThresholdLimit,
            spectrumExtensionEnabled = p.spectrumExtensionEnabled,
            spectrumExtensionStrength = p.spectrumExtensionStrength,
            fieldSurroundEnabled = p.fieldSurroundEnabled,
            fieldSurroundStrength = p.fieldSurroundStrength,
            fieldSurroundMidImageStrength = p.fieldSurroundMidImageStrength,
            differentialSurroundEnabled = p.differentialSurroundEnabled,
            differentialSurroundDelay = p.differentialSurroundDelay,
            dynamicSystemEnabled = p.dynamicSystemEnabled,
            dynamicSystemDeviceType = p.dynamicSystemDeviceType.name,
            dynamicSystemBassStrength = p.dynamicSystemBassStrength,
            tubeSimulatorEnabled = p.tubeSimulatorEnabled,
            viperBassEnabled = p.viperBassEnabled,
            viperBassMode = p.viperBassMode,
            viperBassFrequency = p.viperBassFrequency,
            viperBassGain = p.viperBassGain,
            viperClarityEnabled = p.viperClarityEnabled,
            viperClarityMode = p.viperClarityMode,
            viperClarityGain = p.viperClarityGain,
            auditorySystemProtectionEnabled = p.auditorySystemProtectionEnabled,
            auditorySystemProtectionLevel = p.auditorySystemProtectionLevel,
            analogXEnabled = p.analogXEnabled,
            analogXLevel = p.analogXLevel,
            speakerOptimizationEnabled = p.speakerOptimizationEnabled,
            viperDdcEnabled = p.viperDdcEnabled,
            viperDdcSelectedFile = p.viperDdcSelectedFile,
            iirEqualizerEnabled = p.iirEqualizerEnabled,
            iirEqualizerBandCount = p.iirEqualizerBandCount,
            iirEqualizerPreset = p.iirEqualizerPreset,
            iirEqualizerBandGains = p.iirEqualizerBandGains,
            playbackGainEnabled = p.playbackGainEnabled,
            playbackGainStrength = p.playbackGainStrength,
            playbackGainMaxGain = p.playbackGainMaxGain,
            playbackGainOutputThreshold = p.playbackGainOutputThreshold,
            convolverEnabled = p.convolverEnabled,
            convolverImpulseResponse = p.convolverImpulseResponse,
            convolverCrossChannel = p.convolverCrossChannel,
            fetCompressorEnabled = p.fetCompressorEnabled,
            fetCompressorThreshold = p.fetCompressorThreshold,
            fetCompressorRatio = p.fetCompressorRatio,
            fetCompressorKnee = p.fetCompressorKnee,
            fetCompressorAutoKnee = p.fetCompressorAutoKnee,
            fetCompressorGain = p.fetCompressorGain,
            fetCompressorAutoGain = p.fetCompressorAutoGain,
            fetCompressorAttack = p.fetCompressorAttack,
            fetCompressorAutoAttack = p.fetCompressorAutoAttack,
            fetCompressorRelease = p.fetCompressorRelease,
            fetCompressorAutoRelease = p.fetCompressorAutoRelease,
            fetCompressorKneeMulti = p.fetCompressorKneeMulti,
            fetCompressorMaxAttack = p.fetCompressorMaxAttack,
            fetCompressorMaxRelease = p.fetCompressorMaxRelease,
            fetCompressorCrest = p.fetCompressorCrest,
            fetCompressorAdapt = p.fetCompressorAdapt,
            fetCompressorNoClip = p.fetCompressorNoClip,
            headphoneSurroundEnabled = p.headphoneSurroundEnabled,
            headphoneSurroundLevel = p.headphoneSurroundLevel,
            reverberationEnabled = p.reverberationEnabled,
            reverberationRoomSize = p.reverberationRoomSize,
            reverberationWidth = p.reverberationWidth,
            reverberationDamp = p.reverberationDamp,
            reverberationWet = p.reverberationWet,
            reverberationDry = p.reverberationDry,
            ddcCoeffs = ddcCoeffs.map { c ->
                BackupDdcCoeff(sampleRate = c.sampleRate, index = c.index, value = c.value)
            },
        )
    }
}
