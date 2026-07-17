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
    /**
     * Whether the local member may issue transport commands (play/pause/seek/skip/track) on the shared
     * playback. Derived from the local member's role + session permissions (mirrors the backend's
     * `canControlTransport`): HOST/CONTROLLER always; MEMBER iff `guestsCanControl`; LISTENER never. The
     * host implies true. UX gating only — the server is the real authority. Layer 2 reads this to decide
     * whether local transport events steer the session or apply locally.
     */
    val canControl: Boolean = isHost,
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
