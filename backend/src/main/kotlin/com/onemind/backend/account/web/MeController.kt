package com.onemind.backend.account.web

import com.onemind.api.v1.web.MeApi
import com.onemind.backend.account.application.AccountDeletion
import com.onemind.backend.common.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class MeController(
    private val accountDeletion: AccountDeletion,
) : MeApi {
    override fun deleteMe(): ResponseEntity<Unit> {
        accountDeletion.delete(CurrentUser.id())
        return ResponseEntity.noContent().build()
    }
}
