package com.viperplayer.data.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.plugin.v1.StreamSource
import com.viperplayer.plugin.v1.StreamSourceTypeSealed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ViperPlayerResolver @Inject constructor(
    private val pluginDataSource: PluginDataSource,
    @param:ApplicationContext private val context: Context
) : ResolvingDataSource.Resolver {
    private var lastResolved: Pair<Uri, Result<StreamSource>> = Uri.EMPTY to Result.failure(Exception())

    suspend fun resolve(uri: Uri): Result<StreamSource> {
        val uriString = uri.toString()

        // Try to parse as MediaId (format: "pluginId=...&sourceId=...")
        val mediaId = try {
            MediaId.fromString(uriString)
        } catch (_: Exception) {
            // If parsing fails, it's not a plugin media item, return as-is
            Timber.d("ViperPlayerResolver: URI is not a plugin media item, returning as-is")
            return Result.failure(Exception("URI is not a plugin media item"))
        }

        // Get stream source from plugin (can be URL, DASH XML, or AudioStream)
        Timber.d("ViperPlayerResolver: Getting stream from plugin: $mediaId")
        val streamResult = pluginDataSource.getStream(mediaId)

        lastResolved = uri to streamResult

        return streamResult
    }
    
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
//        Exception().printStackTrace()
        return runBlocking { suspendingResolveDataSpec(dataSpec) }
    }

    private suspend fun suspendingResolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        
        Timber.d("ViperPlayerResolver: resolving URI: $uri")
        
        val (lastResolvedUri, lastResolvedResult) = lastResolved
        if (uri != lastResolvedUri) {
            Timber.d("ViperPlayerResolver: URI has changed, failing")
            return dataSpec

        }
        
        return lastResolvedResult.fold(
            onSuccess = { streamSource ->
                val streamType = StreamSourceTypeSealed.from(streamSource)
                
                when (streamType) {
                    is StreamSourceTypeSealed.Url -> {
                        Timber.d("ViperPlayerResolver: Got stream URL: ${streamType.url}")
                        // Return a DataSpec with the direct URL
                        dataSpec.buildUpon()
                            .setUri(Uri.parse(streamType.url))
                            .build()
                    }
                    is StreamSourceTypeSealed.Dash -> {
                        Timber.d("ViperPlayerResolver: Got DASH XML, length: ${streamType.xml.length}")
                        try {
                            val dashUri = saveDashXmlToFile(streamType.xml)
                            Timber.d("ViperPlayerResolver: Saved DASH XML to: $dashUri")
                            dataSpec.buildUpon()
                                .setUri(dashUri)
                                .build()
                        } catch (e: Exception) {
                            Timber.e(e, "ViperPlayerResolver: Failed to save DASH XML to file")
                            dataSpec
                        }
                    }
                    is StreamSourceTypeSealed.AudioStream -> {
                        val stream = streamType.stream
                        Timber.d("ViperPlayerResolver: Got audio stream, streamId: ${stream.streamId}")
                        // Return a DataSpec with a custom URI that our custom DataSource can handle
                        dataSpec.buildUpon()
                            .setUri(Uri.parse("viper://stream/${stream.streamId}"))
                            .build()
                    }
                }
            },
            onFailure = { error ->
                Timber.e(error, "ViperPlayerResolver: Failed to get stream")
                // Return original dataSpec on error - ExoPlayer will handle the error
                dataSpec
            }
        )
    }

    /**
     * Save DASH XML content to a temporary file and return the file URI.
     */
    private fun saveDashXmlToFile(dashXml: String): Uri {
        val cacheDir = context.cacheDir
        val dashDir = File(cacheDir, "dash_manifests").apply {
            if (!exists()) mkdirs()
        }

        // Generate unique filename based on current time to avoid collisions
        val filename = "manifest_${System.currentTimeMillis()}.mpd"
        val file = File(dashDir, filename)

        // Write DASH XML to file
        file.writeText(dashXml)

        Timber.d("ViperPlayerResolver: DASH XML saved to: ${file.absolutePath}")

        return Uri.fromFile(file)
    }
}