package com.onemind.archfixture.pos.infra.client

// R9: on the ADR 0013 allowlist.
interface PosProvider {
    fun places(): List<String>
}
