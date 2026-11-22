package com.onemind.core.data.profile

import com.onemind.core.api.model.Me
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.profile.Profile
import com.onemind.core.domain.profile.ProfileRepository
import com.onemind.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** `GET /me`: only the display name is kept (the email and memberships aren't shown yet). */
class ProfileRepositoryImpl(
    private val client: HttpClient,
) : ProfileRepository {
    private val state = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = state.asStateFlow()

    override suspend fun refresh() {
        val result = safeCall<Me> { client.get("me") }
        if (result is Result.Success) state.value = Profile(displayName = result.data.displayName)
    }

    fun clear() {
        state.value = null
    }
}
