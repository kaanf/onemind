package com.onemind.core.data.session

class FakeSecureSessionStore(
    var value: String? = null,
) : SecureSessionStore {
    var writes = 0

    override suspend fun read(): String? = value

    override suspend fun write(value: String) {
        writes++
        this.value = value
    }

    override suspend fun clear() {
        value = null
    }
}
