package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
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

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
        process(outputBuffer.array(), outputBuffer.remaining())
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

