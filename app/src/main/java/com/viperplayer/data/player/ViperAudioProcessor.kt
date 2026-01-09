package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
class ViperAudioProcessor @Inject constructor() : AudioProcessor {
    
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false
    
    /**
     * Whether ViPER processing is enabled.
     * When false, the processor will pass through audio unchanged.
     * When true, the processor will apply effects (when implemented).
     */
    @Volatile
    var enabled: Boolean = false
        private set
    
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        
        // For pass-through, output format is the same as input format
        outputAudioFormat = inputAudioFormat
        
        // Initialize output buffer if needed
        if (outputBuffer == null) {
            val bufferSize = BUFFER_SIZE_MS * inputAudioFormat.sampleRate / 1000 * inputAudioFormat.channelCount * BYTES_PER_SAMPLE
            outputBuffer = ByteBuffer.allocateDirect(bufferSize)
                .order(ByteOrder.nativeOrder())
        }
        
        return outputAudioFormat
    }
    
    override fun isActive(): Boolean {
        // Always active if we have a valid input format
        // The enabled flag controls whether we process or pass through
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET
    }
    
    /**
     * Enable or disable ViPER processing.
     * This is called by the manager to control the processor state.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    override fun queueInput(inputBuffer: ByteBuffer) {
        // For pass-through, we need to copy the input buffer to our output buffer
        // since the input buffer is owned by ExoPlayer and may be reused
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        
        var output = outputBuffer ?: return
        
        // Compact any remaining data from previous output
        if (output.hasRemaining()) {
            output.compact()
        } else {
            output.clear()
        }
        
        // Ensure output buffer has enough capacity
        val availableSpace = output.remaining()
        if (availableSpace < remaining) {
            // Reallocate if needed (shouldn't happen often)
            val newCapacity = (output.capacity() + remaining) * 2
            val newBuffer = ByteBuffer.allocateDirect(newCapacity)
                .order(ByteOrder.nativeOrder())
            // Copy any remaining data
            if (output.position() > 0) {
                output.flip()
                newBuffer.put(output)
            }
            outputBuffer = newBuffer
            output = newBuffer
        }
        
        // Copy input to output (pass-through)
        // Create a duplicate to avoid modifying the input buffer's position
        val inputDuplicate = inputBuffer.duplicate()
        output.put(inputDuplicate)
        
        // Prepare output buffer for reading
        output.flip()
    }
    
    override fun queueEndOfStream() {
        inputEnded = true
    }
    
    override fun getOutput(): ByteBuffer {
        val output = outputBuffer ?: return EMPTY_BUFFER
        // Return the output buffer if it has remaining data
        // ExoPlayer will consume it by advancing the position
        // After consumption, we'll compact the buffer in the next queueInput call
        return if (output.hasRemaining()) {
            output
        } else {
            EMPTY_BUFFER
        }
    }
    
    override fun isEnded(): Boolean {
        return inputEnded && (outputBuffer?.hasRemaining() != true)
    }
    
    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer?.clear()
        inputEnded = false
    }
    
    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputBuffer = null
    }
    
    companion object {
        // Buffer size in milliseconds
        private const val BUFFER_SIZE_MS = 100
        
        // Bytes per sample (assuming 32-bit float)
        private const val BYTES_PER_SAMPLE = 4
        
        // Empty buffer for edge cases
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}

