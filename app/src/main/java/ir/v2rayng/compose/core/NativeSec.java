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

    public static String note() {
        return READY ? "sec ok" : "sec library failed its self test";
    }
}
