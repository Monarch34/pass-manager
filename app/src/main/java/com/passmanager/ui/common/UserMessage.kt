package com.passmanager.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-visible message that can be resolved in Compose (localization-safe).
 *
 * - [Resource]: use for all static copy from [strings.xml].
 * - [Plain]: only for truly dynamic text (e.g. exception details); prefer [Resource] with format args when possible.
 */
sealed class UserMessage {
    data class Plain(val text: String) : UserMessage()

    data class Resource(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UserMessage() {
        constructor(@StringRes resId: Int, vararg formatArgs: Any) : this(resId, formatArgs.toList())
    }
}

/** Resolve for [LaunchedEffect] / non-Composable callers (e.g. snackbar from context). */
fun UserMessage.resolve(context: Context): String = when (this) {
    is UserMessage.Plain -> text
    is UserMessage.Resource -> {
        val args = formatArgs.toTypedArray()
        if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args)
        }
    }
}

@Composable
fun UserMessage.resolve(): String = when (this) {
    is UserMessage.Plain -> text
    is UserMessage.Resource -> {
        val args = formatArgs.toTypedArray()
        if (args.isEmpty()) {
            stringResource(resId)
        } else {
            stringResource(resId, *args)
        }
    }
}
