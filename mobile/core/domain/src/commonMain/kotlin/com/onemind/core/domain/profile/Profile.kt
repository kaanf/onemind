package com.onemind.core.domain.profile

import kotlinx.coroutines.flow.StateFlow

/** The signed-in user as the app shows them (`GET /me`). The email is personal data: not kept here. */
data class Profile(
    /** `null` when the user hasn't given a name: no initials are invented then. */
    val displayName: String?,
) {
    /**
     * Account-button initials from [displayName]: the first letter of the first and the last word,
     * upper case (*Kaan Fırat* → *KF*, *Kaan* → *K*); `null` without a name.
     */
    val initials: String?
        get() {
            val words =
                displayName
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            if (words.isEmpty()) return null
            val letters = if (words.size == 1) listOf(words.first()) else listOf(words.first(), words.last())
            return letters.joinToString("") { it.first().uppercase() }
        }
}

interface ProfileRepository {
    /** `null` until loaded (and after sign-out). */
    val profile: StateFlow<Profile?>

    /** Loads `GET /me`; a failure keeps the last profile (the avatar simply stays hidden). */
    suspend fun refresh()
}
