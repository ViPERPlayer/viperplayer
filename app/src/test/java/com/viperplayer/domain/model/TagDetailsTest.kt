package com.viperplayer.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure presence helpers that drive which sections the Tag-details screen shows. */
class TagDetailsTest {

    @Test
    fun `empty details report nothing present`() {
        val d = TagDetails.EMPTY
        assertFalse(d.hasAnyTextTag())
        assertFalse(d.hasAnyTechnical())
        assertFalse(d.hasAny())
    }

    @Test
    fun `a single text tag counts as a text tag only`() {
        val d = TagDetails(title = "Song")
        assertTrue(d.hasAnyTextTag())
        assertFalse(d.hasAnyTechnical())
        assertTrue(d.hasAny())
    }

    @Test
    fun `artists list alone counts as a text tag`() {
        val d = TagDetails(artists = listOf("A", "B"))
        assertTrue(d.hasAnyTextTag())
    }

    @Test
    fun `a technical property alone counts as technical only`() {
        val d = TagDetails(sampleRate = 44100)
        assertFalse(d.hasAnyTextTag())
        assertTrue(d.hasAnyTechnical())
        assertTrue(d.hasAny())
    }

    @Test
    fun `blank artists list does not count as a text tag`() {
        val d = TagDetails(container = "FLAC")
        assertFalse(d.hasAnyTextTag())
        assertTrue(d.hasAnyTechnical())
    }
}
