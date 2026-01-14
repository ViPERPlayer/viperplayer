package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Base ViPER audio processor that processes PCM audio data.
 * Currently implements a pass-through processor that simply forwards audio data unchanged.
 * 
 * This class can be extended to add actual audio processing effects.
 */
@OptIn(UnstableApi::class)
@Singleton
class ViperAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Timber.d("ViperAudioProcessor.onConfigure called: $inputAudioFormat")
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
        
//        // Get the buffer data for native processing
//        // For direct buffers, we need to copy to an array or use GetDirectBufferAddress
//        val remaining = outputBuffer.remaining()
//        if (remaining > 0) {
//            val bufferArray = ByteArray(remaining)
//            val position = outputBuffer.position()
//            outputBuffer.get(bufferArray)
//            outputBuffer.position(position) // Reset position for ExoPlayer
//            process(bufferArray, remaining)
//        }
    }

    override fun onReset() {
        super.onReset()
    }

    fun setEnabled(enabled: Boolean) {

    }

    private external fun process(buffer: ByteArray, size: Int)

    init {
        System.loadLibrary("viper")
    }
}

