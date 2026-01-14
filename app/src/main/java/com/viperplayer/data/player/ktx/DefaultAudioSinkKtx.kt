package com.viperplayer.data.player.ktx

import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor

private val audioProcessorChainField by lazy {
    DefaultAudioSink::class.java.getDeclaredField("audioProcessorChain").apply {
        isAccessible = true
    }
}

private val toFloatPcmAudioProcessorField by lazy {
    DefaultAudioSink::class.java.getDeclaredField("toFloatPcmAudioProcessor").apply {
        isAccessible = true
    }
}

val DefaultAudioSink.audioProcessorChain: AudioProcessorChain
    get() = audioProcessorChainField.get(this) as AudioProcessorChain

val DefaultAudioSink.toFloatPcmAudioProcessor: ToFloatPcmAudioProcessor
    get() = toFloatPcmAudioProcessorField.get(this) as ToFloatPcmAudioProcessor