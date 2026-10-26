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

    /** Keystore handle for this process - fetching it is itself a binder round trip. */
    @Volatile
    private var keyRef: SecretKey? = null

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun readKey(): SecretKey? = try {
        (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) {
        null
    }

    private fun aliasExists(): Boolean = try {
        keystore().containsAlias(ALIAS)
    } catch (_: Exception) {
        false
    }

    /** What happened to the key, for the app log - "cannot read" and "no key at all" are very
     *  different states and the user sees only "nothing saves" without this. */
    @Volatile
    var recoveryNote: String? = null
        private set

    /**
     * A live but unreadable key entry must not be left in place: seal() then returns null, every
     * write is dropped, every read yields defaults, and the app can never store anything again -
     * which is exactly the "nothing saves anymore" state. StrongBox does refuse for a moment under
     * load, so the entry is re-read a few times first; only when it stays unreadable is it
     * replaced, and the replacement is reported instead of happening silently.
     */
    private fun key(): SecretKey? {
        keyRef?.let { return it }
        synchronized(this) {
            if (keyRef != null) return keyRef
            var k = readKey()
            if (k == null && aliasExists()) {
                for (i in 1..3) {
                    try {
                        Thread.sleep(70L)
                    } catch (_: Exception) {
                    }
                    k = readKey()
                    if (k != null) break
                }
            }
            if (k == null) {
                val hadKey = aliasExists()
                if (hadKey) {
                    recoveryNote = "keystore key exists but this device cannot read it - it was replaced, so values stored earlier are gone"
                    try {
                        keystore().deleteEntry(ALIAS)
                    } catch (_: Exception) {
                    }
                }
                try {
                    generateKey()
                } catch (_: Exception) {
                }
                k = readKey()
                if (k == null) recoveryNote = "the keystore gives no key at all on this device - saving cannot work"
            }
            keyRef = k
            return k
        }
    }

    private fun generateKey() {
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

    /** returns base64url(iv || ct) or null when the keystore refuses */
    fun seal(plain: ByteArray): String? {
        val k = key() ?: return null
        return try {
            // one StrongBox operation at a time: concurrent Cipher use fails on some devices
            synchronized(this) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k)
                c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
                val ct = c.doFinal(plain)
                BlobB64.encode(c.iv + ct)
            }
        } catch (_: Exception) {
            keyRef = null
            null
        }
    }

    fun unseal(raw: String): ByteArray? {
        val blob = BlobB64.decode(raw) ?: return null
        if (blob.size < 13) return null
        val k = key() ?: return null
        return try {
            synchronized(this) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
                c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
                c.doFinal(blob.copyOfRange(12, blob.size))
            }
        } catch (_: Exception) {
            keyRef = null
            null
        }
    }
}
