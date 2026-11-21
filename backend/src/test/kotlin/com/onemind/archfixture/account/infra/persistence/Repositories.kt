package com.onemind.archfixture.account.infra.persistence

import com.onemind.archfixture.account.application.UseCase
import com.onemind.archfixture.account.domain.Role
import com.onemind.archfixture.account.infra.client.Client
import org.jooq.Record

class Repository {
    fun role(): Role = Role.OWNER
}

// R4: a public signature exposes a jOOQ Record.
class RepositoryReturningRecord {
    fun find(): Record? = null
}

// Direction: persistence uses only domain.
class PersistenceUsingClient(
    val client: Client,
)

class PersistenceUsingApplication(
    val useCase: UseCase,
)
