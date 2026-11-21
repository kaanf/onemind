package com.onemind.archfixture.account.infra.client

import com.onemind.archfixture.account.infra.persistence.Repository

class Client

// Direction: client and persistence are independent layers.
class ClientUsingPersistence(
    val repository: Repository,
)
