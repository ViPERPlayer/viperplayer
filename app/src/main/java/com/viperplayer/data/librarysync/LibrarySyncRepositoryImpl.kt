package com.viperplayer.data.librarysync

import com.viperplayer.domain.account.AccountApiResult
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.librarysync.LibrarySnapshot
import com.viperplayer.domain.librarysync.LibrarySyncRepository
import com.viperplayer.domain.librarysync.LibrarySyncResult
import com.viperplayer.domain.librarysync.SyncedPlaylist
import com.viperplayer.domain.librarysync.SyncedTrack
import com.viperplayer.domain.librarysync.UpsertResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LibrarySyncRepository]: runs the HTTP/JSON [LibraryApi] through the account session's
 * refresh-and-retry policy ([AccountRepository.withBackendAuth]) so a momentarily-expired access
 * token is refreshed transparently, then maps the DTOs into domain models and the transport-level
 * [AccountApiResult] into the transport-agnostic [LibrarySyncResult].
 *
 * Scope: the transport seam only — no local↔backend reconciliation/orchestration (that's a
 * follow-up). Each method issues exactly one authenticated backend call and returns its outcome.
 */
@Singleton
class LibrarySyncRepositoryImpl @Inject constructor(
    private val api: LibraryApi,
    private val accountRepository: AccountRepository,
) : LibrarySyncRepository {

    override val isConfigured: Boolean get() = api.isConfigured

    override suspend fun getLibrary(): LibrarySyncResult<LibrarySnapshot> =
        accountRepository.withBackendAuth { token -> api.getLibrary(token) }
            .mapToDomain { it.toDomain() }

    override suspend fun upsertPlaylist(
        playlist: SyncedPlaylist,
        baseRevision: Long,
    ): LibrarySyncResult<UpsertResult> =
        accountRepository.withBackendAuth { token -> api.upsertPlaylist(token, playlist.toDto(), baseRevision) }
            .mapToDomain { UpsertResult(playlist = it.playlist.toDomain(), conflict = it.conflict) }

    override suspend fun deletePlaylist(playlistId: String): LibrarySyncResult<Long> =
        accountRepository.withBackendAuth { token -> api.deletePlaylist(token, playlistId) }
            .mapToDomain { it.revision }

    override suspend fun setLike(track: SyncedTrack, liked: Boolean): LibrarySyncResult<Long> =
        accountRepository.withBackendAuth { token -> api.setLike(token, track.toDto(), liked) }
            .mapToDomain { it.revision }
}

/**
 * Maps a transport [AccountApiResult] into a domain [LibrarySyncResult], transforming a successful
 * payload with [transform]. Pure and side-effect free so the outcome mapping is unit-testable.
 */
internal fun <T, R> AccountApiResult<T>.mapToDomain(transform: (T) -> R): LibrarySyncResult<R> = when (this) {
    is AccountApiResult.Success -> LibrarySyncResult.Success(transform(value))
    is AccountApiResult.Rejected -> LibrarySyncResult.Rejected(message)
    AccountApiResult.Unauthenticated -> LibrarySyncResult.Unauthenticated
    AccountApiResult.NetworkError -> LibrarySyncResult.NetworkError
    AccountApiResult.NotConfigured -> LibrarySyncResult.NotConfigured
}
