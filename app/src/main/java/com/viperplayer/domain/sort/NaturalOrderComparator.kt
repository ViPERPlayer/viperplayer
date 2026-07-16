package com.viperplayer.domain.sort

import java.text.Collator
import java.util.Locale

/**
 * A locale-aware natural-order comparator for name-like fields (titles, artist/album names).
 *
 * Two behaviours set it apart from a plain [String.compareTo]:
 *
 *  1. **Numeric chunks** are compared by value, not lexicographically, so `"Track 2"` sorts before
 *     `"Track 10"` (a plain string compare would put `"10"` before `"2"`).
 *  2. **Leading articles** ("The ", "A ", "An ") are ignored for the primary comparison, so
 *     *"The Beatles"* files under **B**, next to *"Beach House"*. Ties fall back to the full string so
 *     the ordering stays stable and total.
 *
 * Non-numeric text is compared with a [Collator] at [Collator.PRIMARY] strength, which is
 * case- and accent-insensitive and respects the supplied [locale]'s alphabet. Nulls sort first.
 *
 * [Collator] is not thread-safe, so each thread gets its own via a [ThreadLocal]; a single instance is
 * therefore safe to reuse concurrently from multiple ViewModels/threads. Bind [locale] to
 * [Locale.getDefault] at construction (not at class-load) so a runtime per-app language change is
 * honoured — [com.viperplayer.domain.sort.MediaSorter] builds a fresh comparator per sort pass.
 */
class NaturalOrderComparator(
    private val locale: Locale = Locale.getDefault(),
    private val stripLeadingArticles: Boolean = true,
) : Comparator<String?> {

    // PRIMARY strength: ignore case and accents; letters differing only in case/diacritic tie here and
    // are then broken by the raw-string fallback, keeping the order total and deterministic. Confined to
    // the calling thread because Collator mutates internal iterators during compare().
    private val collatorLocal: ThreadLocal<Collator> = ThreadLocal.withInitial {
        Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    }

    private val collator: Collator get() = collatorLocal.get()!!

    override fun compare(a: String?, b: String?): Int {
        if (a == null && b == null) return 0
        if (a == null) return -1
        if (b == null) return 1

        val left = if (stripLeadingArticles) stripArticle(a) else a
        val right = if (stripLeadingArticles) stripArticle(b) else b

        val primary = compareNatural(left, right)
        if (primary != 0) return primary

        // Stable, total fallback: articles-stripped equal (e.g. "The Cure" vs "Cure") or collator ties
        // (case/accent) are broken by the untouched strings so equal-looking keys keep a fixed order.
        return a.compareTo(b)
    }

    /**
     * Compare two strings run by run, treating maximal runs of digits as a single number and each
     * maximal run of non-digits as one collated substring. `"a2"` < `"a10"` because the numeric run
     * `2` < `10`.
     *
     * Non-digit runs are handed to [Collator.compare] as **whole substrings**, never char by char, so:
     *  - locale contractions collate correctly (e.g. German "ß" ↔ "ss": whole-string equal, which a
     *    per-char compare would wrongly split apart), and
     *  - astral code points (emoji) stay intact — a per-char loop would slice a surrogate pair into two
     *    lone surrogates and compare garbage.
     *
     * Both cursors advance fully past a run only when that run compared equal, so the two strings stay
     * aligned for the next run. The first non-zero run comparison wins; if every aligned run ties, the
     * shorter string sorts first (any leftover run makes the longer string greater). Collation-equal but
     * non-identical runs (e.g. "ß" ↔ "ss", or a case/accent-only difference) therefore tie here and are
     * broken by the raw-string fallback in [compare], keeping the order total and deterministic.
     */
    private fun compareNatural(a: String, b: String): Int {
        // Resolve the thread's collator once per call rather than per run.
        val collator = this.collator
        var i = 0
        var j = 0
        val lenA = a.length
        val lenB = b.length

        while (i < lenA && j < lenB) {
            val aDigit = a[i].isDigit()
            val bDigit = b[j].isDigit()

            if (aDigit && bDigit) {
                // Consume both digit runs and compare them as numbers (via length + value, so this is
                // safe for arbitrarily long runs that would overflow a Long).
                val startA = i
                val startB = j
                while (i < lenA && a[i].isDigit()) i++
                while (j < lenB && b[j].isDigit()) j++
                val cmp = compareNumericChunks(a, startA, i, b, startB, j)
                if (cmp != 0) return cmp
                // Equal-valued digit runs: both cursors already sit past their runs; continue aligned.
            } else if (aDigit != bDigit) {
                // One side is a digit, the other text: order digits before letters so "2 Tracks" sorts
                // ahead of "Two Tracks", matching typical natural-sort expectations.
                return if (aDigit) -1 else 1
            } else {
                // Both non-digit: extract the maximal non-digit run from each side and collate them as
                // whole substrings — never char by char — so locale contractions collate correctly and
                // astral code points (emoji) are never split into lone surrogates.
                val startA = i
                val startB = j
                while (i < lenA && !a[i].isDigit()) i++
                while (j < lenB && !b[j].isDigit()) j++
                val cmp = collator.compare(a.substring(startA, i), b.substring(startB, j))
                if (cmp != 0) return cmp
                // Runs are collation-equal: both cursors already sit past their (possibly
                // different-length) runs, so the next digit run — if any — still lines up. Any residual
                // difference (ß vs ss, case/accent) is deferred to compare()'s raw-string tie-break.
            }
        }

        // All aligned runs tied: the string with remaining characters is greater (shorter sorts first).
        return (lenA - i) - (lenB - j)
    }

    /**
     * Compare two digit runs [a][startA,endA) and [b][startB,endB) purely by numeric value, ignoring
     * leading zeros — so "007" and "7" compare equal (0). Any remaining difference between the full
     * strings (e.g. the extra zero characters) is left to the top-level raw-string fallback, keeping
     * this a value comparison and the overall order total.
     */
    private fun compareNumericChunks(
        a: String, startA: Int, endA: Int,
        b: String, startB: Int, endB: Int,
    ): Int {
        // Drop leading zeros so only significant digits are compared; keep at least one digit.
        var ia = startA
        var ib = startB
        while (ia < endA - 1 && a[ia] == '0') ia++
        while (ib < endB - 1 && b[ib] == '0') ib++

        val digitsA = endA - ia
        val digitsB = endB - ib
        if (digitsA != digitsB) return digitsA - digitsB // more significant digits => larger number

        // Same digit count: compare digit by digit.
        var ka = ia
        var kb = ib
        while (ka < endA) {
            val d = a[ka] - b[kb]
            if (d != 0) return d
            ka++
            kb++
        }
        // Equal in value (e.g. "007" vs "7") — report equal and let outer logic continue.
        return 0
    }

    private fun stripArticle(value: String): String {
        val trimmed = value.trimStart()
        for (article in ARTICLES) {
            if (trimmed.length > article.length &&
                trimmed.regionMatches(0, article, 0, article.length, ignoreCase = true)
            ) {
                return trimmed.substring(article.length).trimStart()
            }
        }
        return trimmed
    }

    companion object {
        /** Leading articles stripped for the primary comparison (English; extend per locale if needed). */
        private val ARTICLES = listOf("The ", "A ", "An ")
    }
}
