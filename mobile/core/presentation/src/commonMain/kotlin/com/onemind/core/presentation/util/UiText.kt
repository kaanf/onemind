package com.onemind.core.presentation.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Text a ViewModel hands to the UI without knowing the locale: a string resource (with format args)
 * or a dynamic value, such as the server's English `detail` for an error code the app doesn't know.
 */
sealed interface UiText {
    data class Dynamic(
        val value: String,
    ) : UiText

    data class Resource(
        val id: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText

    @Composable
    fun asString(): String =
        when (this) {
            is Dynamic -> value
            is Resource -> stringResource(id, *args.toTypedArray())
        }

    suspend fun asStringAsync(): String =
        when (this) {
            is Dynamic -> value
            is Resource -> getString(id, *args.toTypedArray())
        }
}
