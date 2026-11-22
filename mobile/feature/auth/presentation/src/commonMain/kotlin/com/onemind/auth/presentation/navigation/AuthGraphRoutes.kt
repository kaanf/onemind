package com.onemind.auth.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface AuthGraphRoutes {
    @Serializable
    data object Graph : AuthGraphRoutes

    @Serializable
    data object SignIn : AuthGraphRoutes

    @Serializable
    data class EmailCode(
        val email: String,
        val resendAfterSeconds: Int,
    ) : AuthGraphRoutes {
        override fun toString(): String = "EmailCode(resendAfterSeconds=$resendAfterSeconds)"
    }
}
