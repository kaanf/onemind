package com.onemind.archfixture.account.api

import com.onemind.archfixture.account.application.UseCase
import com.onemind.archfixture.account.infra.persistence.Repository

class Facade(
    val useCase: UseCase,
)

// Direction: api may use application and domain, not infra.
class FacadeUsingPersistence(
    val repository: Repository,
)
