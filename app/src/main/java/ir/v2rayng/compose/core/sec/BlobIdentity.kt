package ir.v2rayng.compose.core.sec

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Per-install device identity.
 *
 * An EC P-256 key pair is generated inside AndroidKeyStore and is never exportable - not by this
 * process, not by a rooted shell reading the prefs. The worker memorises the public key the first
 * time it sees this device (trust-on-first-use) and from then on every session key is handed out
 * only against a signature made by the matching private key.
 *
 * That replaces the old scheme where the app proved itself with a secret baked into the APK:
 * decompiling the app plus guessing an ANDROID_ID used to be enough to authenticate as somebody
 * else's handset. Now the APK carries nothing secret, and the signing capability lives in
 * hardware that the attacker would have to physically own.
 */
object BlobIdentity {

    private const val ALIAS = "blob-dev-v1"

    @Volatile
    private var keyRef: PrivateKey? = null

    @Volatile
    private var pubRef: ByteArray? = null

    /** What happened to the key, for the app log and the panel. */
    @Volatile
    var note: String? = null
        private set

    private fun ks(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun readPrivate(): PrivateKey? = try {
        (ks().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.privateKey
    } catch (_: Exception) {
        null
    }

    /** X.509 SubjectPublicKeyInfo DER - exactly what the worker imports as "spki". */
    private fun readPublic(): ByteArray? = try {
        ks().getCertificate(ALIAS)?.publicKey?.encoded
    } catch (_: Exception) {
        null
    }

    private fun exists(): Boolean = try {
        ks().containsAlias(ALIAS)
    } catch (_: Exception) {
        false
    }

    private fun generate(): Boolean = try {
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        gen.generateKeyPair()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * A key entry that cannot be read is worse than no entry: nothing can ever be signed again.
     * That happens for real when the APK is reinstalled with a different signing certificate, so
     * the dead entry is replaced and the worker sees it as a key rotation instead of a broken app.
     */
    private fun privateKey(): PrivateKey? {
        keyRef?.let { return it }
        val t0 = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (keyRef != null) return keyRef
            var k = readPrivate()
            if (k == null) {
                val had = exists()
                if (had) {
                    note = "identity key unreadable (APK signed with a different certificate?) - a new one was created"
                    try {
                        ks().deleteEntry(ALIAS)
                    } catch (_: Exception) {
                    }
                }
                pubRef = null
                if (generate()) k = readPrivate()
                if (k == null) note = "this device gives no keystore signing key - the server cannot authenticate it"
            }
            keyRef = k
            SecJournal.note(
                "identity key " + (if (k != null) "ok" else "UNAVAILABLE") +
                    " ${android.os.SystemClock.elapsedRealtime() - t0}ms" +
                    (note?.let { " - $it" } ?: "")
            )
            return k
        }
    }

    fun publicKeyB64(): String? {
        if (privateKey() == null) return null
        pubRef?.let { return BlobB64.encode(it) }
        val p = readPublic() ?: return null
        pubRef = p
        return BlobB64.encode(p)
    }

    /** Short human-readable handle for the panel - the public key itself is too long to compare. */
    fun fingerprint(): String = idSeed().take(16)

    /** 32 hex chars over the public key - a device id nobody can invent or copy off another phone. */
    fun idSeed(): String {
        // the very first call also creates the key, so the panel already has the fingerprint on the
        // first sync instead of one sync late
        if (pubRef == null) privateKey()
        val p = pubRef ?: readPublic()?.also { pubRef = it } ?: return ""
        return try {
            val d = MessageDigest.getInstance("SHA-256").digest(p)
            val sb = StringBuilder()
            for (i in 0 until 16) sb.append(String.format("%02x", d[i]))
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /** DER ECDSA-SHA256 signature over msg, base64url - null when this device cannot sign. */
    fun sign(msg: ByteArray): String? {
        val t0 = android.os.SystemClock.elapsedRealtime()
        var k = privateKey() ?: return null
        for (attempt in 0 until 2) {
            try {
                // one operation at a time: some keystore backends reject concurrent use
                val sig = synchronized(this) {
                    val s = Signature.getInstance("SHA256withECDSA")
                    s.initSign(k)
                    s.update(msg)
                    s.sign()
                }
                SecJournal.note("identity sign ${sig.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                return BlobB64.encode(sig)
            } catch (e: Exception) {
                keyRef = null
                pubRef = null
                SecJournal.note("identity sign ERROR ${e.javaClass.simpleName} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                k = privateKey() ?: return null
            }
        }
        return null
    }
}
