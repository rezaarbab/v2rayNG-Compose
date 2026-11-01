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

    private static native byte[] payloadTrimmed();

    /** the payload with its servers array removed - what Kotlin is allowed to see */
    public static byte[] payloadRest() {
        if (!READY) return null;
        try {
            return payloadTrimmed();
        } catch (Throwable t) {
            return null;
        }
    }

    private static native byte[] scrubUtf8(byte[] in);

    /** the log scrubber, in C: no host, ip, uuid, link or credential leaves a line */
    public static byte[] scrub(byte[] in) {
        if (!READY || in == null) return null;
        try {
            return scrubUtf8(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native String coreConfigForRow(int row, int mode, int socksPort);

    /** the config for a stored row as a String: libv2ray startLoop only takes one, so this is the
     *  single place a config text crosses to Kotlin - a property of that API, stated on purpose */
    public static String coreConfig(int row, int mode, int socksPort) {
        if (!READY || row < 0) return null;
        try {
            return coreConfigForRow(row, mode, socksPort);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native int payloadLoadCy(byte[] blob, byte[] kc);

    private static native String payloadSealAll();

    private static native int payloadRestore(String sealed);

    public static int payloadLoadRaw(byte[] blob, byte[] kc) {
        if (!READY || blob == null || kc == null) return -1;
        try {
            return payloadLoadCy(blob, kc);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** the rows sealed with the vault, base64url - an opaque string Kotlin may store as it is */
    public static String payloadSealed() {
        if (!READY) return null;
        try {
            return payloadSealAll();
        } catch (Throwable t) {
            return null;
        }
    }

    public static int payloadRestoreRows(String sealed) {
        if (!READY || sealed == null) return -1;
        try {
            return payloadRestore(sealed);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static native int payloadLoad(String stego, byte[] kc);

    private static native int payloadCount();

    private static native byte[] payloadField(int row, int which);

    /** stage 3: decrypt, parse and keep the server list inside the library. Returns the number of
     *  usable rows, or -1 (the step is in errInfo()). */
    public static int payloadLoadRows(String stego, byte[] kc) {
        if (!READY || stego == null || kc == null) return -1;
        try {
            return payloadLoad(stego, kc);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int payloadRows() {
        if (!READY) return 0;
        try {
            return payloadCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 0 id, 1 name, 3 category, 4 msg - the address (2), the port and the link are not reachable from here */
    public static String payloadText(int row, int which) {
        if (!READY) return "";
        try {
            byte[] b = payloadField(row, which);
            return b == null ? "" : new String(b, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static native String tokenFor(String device, byte[] kt);

    private static native String[] proofFor(String device);

    /** base64url(ts(4) | nonce(8) | HMAC(kt, "blb"|device|0x00|ts|nonce)) */
    public static String token(String device, byte[] kt) {
        if (!READY || device == null || kt == null) return null;
        try {
            return tokenFor(device, kt);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {proof, base64url(spki)} from one call: base64url(ts(4) | nonce(8) | ECDSA) and the public
     *  bytes that made it. They must come from the same instant - a pub cached before the identity
     *  door replaced a key is a signature the worker cannot match. */
    public static String[] proofAndPub(String device) {
        if (!READY || device == null) return null;
        try {
            return proofFor(device);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native int payloadRowOfId(String id);

    private static native int tcpPingRow(int row, int timeoutMs);

    /** the library row for the admin's id - the only handle the app keeps for a server - or -1 */
    public static int rowOf(String id) {
        if (!READY || id == null) return -1;
        try {
            return payloadRowOfId(id);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** a tcp connect to the row's server, timed in this process; -1 when nothing answered */
    public static int tcpPing(int row, int timeoutMs) {
        if (!READY || row < 0) return -1;
        try {
            return tcpPingRow(row, timeoutMs);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String note() {
        return READY ? "sec ok" : "sec library failed its self test";
    }
}
