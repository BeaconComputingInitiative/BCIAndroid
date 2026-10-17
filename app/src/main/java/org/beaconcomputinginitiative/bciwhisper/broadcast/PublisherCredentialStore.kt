/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Publisher Credential Store
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.broadcast

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the publisher credential encrypted with an Android Keystore key.
 *
 * A credential is considered configured if and only if both the encrypted
 * payload and IV can be decrypted successfully with the current device key.
 */
class PublisherCredentialStore(
    context: Context
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun save(token: String) {
        require(token.isNotBlank()) {
            "Publisher credential cannot be blank"
        }

        val cipher =
            Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val encrypted =
            cipher.doFinal(
                token.toByteArray(
                    Charsets.UTF_8
                )
            )

        prefs.edit()
            .putString(
                KEY_CIPHERTEXT,
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )
            .putString(
                KEY_IV,
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .apply()
    }

    fun read(): String? {
        val ciphertext =
            prefs.getString(
                KEY_CIPHERTEXT,
                null
            )
                ?: return null

        val iv =
            prefs.getString(
                KEY_IV,
                null
            )
                ?: return null

        return runCatching {
            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_BITS,
                    Base64.decode(
                        iv,
                        Base64.NO_WRAP
                    )
                )
            )

            String(
                cipher.doFinal(
                    Base64.decode(
                        ciphertext,
                        Base64.NO_WRAP
                    )
                ),
                Charsets.UTF_8
            ).takeIf {
                it.isNotBlank()
            }
        }.getOrElse {
            clear()
            null
        }
    }

    fun isConfigured(): Boolean =
        read() != null

    fun clear() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(
                ANDROID_KEYSTORE
            ).apply {
                load(null)
            }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey

        if (existing != null) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME =
            "bciandroid_broadcast_credentials"

        private const val KEY_CIPHERTEXT =
            "publisher_ciphertext"

        private const val KEY_IV =
            "publisher_iv"

        private const val KEY_ALIAS =
            "bciandroid_publisher_aes_v1"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_BITS =
            128
    }
}
