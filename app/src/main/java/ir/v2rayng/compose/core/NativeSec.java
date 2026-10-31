package ir.v2rayng.compose.core;

/**
 * Java declaration of the native decode primitives (blobsec.c). Java, not Kotlin, for the same
 * reason as BlobGate: the member names the linker looks up are exactly what is written here -
 * selftest, hmac, openEnvelope, b64d and b64uEncode must match
 * Java_ir_v2rayng_compose_core_NativeSec_* one for one.
 *
 * Nothing is used until the known-answer self test has passed once on this device. If it fails,
 * every call returns null, which surfaces as a failed sync with a clear message rather than a
 * payload opened with a broken SHA/HMAC.
 */
public final class NativeSec {

    private static final boolean READY;

    static {
        boolean ok;
        try {
            System.loadLibrary("blobgate");
            ok = selftest() == 0;
        } catch (Throwable t) {
            ok = false;
        }
        READY = ok;
    }

    private NativeSec() {
    }

    /** 0 when SHA-256 and the RFC 4231 vectors match; any other number means a broken library. */
    private static native int selftest();

    public static boolean ready() {
        return READY;
    }

    private static native byte[] hmac(byte[] key, byte[] msg);

    private static native byte[] openEnvelope(byte[] blob);

    private static native byte[] b64d(String s);

    private static native String b64uEncode(byte[] b);

    public static byte[] hmacSha256(byte[] key, byte[] msg) {
        if (!READY || key == null || msg == null) return null;
        try {
            byte[] out = hmac(key, msg);
            return (out != null && out.length == 32) ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** [mode 1B || raw | raw-deflate | zlib] -> bytes, the same three cases java's Inflater covered. */
    public static byte[] openEnv(byte[] envelope) {
        if (!READY || envelope == null || envelope.length < 1) return null;
        try {
            return openEnvelope(envelope);
        } catch (Throwable t) {
            return null;
        }
    }

    /** base64 and base64url both decode here; '-'/'_' and a missing pad are accepted, as before. */
    public static byte[] b64decode(String s) {
        if (!READY || s == null) return null;
        try {
            return b64d(s);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String b64url(byte[] b) {
        if (!READY || b == null) return null;
        try {
            return b64uEncode(b);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native byte[] gcmOpen(byte[] cipher, byte[] key, byte[] aad);

    /** iv(12) || AES-256-GCM ciphertext+tag, opened with the session key - null on any failure. */
    public static byte[] gcmDecrypt(byte[] cipher, byte[] key, byte[] aad) {
        if (!READY || cipher == null || key == null || cipher.length < 29 || key.length != 32) return null;
        try {
            return gcmOpen(cipher, key, aad);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native int vaultReady(int which);

    private static native byte[] vaultSeal(byte[] plain);

    private static native byte[] vaultOpen(byte[] blob, int which);

    private static native int vaultDrop(int which);

    private static native int vaultHas(int which);

    private static native int vaultRoundTrip();

    private static native String err();

    /** which Cipher-through-JNI step ran last, plus any Java exception it met - the reason
     *  "GCM tag fail" has to be split into "AES/GCM refused", "init threw", "bad tag", etc.
     *  Named differently from the native on purpose: same name and same (empty) arguments would
     *  not compile, and a recursive wrapper would blow the stack. */
    public static String errInfo() {
        if (!READY) return "not ready";
        try {
            String e = err();
            return e == null ? "?" : e;
        } catch (Throwable t) {
            return "err() threw";
        }
    }

    /** 0 = sealing and opening the same bytes works end to end with this key */
    public static int vaultSelfCheck() {
        if (!READY) return -1;
        try {
            return vaultRoundTrip();
        } catch (Throwable t) {
            return -2;
        }
    }

    /** does an entry exist under this alias? ask before dropping anything */
    public static boolean hasKey(int which) {
        return READY && vaultHas(which) == 1;
    }

    /** the keystore vault: alias, AAD and cipher all live in blobsec.c; which=1 is the old key */
    public static boolean vaultOk(int which) {
        return READY && vaultReady(which) == 1;
    }

    public static byte[] seal(byte[] plain) {
        if (!READY || plain == null) return null;
        try {
            return vaultSeal(plain);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] unseal(byte[] blob, int which) {
        if (!READY || blob == null) return null;
        try {
            return vaultOpen(blob, which);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean dropKey(int which) {
        return READY && vaultDrop(which) == 1;
    }

    private static native int idEnsure(byte[] challenge);

    private static native byte[] idSign(byte[] msg);

    private static native byte[] idSpki();

    private static native int idDrop();

    private static native String[] idChain();

    private static native byte[] sha(byte[] in);

    /** the device identity key: attested first, plain second, never exportable.
     *  1 = ready, 2 = an unreadable entry was deleted and replaced, 0 = this device gives none. */
    public static int idState() {
        if (!READY) return 0;
        try {
            return idEnsure(null);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static byte[] sign(byte[] msg) {
        if (!READY || msg == null) return null;
        try {
            return idSign(msg);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] spki() {
        if (!READY) return null;
        try {
            return idSpki();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean dropId() {
        return READY && idDrop() == 1;
    }

    /** {chain length, attested, self-signed, issuer, attestation-extension} - or null */
    public static String[] chain() {
        if (!READY) return null;
        try {
            return idChain();
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] sha256(byte[] in) {
        if (!READY || in == null) return null;
        try {
            return sha(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native byte[] rndBytes(int count);

    private static native int verify(byte[] spki, byte[] msg, byte[] sig);

    /** n bytes from the platform CSPRNG - the token nonce and the attestation challenge */
    public static byte[] random(int count) {
        if (!READY || count <= 0) return null;
        try {
            return rndBytes(count);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 1 = the signature opens under this SPKI, 0 = it does not, -1 = the call could not be made */
    public static int verifySpki(byte[] spki, byte[] msg, byte[] sig) {
        if (!READY || spki == null || msg == null || sig == null) return -1;
        try {
            return verify(spki, msg, sig);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static native byte[] stegoOpen(String stego, byte[] kc);

    private static native String tokenFor(String device, byte[] kt);

    private static native String proofFor(String device);

    /** the whole payload path in C: carrier text plus session key in, plaintext JSON bytes out */
    public static byte[] stegoReveal(String stego, byte[] kc) {
        if (!READY || stego == null || kc == null) return null;
        try {
            return stegoOpen(stego, kc);
        } catch (Throwable t) {
            return null;
        }
    }

    /** base64url(ts(4) | nonce(8) | HMAC(kt, "blb"|device|0x00|ts|nonce)) */
    public static String token(String device, byte[] kt) {
        if (!READY || device == null || kt == null) return null;
        try {
            return tokenFor(device, kt);
        } catch (Throwable t) {
            return null;
        }
    }

    /** base64url(ts(4) | nonce(8) | ECDSA over the same message), signed by the hardware key */
    public static String proof(String device) {
        if (!READY || device == null) return null;
        try {
            return proofFor(device);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String note() {
        return READY ? "sec ok" : "sec library failed its self test";
    }
}
