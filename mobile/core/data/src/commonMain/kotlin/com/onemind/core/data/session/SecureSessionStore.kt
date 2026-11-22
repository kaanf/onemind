package com.onemind.core.data.session

/**
 * One encrypted value on the device: the serialized session. Android keeps it AES-GCM encrypted with
 * a non-exportable Android Keystore key, iOS in the Keychain (this device only, no backup). An
 * unreadable value (lost key, tampered file) reads as `null`, never as plaintext or a crash.
 */
interface SecureSessionStore {
    suspend fun read(): String?

    suspend fun write(value: String)

    suspend fun clear()
}
