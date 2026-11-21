package com.onemind.archfixture.account.domain

import com.onemind.archfixture.account.application.UseCase
import org.springframework.util.StopWatch

enum class Role { OWNER }

// Direction: domain depends on nothing in the module.
class DomainUsingApplication(
    val useCase: UseCase,
)

// R6: domain imports no Spring.
class DomainUsingSpring(
    val watch: StopWatch,
)
