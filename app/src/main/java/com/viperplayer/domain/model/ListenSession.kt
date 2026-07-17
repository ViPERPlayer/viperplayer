package com.viperplayer.domain.model

/**
 * A "Listen together" (Jam) session.
 *
 * Produced by [com.viperplayer.domain.repository.ListenTogetherRepository] — either mapped from the
 * ViPER backend session snapshot (real) or synthesised locally (mock). The shape mirrors what the
 * backend returns (host, participants, share code + invite URL), so the UI is backend-agnostic.
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
