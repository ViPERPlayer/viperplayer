package com.viperplayer.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: persist the IIR equalizer, playback gain, and convolver effects in `viper_presets`.
 * These were previously dropped on save/load, so the EQ (and convolver/playback-gain) was wiped
 * whenever a preset was reloaded (e.g. switching audio device).
 *
 * Each ADD COLUMN's type/NOT NULL/DEFAULT must match the corresponding [ViperPresetEntity] field
 * (incl. its @ColumnInfo defaultValue) exactly, or Room's runtime schema validation will fail.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // IIR Equalizer
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN iirEqualizerEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN iirEqualizerBandCount INTEGER NOT NULL DEFAULT 10")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN iirEqualizerPreset TEXT NOT NULL DEFAULT 'Flat'")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN iirEqualizerBandGains TEXT NOT NULL DEFAULT ''")
        // Playback Gain
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN playbackGainEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN playbackGainStrength INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN playbackGainMaxGain INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN playbackGainOutputThreshold REAL NOT NULL DEFAULT 0")
        // Convolver
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN convolverEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN convolverImpulseResponse TEXT")
        db.execSQL("ALTER TABLE viper_presets ADD COLUMN convolverCrossChannel INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v2 -> v3: a playable's source can be a video (music videos in playlists/albums), resolved through
 * the plugin's video endpoint. Matches SongEntity.isVideo's @ColumnInfo(defaultValue = "0").
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN isVideo INTEGER NOT NULL DEFAULT 0")
    }
}
