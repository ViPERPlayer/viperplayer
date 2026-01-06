package com.viperplayer.data.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.plugin.v1.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ViperPlayerResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginDataSource: PluginDataSource
) : ResolvingDataSource.Resolver {
    private var lastResolved: Result<StreamSource> = Result.failure(Exception())
    // Store replayGain and peakAmplitude per mediaId for later retrieval
    private val replayGainCache = mutableMapOf<String, Float?>()
    private val peakAmplitudeCache = mutableMapOf<String, Float?>()

    suspend fun resolve(uri: Uri): Result<StreamSource> {
        val mediaId = try {
            MediaId.fromString(uri.toString())
        } catch (e: Exception) {
            return Result.failure(e)
        }

        Timber.d("resolve: Getting stream from plugin: $mediaId")

        return pluginDataSource.getStream(mediaId).also { result ->
            lastResolved = result
            val streamSource = result.getOrNull()
            // Store replayGain from StreamSource if available
            streamSource?.replayGain?.let { replayGain ->
                replayGainCache[mediaId.toString()] = replayGain
                Timber.d("resolve: Stored replayGain=$replayGain for $mediaId")
            }
            // Store peakAmplitude from StreamSource if available
            streamSource?.peakAmplitude?.let { peakAmplitude ->
                peakAmplitudeCache[mediaId.toString()] = peakAmplitude
                Timber.d("resolve: Stored peakAmplitude=$peakAmplitude for $mediaId")
            }
        }
    }
    
    /**
     * Gets the replayGain for a mediaId, if available from StreamSource.
     */
    fun getReplayGain(mediaId: MediaId): Float? {
        return replayGainCache[mediaId.toString()]
    }
    
    /**
     * Gets the peakAmplitude for a mediaId, if available from StreamSource.
     */
    fun getPeakAmplitude(mediaId: MediaId): Float? {
        return peakAmplitudeCache[mediaId.toString()]
    }
    
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        Timber.d("resolveDataSpec() called with uri: $${dataSpec.uri}")
        return lastResolved.getOrThrow().let { streamSource ->
            when (streamSource.type) {
                StreamSource.Type.URL -> {
                    val url = streamSource.url ?: throw IllegalArgumentException("URL is null")
                    Timber.d("resolveDataSpec: Got stream URL: $url")
                    // Return a DataSpec with the direct URL
                    dataSpec.buildUpon()
                        .setUri(url.toUri())
                        .build()
                }
                StreamSource.Type.DASH -> {
                    val xml = streamSource.dashXml ?: throw IllegalArgumentException("DASH XML is null")
                    Timber.d("resolveDataSpec: Got DASH XML, length: ${xml.length}")
                    val dashUri = saveDashXmlToFile(xml)
                    dataSpec.buildUpon()
                        .setUri(dashUri)
                        .build()
                }
                StreamSource.Type.AUDIO_STREAM -> {
                    val stream = streamSource.audioStream ?: throw IllegalArgumentException("Audio stream is null")
                    Timber.d("resolveDataSpec: Got audio stream, streamId: ${stream.streamId}")
                    // Return a DataSpec with a custom URI that our custom DataSource can handle
                    dataSpec.buildUpon()
                        .setUri("viper://stream/${stream.streamId}".toUri())
                        .build()
                }
            }
        }
    }

    /**
     * Save DASH XML content to a temporary file and return the file URI.
     */
    private fun saveDashXmlToFile(dashXml: String): Uri {
        val dashDir = context.cacheDir.resolve("dash_manifests").apply {
            if (!exists()) mkdirs()
        }

        // Generate unique filename based on current time to avoid collisions
        val filename = "manifest_${System.currentTimeMillis()}.mpd"
        val file = File(dashDir, filename)

        // Write DASH XML to file
        file.writeText(dashXml)

        Timber.d("saveDashXmlToFile: DASH XML saved to: ${file.absolutePath}")

        return file.toUri()
    }
}