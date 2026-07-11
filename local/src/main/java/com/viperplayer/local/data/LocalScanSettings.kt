package com.viperplayer.local.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "local_scan_settings"
private const val KEY_BLACKLISTED_FOLDERS = "blacklisted_folders"
private const val KEY_WHITELISTED_FOLDERS = "whitelisted_folders"
private const val KEY_MIN_TRACK_LENGTH_SECONDS = "min_track_length_seconds"

/**
 * Persistent, SharedPreferences-backed settings for the local media scanner:
 * folder blacklist/whitelist and a minimum track length filter. Read by
 * [LocalMediaScanner] to filter scanned songs; edited from the settings UI.
 */
class LocalScanSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Folder paths to exclude from scanning (a folder and everything under it). */
    var blacklistedFolders: Set<String>
        get() = prefs.getStringSet(KEY_BLACKLISTED_FOLDERS, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_BLACKLISTED_FOLDERS, value).apply()

    /** When non-empty, ONLY folders in this set (and their subfolders) are scanned. */
    var whitelistedFolders: Set<String>
        get() = prefs.getStringSet(KEY_WHITELISTED_FOLDERS, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_WHITELISTED_FOLDERS, value).apply()

    /** Minimum track length in seconds; 0 disables the filter. */
    var minTrackLengthSeconds: Int
        get() = prefs.getInt(KEY_MIN_TRACK_LENGTH_SECONDS, 0)
        set(value) = prefs.edit().putInt(KEY_MIN_TRACK_LENGTH_SECONDS, value).apply()
}
