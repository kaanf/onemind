package com.onemind.archfixture.account.support

import com.onemind.archfixture.account.application.UseCase
import com.onemind.archfixture.account.infra.persistence.Repository

// §2: a package outside the layers, which the direction rules alone would never see.
class Sneaky(
    val repository: Repository,
    val useCase: UseCase,
)
