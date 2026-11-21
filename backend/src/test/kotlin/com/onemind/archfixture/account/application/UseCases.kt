package com.onemind.archfixture.account.application

import com.onemind.api.v1.model.AuthTokens
import com.onemind.archfixture.account.api.events.SomethingHappened
import com.onemind.archfixture.account.domain.Role
import com.onemind.archfixture.account.infra.client.Client
import com.onemind.archfixture.account.infra.persistence.Repository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

@Service
class UseCase(
    val repository: Repository,
    val client: Client,
    val role: Role,
    val clock: Clock,
    val transactions: TransactionTemplate,
) {
    fun publish() = SomethingHappened(role.name)
}

// R2: five collaborators, plus the allowlisted Clock and TransactionTemplate.
@Service
class FiveCollaborators(
    val a: String,
    val b: Int,
    val c: Long,
    val d: Double,
    val e: Short,
    val clock: Clock,
    val transactions: TransactionTemplate,
)

// R2: six collaborators.
@Service
class SixCollaborators(
    val a: String,
    val b: Int,
    val c: Long,
    val d: Double,
    val e: Short,
    val f: Byte,
)

// R2: a bean named *Service.
@Component
class BillingService(
    val clock: Clock,
)

// R2 counts beans only: a result with many fields is fine.
data class BigResult(
    val a: String,
    val b: Int,
    val c: Long,
    val d: Double,
    val e: Short,
    val f: Byte,
    val g: Char,
)

// R5: generated OpenAPI types outside web.
class UseCaseUsingOpenApi(
    val dto: AuthTokens,
)

// R9: an interface with one implementation and no ADR exception.
interface SingleImplPort {
    fun run()
}

// R9: sealed result types are fine.
sealed interface Outcome {
    data object Done : Outcome
}
