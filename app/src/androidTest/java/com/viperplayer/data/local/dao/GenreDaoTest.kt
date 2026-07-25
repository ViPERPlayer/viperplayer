package com.viperplayer.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.data.local.ViperPlayerDatabase
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.SongGenreCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the genre-browse queries on [GenreDao] against a real in-memory Room database.
 * Covers the genre → song-count aggregate (non-empty genres only, name-ordered) and the songs-for-genre
 * join that back the Library Genres tab + Genre detail screen.
 */
@RunWith(AndroidJUnit4::class)
class GenreDaoTest {

    private lateinit var db: ViperPlayerDatabase
    private lateinit var genreDao: GenreDao
    private lateinit var songDao: SongDao
    private lateinit var crossRefDao: CrossRefDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ViperPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        genreDao = db.genreDao()
        songDao = db.songDao()
        crossRefDao = db.crossRefDao()
    }

    @After
    fun tearDown() = db.close()

    private fun song(sourceId: String, title: String) =
        SongEntity(idType = "plugin", pluginId = "local", sourceId = sourceId, title = title)

    @Test
    fun getGenresWithSongCounts_countsOnlyNonEmpty_orderedByName() = runBlocking {
        val rockId = genreDao.insert(GenreEntity(name = "Rock"))
        val jazzId = genreDao.insert(GenreEntity(name = "Jazz"))
        // "Ambient" has no songs → it must not appear in the result.
        genreDao.insert(GenreEntity(name = "Ambient"))

        val s1 = songDao.insert(song("s1", "One"))
        val s2 = songDao.insert(song("s2", "Two"))
        val s3 = songDao.insert(song("s3", "Three"))

        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = s1, genreId = rockId))
        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = s2, genreId = rockId))
        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = s3, genreId = jazzId))

        val genres = genreDao.getGenresWithSongCounts().first()

        // Name-ordered (Jazz before Rock), Ambient dropped, correct counts.
        assertEquals(listOf("Jazz", "Rock"), genres.map { it.name })
        assertEquals(1, genres.first { it.name == "Jazz" }.songCount)
        assertEquals(2, genres.first { it.name == "Rock" }.songCount)
    }

    @Test
    fun getSongsForGenre_returnsOnlyThatGenresSongs_orderedByTitle() = runBlocking {
        val rockId = genreDao.insert(GenreEntity(name = "Rock"))
        val jazzId = genreDao.insert(GenreEntity(name = "Jazz"))

        val zebra = songDao.insert(song("z", "Zebra"))
        val alpha = songDao.insert(song("a", "Alpha"))
        val jazzSong = songDao.insert(song("j", "Jazzy"))

        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = zebra, genreId = rockId))
        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = alpha, genreId = rockId))
        crossRefDao.insertSongGenre(SongGenreCrossRef(songId = jazzSong, genreId = jazzId))

        val rockSongs = genreDao.getSongsForGenre(rockId).first()

        // Only the Rock songs, ordered by title (Alpha before Zebra); the Jazz song is excluded.
        assertEquals(listOf("Alpha", "Zebra"), rockSongs.map { it.title })
    }
}
