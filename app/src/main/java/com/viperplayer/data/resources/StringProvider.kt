package com.viperplayer.data.resources

import androidx.annotation.StringRes

/**
 * Resolves Android string resources outside of a `@Composable` scope (ViewModels, repositories,
 * services) so user-facing text never has to be hardcoded there. Injected via Hilt; in composables
 * prefer `stringResource(...)` directly.
 *
 * A `fun interface` so unit tests can supply a trivial fake — e.g.
 * `StringProvider { id, _ -> "res:$id" }` — without pulling in a real [android.content.Context].
 */
fun interface StringProvider {
    /** Resolves [id], formatting with [formatArgs] when the resource is a format string. */
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String
}
