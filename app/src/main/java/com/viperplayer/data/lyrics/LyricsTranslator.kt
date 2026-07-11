package com.viperplayer.data.lyrics

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device lyrics translation via Google ML Kit. Language models download on demand (no API key).
 * All operations are resilient: any failure (missing/unsupported model, offline first-download, etc.)
 * yields null so callers just fall back to the original lyrics.
 */
@Singleton
class LyricsTranslator @Inject constructor() {

    /**
     * Translates [lines] into [targetLanguage] (a BCP-47 tag, e.g. "en", "es").
     *
     * Detects the source language from the joined lines; returns null when the source is undetermined
     * ("und") or already the target language (nothing to translate). Downloads the translation model
     * if needed, then translates each line in order. Returns null on any failure.
     */
    suspend fun translate(lines: List<String>, targetLanguage: String): List<String>? = runCatching {
        if (lines.isEmpty()) return null

        val target = TranslateLanguage.fromLanguageTag(targetLanguage) ?: return null

        val joined = lines.joinToString("\n") { it.trim() }.trim()
        if (joined.isEmpty()) return null

        val identifier = LanguageIdentification.getClient()
        val sourceTag = try {
            identifier.identifyLanguage(joined).await()
        } finally {
            identifier.close()
        }
        if (sourceTag == "und" || sourceTag == targetLanguage) return null

        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: return null
        if (source == target) return null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            lines.map { line -> translator.translate(line).await() }
        } finally {
            translator.close()
        }
    }.getOrNull()
}
