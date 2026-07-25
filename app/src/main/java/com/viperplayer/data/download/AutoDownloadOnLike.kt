package com.viperplayer.data.download

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song

/**
 * Pure decision for "auto-download on like": should [song] be enqueued for offline download when it
 * has just been liked?
 *
 * True only when ALL of:
 * - [settingEnabled] — the user opted in to auto-download-on-like.
 * - [song] is non-null (the liked row is actually persisted/resolvable).
 * - the song comes from a remote plugin ([MediaId.Plugin]) — the on-device local library
 *   ([MediaId.Local]) is already "offline", and virtual [MediaId.Radio] ids are never downloadable.
 * - the song is not already downloaded ([Song.isDownloaded] is false) — no point re-downloading.
 *
 * Android-free so it can be unit-tested directly. The [DownloadManager.enqueue] in-flight guard is a
 * second, complementary line of defense; this decider keeps already-downloaded and local/radio
 * sources from ever reaching the queue in the first place.
 */
fun shouldAutoDownloadOnLike(song: Song?, settingEnabled: Boolean): Boolean =
    settingEnabled &&
        song != null &&
        song.id is MediaId.Plugin &&
        !song.isDownloaded
