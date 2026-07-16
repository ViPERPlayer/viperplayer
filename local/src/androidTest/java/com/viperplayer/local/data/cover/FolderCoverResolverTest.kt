package com.viperplayer.local.data.cover

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Instrumentation test for [FolderCoverResolver]: exercises the real Android layer — file listing
 * plus a bounds-only [android.graphics.BitmapFactory] decode — feeding the pure scorer, using
 * actual PNG files written to a temp folder.
 */
@RunWith(AndroidJUnit4::class)
class FolderCoverResolverTest {

    private lateinit var dir: File
    private val resolver = FolderCoverResolver()

    @Before
    fun setUp() {
        dir = File.createTempFile("cover_test", "").let {
            it.delete()
            it.mkdirs()
            it
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Writes a solid-colour square PNG of the given side length into the test folder. */
    private fun writePng(name: String, side: Int) {
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.GRAY)
        FileOutputStream(File(dir, name)).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }

    @Test
    fun picksCoverOverBackAndTiny() {
        writePng("back.png", 600)
        writePng("thumb.png", 40) // tiny -> rejected
        writePng("cover.png", 500)

        val uri = resolver.resolve(dir.absolutePath)
        assertEquals(File(dir, "cover.png").toUri(), uri)
    }

    @Test
    fun ignoresNonImageFiles() {
        File(dir, "notes.txt").writeText("not an image")
        File(dir, "album.nfo").writeText("info")
        writePng("front.png", 500)

        val uri = resolver.resolve(dir.absolutePath)
        assertEquals(File(dir, "front.png").toUri(), uri)
    }

    @Test
    fun emptyFolderYieldsNull() {
        assertNull(resolver.resolve(dir.absolutePath))
    }

    @Test
    fun missingFolderYieldsNull() {
        assertNull(resolver.resolve("/does/not/exist/anywhere"))
        assertNull(resolver.resolve(null))
    }

    @Test
    fun cachesRepeatedLookups() {
        writePng("cover.png", 400)
        val first = resolver.resolve(dir.absolutePath)
        assertTrue(first.toString().endsWith("cover.png"))
        // Delete the file; a cached resolver should still return the memoised result.
        File(dir, "cover.png").delete()
        assertEquals(first, resolver.resolve(dir.absolutePath))
    }
}
