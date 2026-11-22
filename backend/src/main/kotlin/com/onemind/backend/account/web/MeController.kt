package com.onemind.backend.account.web

import com.onemind.api.v1.model.Me
import com.onemind.api.v1.model.MyMembership
import com.onemind.api.v1.model.MyVenue
import com.onemind.api.v1.model.MyVenues
import com.onemind.api.v1.web.MeApi
import com.onemind.backend.account.application.AccountDeletion
import com.onemind.backend.account.application.GetMyAccount
import com.onemind.backend.common.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter

@RestController
class MeController(
    private val accountDeletion: AccountDeletion,
    private val getMyAccount: GetMyAccount,
) : MeApi {
    override fun deleteMe(): ResponseEntity<Unit> {
        accountDeletion.delete(CurrentUser.id())
        return ResponseEntity.noContent().build()
    }

    override fun getMe(): ResponseEntity<Me> {
        val me = getMyAccount.me(CurrentUser.id())
        return ResponseEntity.ok(
            Me(
                id = me.userId,
                email = me.email,
                memberships =
                    me.memberships.map {
                        MyMembership(
                            it.organizationId,
                            it.organizationName,
                            MyMembership.Role.valueOf(it.role.name),
                        )
                    },
                displayName = me.displayName,
            ),
        )
    }

    override fun getMyVenues(): ResponseEntity<MyVenues> =
        ResponseEntity.ok(
            MyVenues(
                getMyAccount.venues(CurrentUser.id()).map {
                    MyVenue(
                        id = it.venueId,
                        name = it.name,
                        timeZone = it.timeZone,
                        currency = it.currency.currencyCode,
                        businessDayStart = it.businessDayStart.format(CUT_OFF),
                        followed = it.followed,
                        role = MyVenue.Role.valueOf(it.role.name),
                    )
                },
            ),
        )

    private companion object {
        val CUT_OFF: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
