package com.viperplayer.data.sync.push

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.data.local.ViperPlayerDatabase
import com.viperplayer.data.local.dao.OutboxDao
import com.viperplayer.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the library-push [OutboxDao] against a real in-memory Room database. Covers
 * insert + autoincrement id (the coalescer's seq), FIFO ordering, per-plugin filtering, the pending
 * count flow, delete/attempts/status updates, and the SKIPPED->re-arm path.
 */
@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {

    private lateinit var db: ViperPlayerDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ViperPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.outboxDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(pluginId: String, type: String = "setLiked", json: String = "{}") =
        OutboxEntity(pluginId = pluginId, type = type, payloadJson = json)

    @Test
    fun insert_assignsIncreasingIds_andFifoOrder() = runBlocking {
        val id1 = dao.insert(row("testsource", json = """{"a":1}"""))
        val id2 = dao.insert(row("testsource", json = """{"a":2}"""))

        assertTrue(id2 > id1)
        val pending = dao.pendingFor("testsource")
        assertEquals(listOf(id1, id2), pending.map { it.id })
    }

    @Test
    fun pendingFor_filtersByPlugin() = runBlocking {
        dao.insert(row("testsource"))
        dao.insert(row("fifthsource"))
        dao.insert(row("testsource"))

        assertEquals(2, dao.pendingFor("testsource").size)
        assertEquals(1, dao.pendingFor("fifthsource").size)
        assertEquals(setOf("testsource", "fifthsource"), dao.pluginsWithPending().toSet())
    }

    @Test
    fun deleteByIds_removesOnlyThose() = runBlocking {
        val id1 = dao.insert(row("testsource"))
        val id2 = dao.insert(row("testsource"))
        val id3 = dao.insert(row("testsource"))

        dao.deleteByIds(listOf(id1, id3))

        assertEquals(listOf(id2), dao.pendingFor("testsource").map { it.id })
    }

    @Test
    fun updateAttempts_isPersisted() = runBlocking {
        val id = dao.insert(row("testsource"))
        dao.updateAttempts(id, 3)
        assertEquals(3, dao.pendingFor("testsource").first { it.id == id }.attempts)
    }

    @Test
    fun skipped_entriesAreExcludedFromPending_andRearmable() = runBlocking {
        val id = dao.insert(row("testsource"))
        dao.updateStatus(id, OutboxEntity.STATUS_SKIPPED)

        assertTrue(dao.pendingFor("testsource").isEmpty())

        dao.rearmSkipped("testsource")
        assertEquals(listOf(id), dao.pendingFor("testsource").map { it.id })
    }

    @Test
    fun observePendingCount_reflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, dao.observePendingCount().first())
        val id = dao.insert(row("testsource"))
        assertEquals(1, dao.observePendingCount().first())
        dao.deleteById(id)
        assertEquals(0, dao.observePendingCount().first())
    }

    @Test
    fun deleteForPlugin_clearsThatPluginOnly() = runBlocking {
        dao.insert(row("testsource"))
        dao.insert(row("fifthsource"))
        dao.deleteForPlugin("testsource")
        assertTrue(dao.pendingFor("testsource").isEmpty())
        assertEquals(1, dao.pendingFor("fifthsource").size)
    }
}
