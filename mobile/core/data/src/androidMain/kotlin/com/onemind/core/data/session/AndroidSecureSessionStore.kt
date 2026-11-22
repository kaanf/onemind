package com.onemind.core.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key generated inside the Android Keystore (non-exportable: the app only gets a
 * handle, never the key bytes). Each write gets a fresh random IV from the Keystore cipher
 * (`setRandomizedEncryptionRequired`), so an IV is never reused. The file is
 * `[version][ivLength][iv][ciphertext+tag]` in `noBackupFilesDir`, so it never leaves the device in a
 * backup (where the Keystore key couldn't decrypt it anyway). Anything unreadable is deleted and reads
 * as signed out.
 */
class AndroidSecureSessionStore(
    context: Context,
) : SecureSessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) return@withContext null
            val bytes = file.readFully()
            if (bytes.size < HEADER_BYTES + IV_BYTES + TAG_BITS / 8 || bytes[0] != FORMAT_VERSION || bytes[1].toInt() != IV_BYTES) {
                file.delete()
                return@withContext null
            }
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            if (key == null) {
                file.delete()
                return@withContext null
            }
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, HEADER_BYTES, IV_BYTES))
                cipher.doFinal(bytes, HEADER_BYTES + IV_BYTES, bytes.size - HEADER_BYTES - IV_BYTES).decodeToString()
            } catch (_: KeyPermanentlyInvalidatedException) {
                discardKeyAndFile()
                null
            } catch (_: UnrecoverableKeyException) {
                discardKeyAndFile()
                null
            } catch (_: GeneralSecurityException) {
                // Includes AEADBadTagException: a tampered or foreign file.
                file.delete()
                null
            }
        }

    override suspend fun write(value: String): Unit =
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            check(iv.size == IV_BYTES) { "Unexpected IV size" }
            val encrypted = cipher.doFinal(value.encodeToByteArray())
            val output = file.startWrite()
            try {
                output.write(byteArrayOf(FORMAT_VERSION, IV_BYTES.toByte()))
                output.write(iv)
                output.write(encrypted)
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) { file.delete() }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()
    }

    private fun discardKeyAndFile() {
        file.delete()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "onemind_session_key_v1"
        const val FILE_NAME = "onemind_session_v1.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val HEADER_BYTES = 2
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
