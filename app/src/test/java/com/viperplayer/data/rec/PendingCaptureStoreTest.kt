package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure-JVM tests for [PendingCaptureStore] (Path C): the bounded on-disk mel cache. Uses a temp folder
 * via [PendingCaptureStore.forDirectory] — no Android `Context`. Base64 filename keys + the self-
 * describing binary format are exercised on the JVM (the store uses `java.util.Base64`, not `android.util`).
 */
class PendingCaptureStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun mel(seed: Float, size: Int = 8) = FloatArray(size) { seed + it }

    @Test
    fun putThenReadAllRoundTripsTheMel() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        store.put("t=plugin&p=testsource&s=song1", mel(0.5f))

        val all = store.readAll()
        assertEquals(1, all.size)
        assertEquals("t=plugin&p=testsource&s=song1", all.single().mediaId)
        assertTrue(mel(0.5f).contentEquals(all.single().mel))
    }

    @Test
    fun putReplacesTheEntryForTheSameMediaId() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        store.put("id-a", mel(1f))
        store.put("id-a", mel(9f)) // same id -> replace, not duplicate
        assertEquals(1, store.size())
        assertTrue(mel(9f).contentEquals(store.readAll().single().mel))
    }

    @Test
    fun emptyMelIsNotStored() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        store.put("id", FloatArray(0))
        assertEquals(0, store.size())
    }

    @Test
    fun removeDeletesTheEntry() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        store.put("id-x", mel(2f))
        store.put("id-y", mel(3f))
        store.remove("id-x")
        val ids = store.readAll().map { it.mediaId }
        assertEquals(listOf("id-y"), ids)
    }

    @Test
    fun clearRemovesEverything() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        store.put("a", mel(1f))
        store.put("b", mel(2f))
        store.clear()
        assertEquals(0, store.size())
        assertTrue(store.readAll().isEmpty())
    }

    /**
     * Stamp a strictly-increasing mod-time on each id's cached file (in [order], oldest first) so
     * eviction order is deterministic regardless of the host filesystem's mod-time granularity. The
     * mediaId is decoded from each file's inline header (magic, version, idLen, idBytes, …).
     */
    private fun stampModTimes(dir: java.io.File, order: List<String>) {
        val idToFile = dir.listFiles()!!.filter { it.isFile }.associateBy { file ->
            val buf = java.nio.ByteBuffer.wrap(file.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.int; buf.int // magic, version
            val idLen = buf.int
            val idBytes = ByteArray(idLen); buf.get(idBytes)
            String(idBytes, Charsets.UTF_8)
        }
        order.forEachIndexed { idx, id -> idToFile[id]?.setLastModified(1000L * (idx + 1)) }
    }

    @Test
    fun evictsOldestPastMaxEntries() {
        val dir = tmp.newFolder("caps")
        // Insert with a HUGE cap first (no eviction), stamp deterministic ages, then a bounded store
        // evicts on the next put.
        val loose = PendingCaptureStore.forDirectory(dir, maxEntries = 100, maxBytes = Long.MAX_VALUE)
        for (i in 1..4) loose.put("id-$i", mel(i.toFloat()))
        stampModTimes(dir, listOf("id-1", "id-2", "id-3", "id-4")) // id-1 oldest

        // A store with maxEntries=3 evicts down to the cap (id-1, id-2 oldest) when the 5th entry lands.
        val store = PendingCaptureStore.forDirectory(dir, maxEntries = 3, maxBytes = Long.MAX_VALUE)
        store.put("id-5", mel(5f))
        val remaining = store.readAll().map { it.mediaId }.toSet()
        assertEquals(3, remaining.size)
        assertTrue("id-1 should be evicted", "id-1" !in remaining)
        assertTrue("id-2 should be evicted", "id-2" !in remaining)
        assertTrue("id-5 (newest) should survive", "id-5" in remaining)
    }

    @Test
    fun evictionNeverDropsTheJustWrittenEntryEvenIfItSortsOldest() {
        val dir = tmp.newFolder("caps")
        val store = PendingCaptureStore.forDirectory(dir, maxEntries = 1, maxBytes = Long.MAX_VALUE)
        store.put("existing", mel(1f))
        // Make the existing file look FAR NEWER than the next write, so a naive oldest-first eviction would
        // pick the just-written "fresh" file as the victim. The `keep` guard must protect it regardless.
        dir.listFiles()!!.forEach { it.setLastModified(99_999_999_999_999L) }
        store.put("fresh", mel(2f)) // maxEntries=1 -> exactly one must be evicted
        val ids = store.readAll().map { it.mediaId }.toSet()
        assertEquals(setOf("fresh"), ids)
    }

    @Test
    fun evictsPastMaxBytes() {
        val dir = tmp.newFolder("caps")
        // Each mel is 64 floats -> a few hundred bytes with the header. Cap bytes low to force eviction.
        val store = PendingCaptureStore.forDirectory(dir, maxEntries = 100, maxBytes = 400)
        for (i in 1..6) store.put("id-$i", mel(i.toFloat(), size = 64))
        val totalBytes = dir.listFiles()!!.sumOf { it.length() }
        assertTrue("total $totalBytes should be <= 400 after eviction", totalBytes <= 400)
    }

    @Test
    fun staleCaptureFormatVersionIsIgnoredOnRead() {
        val dir = tmp.newFolder("caps")
        PendingCaptureStore.forDirectory(dir, captureFormatVersion = 1).put("id", mel(1f))
        // A store expecting a DIFFERENT capture-format version must not read the v1 file.
        val v2 = PendingCaptureStore.forDirectory(dir, captureFormatVersion = 2)
        assertTrue(v2.readAll().isEmpty())
    }

    @Test
    fun readAllOnEmptyDirIsEmpty() {
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("caps"))
        assertTrue(store.readAll().isEmpty())
        assertNull(store.readAll().firstOrNull())
    }
}
