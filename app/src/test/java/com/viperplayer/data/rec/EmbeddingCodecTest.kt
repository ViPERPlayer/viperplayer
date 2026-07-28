package com.viperplayer.data.rec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM tests for the embedding byte codec ([embeddingToBytes] / [bytesToEmbedding]) and the
 * [ClapModel] handshake constants. The codec is the exact at-rest layout of the `songs.audioEmbedding`
 * BLOB, so a round-trip mismatch would silently corrupt every stored/compared vector.
 */
class EmbeddingCodecTest {

    @Test
    fun clapModelConstants() {
        assertEquals(512, ClapModel.EMBEDDING_DIM)
        assertEquals(48_000, ClapModel.TARGET_SAMPLE_RATE)
        assertEquals(480_000, ClapModel.CLIP_SAMPLES)
        assertEquals(2048, ClapModel.EMBEDDING_BYTES)
        assertTrue("model version must be non-blank (it is the vector-space handshake key)",
            ClapModel.MODEL_VERSION.isNotBlank())
    }

    @Test
    fun roundTripPreservesVector() {
        val v = FloatArray(ClapModel.EMBEDDING_DIM) { (it - 256) * 0.013f }
        val bytes = embeddingToBytes(v)
        assertEquals(ClapModel.EMBEDDING_BYTES, bytes.size)
        val back = bytesToEmbedding(bytes)
        assertArrayEquals(v, back, 0.0f) // float32 -> LE bytes -> float32 is exact
    }

    @Test
    fun bytesAreLittleEndianFloat32() {
        // First float = 1.0f -> IEEE-754 LE bytes 00 00 80 3F.
        val v = FloatArray(ClapModel.EMBEDDING_DIM)
        v[0] = 1.0f
        val bytes = embeddingToBytes(v)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        assertEquals(0x80.toByte(), bytes[2])
        assertEquals(0x3F.toByte(), bytes[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodingWrongDimThrows() {
        embeddingToBytes(FloatArray(511))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodingWrongSizeThrows() {
        bytesToEmbedding(ByteArray(2047))
    }

    @Test
    fun l2NormalizeProducesUnitVector() {
        val v = FloatArray(ClapModel.EMBEDDING_DIM) { (it + 1).toFloat() }
        val u = l2Normalize(v)
        var n = 0.0
        for (x in u) n += x.toDouble() * x
        assertEquals(1.0, sqrt(n), 1e-5)
    }
}
