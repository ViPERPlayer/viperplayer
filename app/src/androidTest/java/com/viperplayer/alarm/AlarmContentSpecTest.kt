package com.viperplayer.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.alarm.data.AlarmEntity
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.domain.AlarmContentSpec
import com.viperplayer.domain.model.MediaId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/**
 * Instrumented (needs android.net.Uri via [MediaId]) tests for [AlarmContentSpec] encoding and the
 * [AlarmEntity] <-> [Alarm] mapping round-trip.
 */
@RunWith(AndroidJUnit4::class)
class AlarmContentSpecTest {

    @Test
    fun shuffleAll_roundTrips() {
        assertEquals(
            AlarmContentSpec.ShuffleAll,
            AlarmContentSpec.decode(AlarmContentSpec.ShuffleAll.encode()),
        )
    }

    @Test
    fun playlist_roundTrips_withShuffleFlag() {
        val id = MediaId("local", "playlist-42")
        val spec = AlarmContentSpec.PlaylistContent(id, shuffle = true)
        val decoded = AlarmContentSpec.decode(spec.encode())
        assertEquals(spec, decoded)
    }

    @Test
    fun playlist_roundTrips_withoutShuffle() {
        val id = MediaId("local", "playlist-7")
        val spec = AlarmContentSpec.PlaylistContent(id, shuffle = false)
        assertEquals(spec, AlarmContentSpec.decode(spec.encode()))
    }

    @Test
    fun malformedContent_fallsBackToShuffleAll() {
        assertEquals(AlarmContentSpec.ShuffleAll, AlarmContentSpec.decode("garbage"))
        assertEquals(AlarmContentSpec.ShuffleAll, AlarmContentSpec.decode(null))
        assertEquals(AlarmContentSpec.ShuffleAll, AlarmContentSpec.decode("playlist:1|bad"))
    }

    @Test
    fun entityRoundTripsAllFields() {
        val alarm = Alarm(
            id = 5,
            hour = 6,
            minute = 45,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            enabled = true,
            label = "Gym",
            content = AlarmContentSpec.PlaylistContent(MediaId("local", "p1"), shuffle = true),
            volume = 0.7f,
            fadeInDurationSec = 45,
            vibrate = false,
        )
        val roundTripped = AlarmEntity.fromDomain(alarm).toDomain()
        assertEquals(alarm, roundTripped)
    }
}
