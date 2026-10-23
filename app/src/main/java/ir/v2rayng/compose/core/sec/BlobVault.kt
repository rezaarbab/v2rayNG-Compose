package ir.v2rayng.compose.core.sec

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage: every sensitive preference blob (server list, session
 * keys) is sealed with an AES-256-GCM key that lives inside AndroidKeyStore
 * (StrongBox when available) - plaintext never touches SharedPreferences.
 */
object BlobVault {

    private const val ALIAS = "blob-kv-v1"
    private const val AAD = "BLKV1"

    @Volatile
    private var cached: Boolean = false

    private fun secretKey(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val e = ks.getEntry(ALIAS, null)
        (e as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) {
        null
    }

    private fun createKey() {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = {
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
        }
        var okBox = false
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                kg.init(builder().setIsStrongBoxBacked(true).build())
                kg.generateKey()
                okBox = true
            } catch (_: Exception) {
                okBox = false
            }
        }
        if (!okBox) {
            kg.init(builder().build())
            kg.generateKey()
        }
    }

    private fun ensureKey() {
        if (secretKey() == null) {
            try {
                createKey()
            } catch (_: Exception) {
            }
        }
        cached = true
    }

    /** returns base64url(iv || ct) or null when the keystore refuses */
    fun seal(plain: ByteArray): String? {
        if (!cached) ensureKey()
        val k = secretKey() ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k)
            c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            val ct = c.doFinal(plain)
            BlobB64.encode(c.iv + ct)
        } catch (_: Exception) {
            null
        }
    }

    fun unseal(raw: String): ByteArray? {
        val blob = BlobB64.decode(raw) ?: return null
        if (blob.size < 13) return null
        val k = secretKey() ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            c.doFinal(blob.copyOfRange(12, blob.size))
        } catch (_: Exception) {
            null
        }
    }
}
