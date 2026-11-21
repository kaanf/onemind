package com.onemind.archfixture.account.infra.client

// R9: the allowlist is by fully qualified name; the same simple name elsewhere is not allowed.
interface PosProvider {
    fun places(): List<String>
}
