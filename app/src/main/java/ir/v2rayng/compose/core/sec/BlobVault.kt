package ir.v2rayng.compose.core.sec

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage: every sensitive preference blob (server list, session keys) is sealed
 * with an AES-256-GCM key that lives inside AndroidKeyStore - plaintext never touches
 * SharedPreferences, and the key material is not exportable.
 *
 * The key used to be created with setIsStrongBoxBacked(true). The device in the app log showed why
 * that has to go: a 1094-byte value took 3.8s, 4.3s and 8.5s to unseal, and every operation queued
 * behind every other one, because StrongBox is a small co-processor with its own lock. The TEE
 * keystore is still hardware-backed and still non-exportable, so the alias moved to blob-kv-v2 and
 * the old StrongBox key is kept readable purely so values sealed with it can migrate on the next
 * write instead of being lost.
 */
object BlobVault {

    private const val ALIAS = "blob-kv-v2"
    private const val LEGACY_ALIAS = "blob-kv-v1"
    private const val AAD = "BLKV1"

    /** Keystore handles for this process - fetching one is itself a binder round trip. */
    @Volatile
    private var keyRef: SecretKey? = null

    @Volatile
    private var legacyRef: SecretKey? = null

    @Volatile
    private var legacyTried = false

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun readKey(alias: String): SecretKey? = try {
        (keystore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) {
        null
    }

    private fun aliasExists(alias: String): Boolean = try {
        keystore().containsAlias(alias)
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
     * which is exactly the "nothing saves anymore" state. The keystore does refuse for a moment
     * under load, so the entry is re-read a few times first; only when it stays unreadable is it
     * replaced, and the replacement is reported instead of happening silently.
     */
    private fun key(): SecretKey? {
        keyRef?.let { return it }
        val t0 = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (keyRef != null) return keyRef
            var k = readKey(ALIAS)
            if (k == null && aliasExists(ALIAS)) {
                for (i in 1..3) {
                    try {
                        Thread.sleep(70L)
                    } catch (_: Exception) {
                    }
                    k = readKey(ALIAS)
                    if (k != null) break
                }
            }
            if (k == null) {
                val hadKey = aliasExists(ALIAS)
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
                k = readKey(ALIAS)
                if (k == null) recoveryNote = "the keystore gives no key at all on this device - saving cannot work"
            }
            keyRef = k
            SecJournal.note(
                "keystore key " + (if (k != null) "ok" else "UNAVAILABLE") +
                    " ${android.os.SystemClock.elapsedRealtime() - t0}ms" +
                    (recoveryNote?.let { " - $it" } ?: "")
            )
            return k
        }
    }

    /** The old StrongBox key, read only to open values that were sealed before the move. */
    private fun legacyKey(): SecretKey? {
        if (legacyTried) return legacyRef
        synchronized(this) {
            if (legacyTried) return legacyRef
            legacyTried = true
            legacyRef = readKey(LEGACY_ALIAS)
            if (legacyRef != null) SecJournal.note("legacy strongbox key present (read-only, for migration)")
            return legacyRef
        }
    }

    private fun generateKey() {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        kg.init(spec)
        kg.generateKey()
    }

    private fun openWith(k: SecretKey, blob: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        c.doFinal(blob.copyOfRange(12, blob.size))
    } catch (_: Exception) {
        null
    }

    /** returns base64url(iv || ct) or null when the keystore refuses */
    fun seal(plain: ByteArray): String? {
        val k = key() ?: return null
        val t0 = android.os.SystemClock.elapsedRealtime()
        return try {
            // one Cipher at a time: concurrent use of a keystore key fails on some devices
            val out = synchronized(this) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k)
                c.updateAAD(AAD.toByteArray(Charsets.UTF_8))
                val ct = c.doFinal(plain)
                BlobB64.encode(c.iv + ct)
            }
            SecJournal.note("seal ${plain.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            out
        } catch (e: Exception) {
            keyRef = null
            SecJournal.note("seal ERROR ${e.javaClass.simpleName} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            null
        }
    }

    fun unseal(raw: String): ByteArray? {
        val blob = BlobB64.decode(raw) ?: return null
        if (blob.size < 13) return null
        val k = key() ?: return null
        val t0 = android.os.SystemClock.elapsedRealtime()
        val out = synchronized(this) { openWith(k, blob) }
        if (out != null) {
            SecJournal.note("unseal ${out.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            return out
        }
        // written before the StrongBox key was dropped - open it with the old key; the next seal
        // rewrites it under the new one, so this path disappears after the first save
        val old = legacyKey()
        if (old != null) {
            val viaOld = synchronized(this) { openWith(old, blob) }
            if (viaOld != null) {
                SecJournal.note("unseal ${viaOld.size}B with LEGACY key ${android.os.SystemClock.elapsedRealtime() - t0}ms (will be resealed)")
                return viaOld
            }
        }
        SecJournal.note("unseal BadPadding ${android.os.SystemClock.elapsedRealtime() - t0}ms")
        return null
    }
}
