package com.viperplayer.domain.model

/**
 * A "Listen together" (Jam) session.
 *
 * MOCK-BACKED for now — there is no realtime jam backend, so these are produced locally by
 * [com.viperplayer.domain.repository.ListenTogetherRepository]. The shape mirrors what a real backend
 * would return (host, participants, share code + invite URL), so adding a real service later is a
 * repository-only change; nothing in the UI needs to move.
 */
data class ListenSession(
    val code: String,
    val inviteUrl: String,
    val hostName: String,
    val isHost: Boolean,
    val participants: List<SessionParticipant>,
) {
    /** "You + N listening" — N is everyone except the local user. */
    val othersCount: Int get() = (participants.size - 1).coerceAtLeast(0)
}

data class SessionParticipant(
    val id: String,
    val name: String,
    val isSelf: Boolean = false,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}
