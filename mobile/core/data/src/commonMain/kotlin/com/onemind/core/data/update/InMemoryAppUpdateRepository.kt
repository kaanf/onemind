package com.onemind.core.data.update

import com.onemind.core.domain.session.AppUpdateRepository
import com.onemind.core.domain.session.AppUpdateRequired
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Set by the HTTP client on `426 APP_UPGRADE_REQUIRED`; read by the app root to show the update screen. */
class InMemoryAppUpdateRepository : AppUpdateRepository {
    private val _required = MutableStateFlow<AppUpdateRequired?>(null)
    override val required: StateFlow<AppUpdateRequired?> = _required.asStateFlow()

    fun report(update: AppUpdateRequired) {
        _required.value = update
    }

    override fun dismiss() {
        _required.value = null
    }
}
