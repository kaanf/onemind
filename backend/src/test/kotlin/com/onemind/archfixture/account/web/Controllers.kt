package com.onemind.archfixture.account.web

import com.onemind.api.v1.model.AuthTokens
import com.onemind.archfixture.account.api.events.SomethingHappened
import com.onemind.archfixture.account.application.UseCase
import com.onemind.archfixture.account.domain.Role
import com.onemind.archfixture.account.infra.persistence.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

class Controller(
    val useCase: UseCase,
    val role: Role,
    val dto: AuthTokens,
)

// Direction: web never uses infra or api.events.
class ControllerUsingPersistence(
    val repository: Repository,
)

class ControllerUsingEvents(
    val event: SomethingHappened,
)

// R7: transactions only in application.
class ControllerUsingTransactions(
    val transactions: TransactionTemplate,
)

open class ControllerWithTransactionalMethod {
    @Transactional
    open fun handle() = Unit
}
