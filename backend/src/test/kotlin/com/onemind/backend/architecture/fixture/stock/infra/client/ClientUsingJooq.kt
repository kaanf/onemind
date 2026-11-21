package com.onemind.backend.architecture.fixture.stock.infra.client

import org.jooq.DSLContext

// R3: jOOQ outside infra.persistence, here in an external-system adapter.
class ClientUsingJooq(
    val dsl: DSLContext,
)
