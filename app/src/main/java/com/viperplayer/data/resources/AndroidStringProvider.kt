package com.viperplayer.data.resources

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [StringProvider] backed by the application [Context]. */
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {
    override fun getString(@StringRes id: Int, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) context.getString(id) else context.getString(id, *formatArgs)
}
