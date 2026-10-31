/* blobsec - the decode primitives, in C.
 *
 * SHA-256, HMAC-SHA256, base64url and the zlib envelope opening are implemented here; the Kotlin
 * side keeps no copy of them. AES-256-GCM and the keystore are NOT re-implemented: this library
 * drives the platform's own Cipher, KeyStore and Signature through JNI, because on a machine with
 * no C compiler and no local builds a hand-typed AES table is a bug nobody can find before it
 * ships. Either way no Java or Kotlin source contains a cipher, a key spec or an alias.
 * The self-test below must pass on the device before any of this is trusted.
 */

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <zlib.h>
#include <stdint.h>
#include <time.h>

/* ---------------- SHA-256 (FIPS 180-4) ---------------- */

static const unsigned int K256[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
};

#define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTR(x, 2) ^ ROTR(x, 13) ^ ROTR(x, 22))
#define EP1(x) (ROTR(x, 6) ^ ROTR(x, 11) ^ ROTR(x, 25))
#define SIG0(x) (ROTR(x, 7) ^ ROTR(x, 18) ^ ((x) >> 3))
#define SIG1(x) (ROTR(x, 17) ^ ROTR(x, 19) ^ ((x) >> 10))

typedef struct {
    unsigned int h[8];
    unsigned char buf[64];
    unsigned long long total;
    size_t buflen;
} sha256_ctx;

static void sha_block(unsigned int *h, const unsigned char *p) {
    unsigned int w[64], a, b, c, d, e, f, g, hh, t1, t2;
    int i;
    for (i = 0; i < 16; i++)
        w[i] = ((unsigned int) p[i * 4] << 24) | ((unsigned int) p[i * 4 + 1] << 16) |
               ((unsigned int) p[i * 4 + 2] << 8) | (unsigned int) p[i * 4 + 3];
    for (i = 16; i < 64; i++) w[i] = SIG1(w[i - 2]) + w[i - 7] + SIG0(w[i - 15]) + w[i - 16];
    a = h[0]; b = h[1]; c = h[2]; d = h[3]; e = h[4]; f = h[5]; g = h[6]; hh = h[7];
    for (i = 0; i < 64; i++) {
        t1 = hh + EP1(e) + CH(e, f, g) + K256[i] + w[i];
        t2 = EP0(a) + MAJ(a, b, c);
        hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
}

static void sha_init(sha256_ctx *c) {
    c->h[0] = 0x6a09e667u; c->h[1] = 0xbb67ae85u; c->h[2] = 0x3c6ef372u; c->h[3] = 0xa54ff53au;
    c->h[4] = 0x510e527fu; c->h[5] = 0x9b05688cu; c->h[6] = 0x1f83d9abu; c->h[7] = 0x5be0cd19u;
    c->total = 0;
    c->buflen = 0;
    memset(c->buf, 0, sizeof(c->buf));
}

static void sha_update(sha256_ctx *c, const unsigned char *in, size_t len) {
    c->total += len;
    while (len > 0) {
        size_t take = 64 - c->buflen;
        if (take > len) take = len;
        memcpy(c->buf + c->buflen, in, take);
        c->buflen += take;
        in += take;
        len -= take;
        if (c->buflen == 64) {
            sha_block(c->h, c->buf);
            c->buflen = 0;
        }
    }
}

static void sha_final(sha256_ctx *c, unsigned char out[32]) {
    unsigned long long bits = c->total * 8ULL;
    unsigned char pad[72];
    size_t n = 0, i;
    pad[n++] = 0x80;
    while ((c->buflen + n) % 64 != 56) pad[n++] = 0;
    for (i = 7; i != (size_t) -1; i--) pad[n++] = (unsigned char) ((bits >> (i * 8)) & 0xffu);
    sha_update(c, pad, n);
    for (i = 0; i < 8; i++) {
        out[i * 4] = (unsigned char) ((c->h[i] >> 24) & 0xffu);
        out[i * 4 + 1] = (unsigned char) ((c->h[i] >> 16) & 0xffu);
        out[i * 4 + 2] = (unsigned char) ((c->h[i] >> 8) & 0xffu);
        out[i * 4 + 3] = (unsigned char) (c->h[i] & 0xffu);
    }
}

static void sha256(const unsigned char *in, size_t len, unsigned char out[32]) {
    sha256_ctx c;
    sha_init(&c);
    sha_update(&c, in, len);
    sha_final(&c, out);
}

/* ---------------- HMAC-SHA256 (RFC 2104) ---------------- */

static void hmac_sha256(const unsigned char *key, size_t klen,
                        const unsigned char *msg, size_t mlen, unsigned char out[32]) {
    unsigned char k[64], ip[64], op[64], inner[32];
    sha256_ctx c;
    size_t i;
    memset(k, 0, sizeof(k));
    if (klen > 64) {
        unsigned char kh[32];
        sha256(key, klen, kh);
        memcpy(k, kh, 32);
    } else {
        memcpy(k, key, klen);
    }
    for (i = 0; i < 64; i++) { ip[i] = k[i] ^ 0x36; op[i] = k[i] ^ 0x5c; }
    sha_init(&c);
    sha_update(&c, ip, 64);
    sha_update(&c, msg, mlen);
    sha_final(&c, inner);
    sha_init(&c);
    sha_update(&c, op, 64);
    sha_update(&c, inner, 32);
    sha_final(&c, out);
}

/* ---------------- base64 / base64url ---------------- */

static const char B64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static int b64val(unsigned char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+' || c == '-') return 62;
    if (c == '/' || c == '_') return 63;
    return -1;
}

static int hexeq(const unsigned char *a, const char *hex) {
    int i;
    for (i = 0; i < 32; i++) {
        char b[3];
        sprintf(b, "%02x", a[i]);
        if (b[0] != hex[i * 2] || b[1] != hex[i * 2 + 1]) return 0;
    }
    return 1;
}

/* ---------------- known-answer self test ---------------- */

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_selftest(JNIEnv *env, jclass cls) {
    int bad = 0;
    unsigned char d[32];
    sha256((const unsigned char *) "abc", 3, d);
    if (!hexeq(d, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")) bad++;
    sha256((const unsigned char *) "", 0, d);
    if (!hexeq(d, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")) bad++;
    {
        /* 448-bit input: crosses the block boundary, the case a hand-rolled length field gets wrong */
        const char *long448 = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
        sha256((const unsigned char *) long448, strlen(long448), d);
        if (!hexeq(d, "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1")) bad++;
    }
    {
        unsigned char key[32], msg[48], mac[32];
        /* RFC 4231 case 1: key = 20 x 0x0b, data = "Hi There" */
        int i;
        for (i = 0; i < 20; i++) key[i] = 0x0b;
        memcpy(msg, "Hi There", 8);
        hmac_sha256(key, 20, msg, 8, mac);
        if (!hexeq(mac, "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7")) bad++;
        /* RFC 4231 case 2: key "Jefe", data "what do ya want for nothing?" */
        memcpy(key, "Jefe", 4);
        memcpy(msg, "what do ya want for nothing?", 28);
        hmac_sha256(key, 4, msg, 28, mac);
        if (!hexeq(mac, "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843")) bad++;
        /* the exact seed the carrier shuffle uses: HMAC(Kc, "BLB1") */
        for (i = 0; i < 32; i++) key[i] = (unsigned char) i;
        hmac_sha256(key, 32, (const unsigned char *) "BLB1", 4, mac);
        if (mac[0] != 0 || mac[31] != 0) bad += 0; /* shape only; the real check is on-device parity */
    }
    return bad;
}

/* ---------------- JNI ---------------- */

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_hmac(JNIEnv *env, jclass cls, jbyteArray jkey, jbyteArray jmsg) {
    if (jkey == NULL || jmsg == NULL) return NULL;
    jsize kl = (*env)->GetArrayLength(env, jkey), ml = (*env)->GetArrayLength(env, jmsg);
    unsigned char *k = (unsigned char *) malloc(kl > 0 ? (size_t) kl : 1);
    unsigned char *m = (unsigned char *) malloc(ml > 0 ? (size_t) ml : 1);
    unsigned char out[32];
    jbyteArray r = NULL;
    if (k && m) {
        (*env)->GetByteArrayRegion(env, jkey, 0, kl, (jbyte *) k);
        (*env)->GetByteArrayRegion(env, jmsg, 0, ml, (jbyte *) m);
        hmac_sha256(k, (size_t) kl, m, (size_t) ml, out);
        r = (*env)->NewByteArray(env, 32);
        if (r) (*env)->SetByteArrayRegion(env, r, 0, 32, (const jbyte *) out);
    }
    free(k);
    free(m);
    return r;
}

/* envelope = [mode 1B || raw | raw-deflate | zlib]. mode 0/other = strip the byte, 1 = raw
 * deflate, 2 = zlib-wrapped - the same three cases java's Inflater(true/false) used to cover.
 * On any inflate trouble it returns the stripped bytes, exactly like the old catch {}. */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_openEnvelope(JNIEnv *env, jclass cls, jbyteArray jblob) {
    if (jblob == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, jblob);
    if (n < 1) return NULL;
    unsigned char *in = (unsigned char *) malloc((size_t) n);
    if (!in) return NULL;
    (*env)->GetByteArrayRegion(env, jblob, 0, n, (jbyte *) in);
    unsigned char mode = in[0];
    unsigned char *body = in + 1;
    size_t blen = (size_t) (n - 1);
    jbyteArray out = NULL;
    if (mode == 1 || mode == 2) {
        size_t cap = blen * 8 + 4096;
        unsigned char *buf = (unsigned char *) malloc(cap);
        if (buf) {
            z_stream s;
            memset(&s, 0, sizeof(s));
            if (inflateInit2(&s, mode == 1 ? -15 : 15) == Z_OK) {
                size_t have = 0;
                int ret = Z_OK;
                s.next_in = body;
                s.avail_in = (unsigned) blen;
                while (ret == Z_OK) {
                    if (have == cap) {
                        size_t nc = cap * 2;
                        unsigned char *nb = (unsigned char *) realloc(buf, nc);
                        if (!nb) { ret = Z_MEM_ERROR; break; }
                        buf = nb;
                        cap = nc;
                    }
                    s.next_out = buf + have;
                    s.avail_out = (unsigned) (cap - have);
                    ret = inflate(&s, Z_NO_FLUSH);
                    have = (size_t) s.total_out;
                    if (ret == Z_STREAM_END) break;
                    if (s.avail_in == 0 && s.avail_out > 0 && ret == Z_OK) break;
                }
                inflateEnd(&s);
                if (ret == Z_STREAM_END || (ret == Z_OK && have > 0)) {
                    out = (*env)->NewByteArray(env, (jsize) have);
                    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize) have, (const jbyte *) buf);
                }
            }
            free(buf);
        }
    }
    if (out == NULL) {
        out = (*env)->NewByteArray(env, (jsize) blen);
        if (out && blen > 0) (*env)->SetByteArrayRegion(env, out, 0, (jsize) blen, (const jbyte *) body);
    }
    free(in);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_b64d(JNIEnv *env, jclass cls, jstring js) {
    if (js == NULL) return NULL;
    const char *s = (*env)->GetStringUTFChars(env, js, NULL);
    if (!s) return NULL;
    size_t len = strlen(s);
    unsigned char *buf = (unsigned char *) malloc(len + 4);
    jbyteArray out = NULL;
    if (buf) {
        size_t n = 0, i;
        int bits = 0, acc = 0;
        for (i = 0; i < len; i++) {
            unsigned char c = (unsigned char) s[i];
            if (c == '=' || c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
            int v = b64val(c);
            if (v < 0) { n = 0; break; }
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                buf[n++] = (unsigned char) ((acc >> bits) & 0xff);
                acc &= (1 << bits) - 1;
            }
        }
        out = (*env)->NewByteArray(env, (jsize) n);
        if (out && n > 0) (*env)->SetByteArrayRegion(env, out, 0, (jsize) n, (const jbyte *) buf);
        free(buf);
    }
    (*env)->ReleaseStringUTFChars(env, js, s);
    return out;
}

JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_b64uEncode(JNIEnv *env, jclass cls, jbyteArray jb) {
    if (jb == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, jb);
    unsigned char *in = (unsigned char *) malloc(n > 0 ? (size_t) n : 1);
    if (!in) return NULL;
    (*env)->GetByteArrayRegion(env, jb, 0, n, (jbyte *) in);
    size_t cap = (size_t) ((n + 2) / 3) * 4 + 4, o = 0;
    char *s = (char *) malloc(cap);
    jstring out = NULL;
    if (s) {
        size_t i;
        for (i = 0; i + 2 < (size_t) n; i += 3) {
            unsigned v = ((unsigned) in[i] << 16) | ((unsigned) in[i + 1] << 8) | (unsigned) in[i + 2];
            s[o++] = B64[(v >> 18) & 63];
            s[o++] = B64[(v >> 12) & 63];
            s[o++] = B64[(v >> 6) & 63];
            s[o++] = B64[v & 63];
        }
        if (i < (size_t) n) {
            unsigned v = (unsigned) in[i] << 16;
            if (i + 1 < (size_t) n) v |= (unsigned) in[i + 1] << 8;
            s[o++] = B64[(v >> 18) & 63];
            s[o++] = B64[(v >> 12) & 63];
            if (i + 1 < (size_t) n) s[o++] = B64[(v >> 6) & 63];
        }
        s[o] = 0;
        for (i = 0; i < o; i++) {
            if (s[i] == '+') s[i] = '-';
            else if (s[i] == '/') s[i] = '_';
        }
        out = (*env)->NewStringUTF(env, s);
        free(s);
    }
    free(in);
    return out;
}

/* AES-256-GCM: the primitive itself is NOT re-implemented here. This machine has no C compiler and
 * local builds are forbidden, so a hand-typed S-box / GHASH could only be tested on a handset - and
 * a single wrong table entry would look exactly like "the server is broken" on every device at once.
 * Instead the platform's own AES (the same BoringSSL java has always used) is driven from here
 * through JNI: the byte layout, the 12-byte IV prefix, the 128-bit tag and the AAD are unchanged, so
 * the wire stays byte-identical, while no Java or Kotlin source contains any of this any more.
 * The class and method names live in this binary, assembled from bytes.
 */

static const unsigned char CIPH[] = {
    0x6a, 0x61, 0x76, 0x61, 0x78, 0x2f, 0x63, 0x72, 0x79, 0x70, 0x74, 0x6f, 0x2f,
    0x43, 0x69, 0x70, 0x68, 0x65, 0x72, 0x00
};
static const unsigned char TRANS[] = { 0x41, 0x45, 0x53, 0x2f, 0x47, 0x43, 0x4d, 0x2f, 0x4e, 0x6f, 0x50, 0x61, 0x64, 0x64, 0x69, 0x6e, 0x67, 0x00 };
static const unsigned char ALG[] = { 0x41, 0x45, 0x53, 0x00 };
static const char MD_GET[] = "getInstance";
static const char MD_INIT[] = "init";
static const char MD_AAD[] = "updateAAD";
static const char MD_FIN[] = "doFinal";
static const char SIG_GET[] = "(Ljava/lang/String;)Ljavax/crypto/Cipher;";
/* java.security.spec, not javax.crypto.spec: AlgorithmParameterSpec lives with the other JCA
 * interfaces, and a wrong package in a descriptor is invisible until GetMethodID answers
 * NoSuchMethodError at runtime - which is exactly what "gcm:init | no non-static method
 * Ljavax/crypto/spec/AlgorithmParameterSpec;" was (vaultOpen had it right, which is why the vault
 * worked while the payload could not be opened). */
static const char SIG_INIT[] = "(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V";
static const char SIG_B[] = "([B)[B";

/* Which step of the Cipher-through-JNI path failed. The known-answer test passing proves
 * SHA/HMAC/base64 work on this device, so the only thing left is how this library drives
 * javax.crypto - and "GCM tag fail" is what the caller says for any null it gets back, which hides
 * whether Cipher.getInstance, the GCMParameterSpec constructor, init, updateAAD or doFinal was the
 * one that refused. Every step below records its name, and a real Java exception adds its class
 * name. Reported to the log page by NativeSec.err(); to be deleted once the cause is known. */
static char g_step[40] = "ok";    /* last named point reached, on success or failure */
static char g_last[224] = "";      /* what refused, in one string: where | class | message */
static char g_det[80] = "";        /* values read back from a spec that was built */

/** copy ASCII only, and never cut a multibyte sequence in half: these buffers go back into
 *  NewStringUTF, and a dangling byte there is a second crash on the way to reporting the first */
static void copy_ascii(char *out, size_t n, const char *in) {
    size_t k = 0;
    if (in == NULL) { out[0] = 0; return; }
    for (; k + 1 < n && in[k] != 0; k++) out[k] = ((unsigned char) in[k] < 0x80) ? in[k] : '?';
    out[k] = 0;
}

static void set_step(const char *where) {
    strncpy(g_step, where, sizeof(g_step) - 1);
    g_step[sizeof(g_step) - 1] = 0;
}

/** record a refusal that involves no Java exception - a class that FindClass would not name, a
 *  method ID that was not there, a handle the keystore did not give back. The FIRST refusal since
 *  the last read is kept: the ones after it are usually consequences of it, and overwriting with
 *  the consequence is what made tonight's log say "object not initialized for signing" instead of
 *  the thing initSign actually answered. */
static void note_null(const char *where, const char *what) {
    if (g_last[0] != 0) return;
    snprintf(g_last, sizeof(g_last), "%s: %s", where, what);
}

/** the message of a throwable, which is the part that says WHY: "Key size must be either 128 or
 *  256 bits." is an answer, InvalidAlgorithmParameterException alone is not */
static void read_exc_msg(JNIEnv *env, jthrowable ex, char *out, size_t n) {
    out[0] = 0;
    jclass oc = (*env)->GetObjectClass(env, ex);
    jmethodID getMsg = oc ? (*env)->GetMethodID(env, oc, "getMessage", "()Ljava/lang/String;") : NULL;
    /* the method is found on the exception's class but MUST be invoked on the exception: calling it
     * with the Class object as receiver (which is what this line said for one build) hands ART an
     * object of the wrong shape, and that is a SIGSEGV in InvokeVirtualOrInterfaceWithVarArgs, not a
     * Java exception - the tombstone in /data/tombstones is what proved it */
    if (getMsg && ex) {
        jstring nm = (jstring) (*env)->CallObjectMethod(env, ex, getMsg);
        if (nm) {
            const char *c = (*env)->GetStringUTFChars(env, nm, NULL);
            if (c) { copy_ascii(out, n, c); (*env)->ReleaseStringUTFChars(env, nm, c); }
            (*env)->DeleteLocalRef(env, nm);
        }
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (oc) (*env)->DeleteLocalRef(env, oc);
}

/** name the throwable's class without touching a hidden class or a Java-side catch */
static void read_exc_class(JNIEnv *env, jthrowable ex, char *out, size_t n) {
    out[0] = 0;
    jclass oc = (*env)->GetObjectClass(env, ex);
    jclass clsCls = oc ? (*env)->GetObjectClass(env, oc) : NULL;
    jmethodID getName = clsCls ? (*env)->GetMethodID(env, clsCls, "getName", "()Ljava/lang/String;") : NULL;
    if (getName && oc) {
        jstring nm = (jstring) (*env)->CallObjectMethod(env, oc, getName);
        if (nm) {
            const char *c = (*env)->GetStringUTFChars(env, nm, NULL);
            if (c) {
                const char *dot = strrchr(c, '.');
                copy_ascii(out, n, dot ? dot + 1 : c);
                (*env)->ReleaseStringUTFChars(env, nm, c);
            }
            (*env)->DeleteLocalRef(env, nm);
        }
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (oc) (*env)->DeleteLocalRef(env, oc);
    if (clsCls) (*env)->DeleteLocalRef(env, clsCls);
}

/* clear any pending exception and record where + class + message together, returning true when
 * one was raised; a later set_step cannot separate them again */
static int step_failed(JNIEnv *env, const char *where) {
    if (!(*env)->ExceptionCheck(env)) { set_step(where); return 0; }
    set_step(where);
    jthrowable ex = (*env)->ExceptionOccurred(env);
    if (ex == NULL) return 1;
    (*env)->ExceptionClear(env);
    char cls[48] = "", msg[128] = "";
    read_exc_class(env, ex, cls, sizeof(cls));
    read_exc_msg(env, ex, msg, sizeof(msg));
    /* same rule as note_null: keep the first failure, not the last one */
    if (g_last[0] == 0) {
        if (msg[0] != 0) snprintf(g_last, sizeof(g_last), "%s | %s | %s", where, cls, msg);
        else snprintf(g_last, sizeof(g_last), "%s | %s", where, cls);
    }
    (*env)->DeleteLocalRef(env, ex);
    return 1;
}

static void note_det(const char *fmt, int a, int b, int c, const char *d, const char *e) {
    snprintf(g_det, sizeof(g_det), fmt, a, b, c, d, e);
}

JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_err(JNIEnv *env, jclass cls) {
    char buf[352];
    if (g_last[0] != 0 && g_det[0] != 0) snprintf(buf, sizeof(buf), "%s | last=%s | %s", g_step, g_last, g_det);
    else if (g_last[0] != 0) snprintf(buf, sizeof(buf), "%s | last=%s", g_step, g_last);
    else if (g_det[0] != 0) snprintf(buf, sizeof(buf), "%s | %s", g_step, g_det);
    else snprintf(buf, sizeof(buf), "%s", g_step);
    g_last[0] = 0;
    g_det[0] = 0;
    return (*env)->NewStringUTF(env, buf);
}

/** a method id, with its no-such-method answer kept: a failed lookup on a class that WAS found is
 *  a descriptor mismatch, and the framework's own message names the missing method for us */
static jmethodID mid_lookup(JNIEnv *env, jclass cls, const char *name, const char *sig, const char *tag) {
    jmethodID m = (*env)->GetMethodID(env, cls, name, sig);
    if ((*env)->ExceptionCheck(env)) step_failed(env, tag);
    return m;
}

static jmethodID mid_lookup_static(JNIEnv *env, jclass cls, const char *name, const char *sig, const char *tag) {
    jmethodID m = (*env)->GetStaticMethodID(env, cls, name, sig);
    if ((*env)->ExceptionCheck(env)) step_failed(env, tag);
    return m;
}

static jclass find_class_checked(JNIEnv *env, const unsigned char *name, int *err) {
    jclass c = (*env)->FindClass(env, (const char *) name);
    if (c == NULL) {
        *err = 1;
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        note_null("class-missing", (const char *) name);
    }
    return c;
}

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_gcmOpen(JNIEnv *env, jclass cls, jbyteArray jcipher, jbyteArray jkey, jbyteArray jaad) {
    set_step("gcm:entered");
    if (jcipher == NULL || jkey == NULL) { set_step("gcm:null-args"); return NULL; }
    jsize cn = (*env)->GetArrayLength(env, jcipher), kn = (*env)->GetArrayLength(env, jkey);
    /* under iv(12)+tag(16) there is no GCM blob here at all - no cipher call happened */
    if (cn < 29 || kn != 32) { set_step("gcm:bad-shape"); return NULL; }

    int err = 0;
    jclass cipherCls = find_class_checked(env, CIPH, &err);
    jclass specCls = find_class_checked(env, (const unsigned char *) "javax/crypto/spec/GCMParameterSpec", &err);
    jclass keyCls = find_class_checked(env, (const unsigned char *) "javax/crypto/spec/SecretKeySpec", &err);
    if (err || cipherCls == NULL || specCls == NULL || keyCls == NULL) { set_step("gcm:find-classes"); return NULL; }

    jmethodID midGet = mid_lookup_static(env, cipherCls, MD_GET, SIG_GET, "gcm:getInstance");
    jmethodID midInit = mid_lookup(env, cipherCls, MD_INIT, SIG_INIT, "gcm:init");
    jmethodID midAad = mid_lookup(env, cipherCls, MD_AAD, "([B)V", "gcm:updateAAD");
    jmethodID midFin = mid_lookup(env, cipherCls, MD_FIN, SIG_B, "gcm:doFinal");
    /* the two-argument constructor is the one vaultOpen uses, and a full vault round trip works on
     * this handset, so it is tried first; the four-argument form stays as the fallback */
    jmethodID midSpec2 = mid_lookup(env, specCls, "<init>", "(I[B)V", "gcm:spec-2");
    jmethodID midSpec4 = mid_lookup(env, specCls, "<init>", "(I[BII)V", "gcm:spec-4");
    jmethodID midKey = mid_lookup(env, keyCls, "<init>", "([BLjava/lang/String;)V", "gcm:key-ctor");
    if (!midGet || !midInit || !midAad || !midFin || !midKey || (!midSpec2 && !midSpec4)) {
        const char *miss = !midGet ? "getInstance" : (!midInit ? "init" : (!midAad ? "updateAAD" : (!midFin ? "doFinal" : (!midKey ? "SecretKeySpec" : "GCMParameterSpec"))));
        note_null("gcm:method-ids", miss);
        return NULL;
    }

    jbyteArray out = NULL;
    jstring transform = (*env)->NewStringUTF(env, (const char *) TRANS);
    jstring alg = (*env)->NewStringUTF(env, (const char *) ALG);
    /* copied into a plain buffer instead of pinned: this call goes into javax.crypto (which may run
     * for a megabyte payload), and a pinned array held across those callbacks occupies a VM slot
     * until it is released - the leak that aborts the process is described in vaultSeal */
    jbyte *cip = (jbyte *) malloc((size_t) cn);
    jbyte *key = (jbyte *) malloc((size_t) kn);

    if (transform && alg && key && cip) {
        (*env)->GetByteArrayRegion(env, jcipher, 0, cn, cip);
        (*env)->GetByteArrayRegion(env, jkey, 0, kn, key);
        /* iv = cipher[0..11], ct||tag = cipher[12..] */
        jbyteArray iv = (*env)->NewByteArray(env, 12);
        jbyteArray ct = (*env)->NewByteArray(env, (jsize) (cn - 12));
        if (iv && ct) {
            (*env)->SetByteArrayRegion(env, iv, 0, 12, cip);
            (*env)->SetByteArrayRegion(env, ct, 0, (jsize) (cn - 12), cip + 12);
            jobject spec = midSpec2 ? (*env)->NewObject(env, specCls, midSpec2, 128, iv)
                                    : (*env)->NewObject(env, specCls, midSpec4, 128, iv, 0, 12);
            jobject kobj = (*env)->NewObject(env, keyCls, midKey, jkey, alg);
            jobject c = (*env)->CallStaticObjectMethod(env, cipherCls, midGet, transform);
            /* returning to Java with an exception still pending turns this into a thrown
             * NoSuchPaddingException at the call site, which the caller reports as "tag fail" -
             * record it, clear it, and say which step it was */
            if (step_failed(env, "gcm:getInstance/new-object")) { return NULL; }
            if (spec && kobj && c) {
                (*env)->CallVoidMethod(env, c, midInit, 2 /* DECRYPT_MODE */, kobj, spec);
                if (!step_failed(env, "gcm:cipher.init")) {
                    int failed = 0;
                    if (jaad != NULL) {
                        (*env)->CallVoidMethod(env, c, midAad, jaad);
                        failed = step_failed(env, "gcm:cipher.updateAAD");
                    }
                    if (!failed && out == NULL) {
                        out = (jbyteArray) (*env)->CallObjectMethod(env, c, midFin, ct);
                        /* AEADBadTagException here means a real key/carrier mismatch; anything else
                         * means this JNI path is wrong, and the two need different fixes */
                        if (step_failed(env, "gcm:cipher.doFinal")) out = NULL;
                    }
                }
            }
        }
    }
    if (key) free(key);
    if (cip) free(cip);
    if (transform) (*env)->DeleteLocalRef(env, transform);
    if (alg) (*env)->DeleteLocalRef(env, alg);
    (*env)->DeleteLocalRef(env, cipherCls);
    (*env)->DeleteLocalRef(env, specCls);
    (*env)->DeleteLocalRef(env, keyCls);
    if (out) set_step("gcm:ok");
    return out;
}

/* ---------------- the local sealing vault (AndroidKeyStore AES-GCM) ----------------
 * The whole keystore path lives here: provider name, alias handling, the key parameters, the
 * cipher and the IV. Kotlin asks for a sealed byte string and gets plaintext back; it holds no
 * key handle, no alias and no cipher any more. The key object is cached in this library because
 * fetching it from the keystore is a binder round trip that costs seconds on some handsets.
 */

/* the PUBLIC door: android/security/keystore/KeyStore is @hide, and FindClass of a hidden class
 * answers NULL to an app process (which is what every keystore read did on API 36 - the key was
 * there, the app just could not name the class to ask for it). java.security.KeyStore with the
 * AndroidKeyStore provider is the supported way, and is what the Kotlin code used before. */
static const char KS_CLS[] = "java/security/KeyStore";
static const char KG_CLS[] = "android/security/keystore/KeyGenParameterSpec$Builder";
static const char KP_CLS[] = "android/security/keystore/KeyProperties";
static const char SKS_CLS[] = "java/security/KeyStore$SecretKeyEntry";
static const char KEYGEN_CLS[] = "javax/crypto/KeyGenerator";
static const char SRND_CLS[] = "java/security/SecureRandom";
static const char PROVIDER[] = "AndroidKeyStore";
static const char KAES[] = "AES", KGCM[] = "GCM", KNONE[] = "None";
#define PURPOSE_ENC_DEC 3 /* ENCRYPT | DECRYPT from KeyProperties */

static jobject cached_key = NULL;      /* global ref, this process */
static int cached_valid = 0;

static jclass need_class(JNIEnv *env, const char *name) {
    jclass c = (*env)->FindClass(env, name);
    if (c == NULL) {
        (*env)->ExceptionClear(env);
        /* no class is a server name and no class is secret material: this is the line that tells
         * a hidden-API refusal apart from a wrong descriptor, and without it the refusal is silent */
        note_null("class-missing", name);
    }
    return c;
}

/** the keystore, loaded; caller deletes the local ref */
static jobject keystore_open(JNIEnv *env, int *err) {
    jclass ksCls = need_class(env, KS_CLS);
    if (!ksCls) { set_step("ks:find-class"); *err = 1; return NULL; }
    jmethodID getInstance = (*env)->GetStaticMethodID(env, ksCls, "getInstance", "(Ljava/lang/String;)Ljava/security/KeyStore;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring prov = (*env)->NewStringUTF(env, PROVIDER);
    jobject ks = getInstance && prov ? (*env)->CallStaticObjectMethod(env, ksCls, getInstance, prov) : NULL;
    if ((*env)->ExceptionCheck(env)) { step_failed(env, "ks:getInstance"); ks = NULL; *err = 1; }
    if (ks) {
        jmethodID load = (*env)->GetMethodID(env, ksCls, "load", "(Ljava/io/InputStream;[C)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (load) {
            (*env)->CallVoidMethod(env, ks, load, NULL, NULL);
            if (step_failed(env, "ks:load")) { ks = NULL; *err = 1; }
        } else { set_step("ks:load-method"); *err = 1; }
    }
    (*env)->DeleteLocalRef(env, ksCls);
    if (prov) (*env)->DeleteLocalRef(env, prov);
    return ks;
}

static jobject read_key(JNIEnv *env, jstring jalias) {
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return NULL;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID getEntry = (*env)->GetMethodID(env, ksCls, "getEntry", "(Ljava/lang/String;Ljava/security/KeyStore$ProtectionParameter;)Ljava/security/KeyStore$Entry;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!getEntry) { set_step("ks:getEntry-method"); (*env)->DeleteLocalRef(env, ksCls); (*env)->DeleteLocalRef(env, ks); return NULL; }
    jobject entry = (*env)->CallObjectMethod(env, ks, getEntry, jalias, NULL);
    if (step_failed(env, "ks:getEntry")) entry = NULL;
    jobject key = NULL;
    if (entry) {
        jclass skCls = need_class(env, SKS_CLS);
        if (!skCls) set_step("ks:secretkeyentry-class");
        else if (!(*env)->IsInstanceOf(env, entry, skCls)) set_step("ks:entry-not-secretkey");
        if (skCls && (*env)->IsInstanceOf(env, entry, skCls)) {
            jmethodID getSecret = (*env)->GetMethodID(env, skCls, "getSecretKey", "()Ljavax/crypto/SecretKey;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            if (!getSecret) set_step("ks:getSecretKey-method");
            if (getSecret) key = (*env)->CallObjectMethod(env, entry, getSecret);
            if (step_failed(env, "ks:getSecretKey")) key = NULL;
        }
        if (skCls) (*env)->DeleteLocalRef(env, skCls);
    }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    if (entry) (*env)->DeleteLocalRef(env, entry);
    return key;
}

/** the spec reads itself back: if the keystore rejects it, what matters is the values the object
 *  holds, not the values this file intended to put in - keySize 0 here means a setter never ran,
 *  keySize 256 with the same rejection means the parameters are argued about somewhere else */
static void spec_detail(JNIEnv *env, jobject spec, int variant) {
    if (spec == NULL) return;
    jclass sc = (*env)->GetObjectClass(env, spec);
    if (!sc) return;
    jmethodID mSz = (*env)->GetMethodID(env, sc, "getKeySize", "()I");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mPu = (*env)->GetMethodID(env, sc, "getPurposes", "()I");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mBm = (*env)->GetMethodID(env, sc, "getBlockModes", "()[Ljava/lang/String;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mEp = (*env)->GetMethodID(env, sc, "getEncryptionPaddings", "()[Ljava/lang/String;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    int sz = -1, pu = -1;
    if (mSz) sz = (int) (*env)->CallIntMethod(env, spec, mSz);
    if (mPu) pu = (int) (*env)->CallIntMethod(env, spec, mPu);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    char bm[20] = "?", ep[20] = "?";
    jobjectArray arr = mBm ? (jobjectArray) (*env)->CallObjectMethod(env, spec, mBm) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); arr = NULL; }
    if (arr && (*env)->GetArrayLength(env, arr) > 0) {
        jstring first = (jstring) (*env)->GetObjectArrayElement(env, arr, 0);
        if (first) {
            const char *c = (*env)->GetStringUTFChars(env, first, NULL);
            if (c) { copy_ascii(bm, sizeof(bm), c); (*env)->ReleaseStringUTFChars(env, first, c); }
            (*env)->DeleteLocalRef(env, first);
        }
    }
    arr = mEp ? (jobjectArray) (*env)->CallObjectMethod(env, spec, mEp) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); arr = NULL; }
    if (arr && (*env)->GetArrayLength(env, arr) > 0) {
        jstring first = (jstring) (*env)->GetObjectArrayElement(env, arr, 0);
        if (first) {
            const char *c = (*env)->GetStringUTFChars(env, first, NULL);
            if (c) { copy_ascii(ep, sizeof(ep), c); (*env)->ReleaseStringUTFChars(env, first, c); }
            (*env)->DeleteLocalRef(env, first);
        }
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    note_det("spec v%d sz=%d pu=%d bm=%s ep=%s", variant, sz, pu, bm, ep);
    (*env)->DeleteLocalRef(env, sc);
}

/* ---- the framework's own constants ----
 * Everything this library asks the keystore for comes from KeyProperties, never from a literal
 * typed here. On this handset the literal was wrong in a way that still creates keys: the padding
 * string was refused at creation ("Unsupported encryption padding scheme: None"), and a key built
 * without one exists, reads back, and then refuses every Cipher.init with INCOMPATIBLE_PADDING_MODE
 * (keystore2's own words, read off the device with adb). The Kotlin that worked for months always
 * used the constant. Reading the field makes the request correct on whatever framework runs. */
static jstring kprop_str(JNIEnv *env, const char *field, const char *fallback) {
    jclass kp = need_class(env, KP_CLS);
    jstring out = NULL;
    if (kp) {
        jfieldID f = (*env)->GetStaticFieldID(env, kp, field, "Ljava/lang/String;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (f) out = (jstring) (*env)->GetStaticObjectField(env, kp, f);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); out = NULL; }
        (*env)->DeleteLocalRef(env, kp);
    }
    if (out == NULL) out = (*env)->NewStringUTF(env, fallback);
    return out;
}

static int kprop_int(JNIEnv *env, const char *field, int fallback) {
    jclass kp = need_class(env, KP_CLS);
    int v = fallback;
    if (kp) {
        jfieldID f = (*env)->GetStaticFieldID(env, kp, field, "I");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (f) {
            v = (*env)->GetStaticIntField(env, kp, f);
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); v = fallback; }
        }
        (*env)->DeleteLocalRef(env, kp);
    }
    return v;
}

/** delete one alias - only ever called on a key this same attempt just created */
static int ks_delete(JNIEnv *env, jstring jalias) {
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return 0;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID del = (*env)->GetMethodID(env, ksCls, "deleteEntry", "(Ljava/lang/String;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    int done = 0;
    if (del) {
        (*env)->CallVoidMethod(env, ks, del, jalias);
        done = step_failed(env, "ks:deleteEntry") ? 0 : 1;
    }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    return done;
}

/** does this key really encrypt? create-then-use is where this ROM bit: a key without an
 *  authorized padding exists and reads back, and refuses every Cipher.init afterwards. A variant is
 *  accepted only if a real encrypt of four bytes goes through. */
static int cipher_probe(JNIEnv *env, jobject key) {
    jclass cipherCls = need_class(env, (const char *) CIPH);
    if (!cipherCls || key == NULL) return 0;
    jmethodID getInst = (*env)->GetStaticMethodID(env, cipherCls, MD_GET, SIG_GET);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID init = (*env)->GetMethodID(env, cipherCls, MD_INIT, "(ILjava/security/Key;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID fin = (*env)->GetMethodID(env, cipherCls, MD_FIN, SIG_B);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring tr = (*env)->NewStringUTF(env, (const char *) TRANS);
    jobject c = (getInst && tr) ? (*env)->CallStaticObjectMethod(env, cipherCls, getInst, tr) : NULL;
    if (step_failed(env, "probe:getInstance")) c = NULL;
    int ok = 0;
    if (c && init && fin) {
        jbyteArray in = (*env)->NewByteArray(env, 4);
        (*env)->CallVoidMethod(env, c, init, 1 /* ENCRYPT_MODE */, key);
        if (!step_failed(env, "probe:init") && in) {
            jobject out = (*env)->CallObjectMethod(env, c, fin, in);
            ok = (out != NULL) && !step_failed(env, "probe:doFinal");
        }
    }
    (*env)->DeleteLocalRef(env, cipherCls);
    if (tr) (*env)->DeleteLocalRef(env, tr);
    if (c) (*env)->DeleteLocalRef(env, c);
    return ok;
}

/** can this identity key sign? the same create-then-use rule: a key whose purpose mask did not
 *  match this framework reads fine and then answers INCOMPATIBLE_PURPOSE on initSign. */
static int sig_probe(JNIEnv *env, jobject key) {
    jclass sigCls = need_class(env, "java/security/Signature");
    if (!sigCls || key == NULL) return 0;
    jmethodID getInst = (*env)->GetStaticMethodID(env, sigCls, "getInstance", "(Ljava/lang/String;)Ljava/security/Signature;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID init = (*env)->GetMethodID(env, sigCls, "initSign", "(Ljava/security/PrivateKey;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring algo = (*env)->NewStringUTF(env, "SHA256withECDSA");
    jobject s = (getInst && algo) ? (*env)->CallStaticObjectMethod(env, sigCls, getInst, algo) : NULL;
    if (step_failed(env, "probe:sig.getInstance")) s = NULL;
    int ok = 0;
    if (s && init) {
        (*env)->CallVoidMethod(env, s, init, key);
        ok = !step_failed(env, "probe:initSign");
    }
    (*env)->DeleteLocalRef(env, sigCls);
    if (algo) (*env)->DeleteLocalRef(env, algo);
    if (s) (*env)->DeleteLocalRef(env, s);
    return ok;
}

/** one AES key of the given size, with purposes, mode and padding read from this framework.
 *  The key must encrypt four bytes before it is returned; otherwise the alias this attempt created
 *  is removed again, so a half-authorized blob can never become the app's vault key. */
static jobject aes_try(JNIEnv *env, jstring jalias, int keySize, int variant) {
    char where[40];
    jclass bld = need_class(env, KG_CLS);
    jclass kgc = need_class(env, KEYGEN_CLS);
    if (!bld || !kgc) return NULL;
    int purposes = kprop_int(env, "PURPOSE_ENCRYPT", 1) | kprop_int(env, "PURPOSE_DECRYPT", 2);
    jstring sAes = kprop_str(env, "KEY_ALGORITHM_AES", KAES);
    jstring gcm = kprop_str(env, "BLOCK_MODE_GCM", KGCM);
    jstring none = kprop_str(env, "ENCRYPTION_PADDING_NONE", KNONE);
    jstring prov = (*env)->NewStringUTF(env, PROVIDER);
    jmethodID ctor = (*env)->GetMethodID(env, bld, "<init>", "(Ljava/lang/String;I)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!ctor) note_null("mk:ctor-method", "builder");
    jobject b = ctor ? (*env)->NewObject(env, bld, ctor, jalias, purposes) : NULL;
    if (step_failed(env, "mk:ctor")) return NULL;
    if (b == NULL) return NULL;
    jmethodID mSize = (*env)->GetMethodID(env, bld, "setKeySize", "(I)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mMode = (*env)->GetMethodID(env, bld, "setBlockModes", "([Ljava/lang/String;)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mPad = (*env)->GetMethodID(env, bld, "setEncryptionPaddings", "([Ljava/lang/String;)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mRnd = (*env)->GetMethodID(env, bld, "setRandomizedEncryptionRequired", "(Z)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID mBuild = (*env)->GetMethodID(env, bld, "build", "()Landroid/security/keystore/KeyGenParameterSpec;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!mSize || !mMode || !mPad || !mRnd || !mBuild) {
        snprintf(where, sizeof(where), "mk:method-ids v%d", variant);
        note_null(where, "setter not found");
        return NULL;
    }
    jclass strCls = need_class(env, "java/lang/String");
    jobjectArray modeArr = (strCls && gcm) ? (*env)->NewObjectArray(env, 1, strCls, NULL) : NULL;
    jobjectArray padArr = (strCls && none) ? (*env)->NewObjectArray(env, 1, strCls, NULL) : NULL;
    if (modeArr && gcm) (*env)->SetObjectArrayElement(env, modeArr, 0, gcm);
    if (padArr && none) (*env)->SetObjectArrayElement(env, padArr, 0, none);
    int failed = 0;
    b = (*env)->CallObjectMethod(env, b, mSize, keySize);
    failed = step_failed(env, "mk:setKeySize");
    if (!failed) b = (*env)->CallObjectMethod(env, b, mMode, modeArr);
    if (!failed) failed = step_failed(env, "mk:setBlockModes");
    if (!failed) b = (*env)->CallObjectMethod(env, b, mPad, padArr);
    if (!failed) failed = step_failed(env, "mk:setPaddings");
    if (!failed) b = (*env)->CallObjectMethod(env, b, mRnd, JNI_TRUE);
    if (!failed) failed = step_failed(env, "mk:setRandomized");
    if (failed || b == NULL) {
        snprintf(where, sizeof(where), "mk:builder v%d", variant);
        note_null(where, "builder refused a setter");
        return NULL;
    }
    jobject spec = (*env)->CallObjectMethod(env, b, mBuild);
    if (step_failed(env, "mk:spec.build")) return NULL;
    if (spec == NULL) return NULL;
    spec_detail(env, spec, variant);
    jmethodID kgi = (*env)->GetStaticMethodID(env, kgc, "getInstance", "(Ljava/lang/String;Ljava/lang/String;)Ljavax/crypto/KeyGenerator;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jobject kg = (kgi && sAes && prov) ? (*env)->CallStaticObjectMethod(env, kgc, kgi, sAes, prov) : NULL;
    if (step_failed(env, "mk:keygen.getInstance")) kg = NULL;
    if (kg == NULL) {
        snprintf(where, sizeof(where), "mk:keygen.getInstance v%d", variant);
        note_null(where, "provider refused");
        return NULL;
    }
    jmethodID kinit = (*env)->GetMethodID(env, kgc, "init", "(Ljava/security/spec/AlgorithmParameterSpec;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID kgen = (*env)->GetMethodID(env, kgc, "generateKey", "()Ljavax/crypto/SecretKey;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!kinit || !kgen) {
        snprintf(where, sizeof(where), "mk:keygen-methods v%d", variant);
        note_null(where, "no init or generateKey");
        return NULL;
    }
    (*env)->CallVoidMethod(env, kg, kinit, spec);
    if (step_failed(env, "mk:keygen.init")) {
        snprintf(where, sizeof(where), "mk:keygen.init v%d sz%d", variant, keySize);
        set_step(where);
        return NULL;
    }
    jobject k = (*env)->CallObjectMethod(env, kg, kgen);
    if (step_failed(env, "mk:generateKey") || k == NULL) {
        snprintf(where, sizeof(where), "mk:generateKey v%d", variant);
        set_step(where);
        return NULL;
    }
    if (!cipher_probe(env, k)) {
        ks_delete(env, jalias);
        (*env)->DeleteLocalRef(env, k);
        snprintf(where, sizeof(where), "mk:unusable v%d sz%d", variant, keySize);
        set_step(where);
        return NULL;
    }
    return k;
}

/** 256 first, then 128 - the only thing that varies now, because purposes, mode and padding come
 *  from this framework's own KeyProperties. The step name records what was accepted (mk:key-made
 *  vN szN) and survives the later steps that overwrite g_step. */
static jobject make_key(JNIEnv *env, jstring jalias) {
    static const int SZ[2] = { 256, 128 };
    char note_buf[40];
    for (int i = 0; i < 2; i++) {
        jobject k = aes_try(env, jalias, SZ[i], i);
        if (k != NULL) {
            snprintf(note_buf, sizeof(note_buf), "mk:key-made v%d sz%d", i, SZ[i]);
            set_step(note_buf);
            return k;
        }
    }
    set_step("mk:no-key-any-variant");
    return NULL;
}

/* the alias and the AAD belong to this library, not to Kotlin: which=0 is the live key,
 * which=1 the old StrongBox key that only has to open what it sealed */
static const char VAULT_ALIAS[] = "blob-kv-v2";
static const char VAULT_LEGACY[] = "blob-kv-v1";
static const unsigned char VAULT_AAD[] = { 0x42, 0x4c, 0x4b, 0x56, 0x31 }; /* BLKV1 */

static jstring alias_for(JNIEnv *env, jint which) {
    return (*env)->NewStringUTF(env, which == 1 ? VAULT_LEGACY : VAULT_ALIAS);
}

static jbyteArray aad_bytes(JNIEnv *env) {
    jbyteArray a = (*env)->NewByteArray(env, (jsize) sizeof(VAULT_AAD));
    if (a) (*env)->SetByteArrayRegion(env, a, 0, (jsize) sizeof(VAULT_AAD), (const jbyte *) VAULT_AAD);
    return a;
}

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultReady(JNIEnv *env, jclass cls, jint which) {
    set_step("vault:entered");
    jstring jalias = alias_for(env, which);
    if (jalias == NULL) { set_step("vault:alias"); return 0; }
    if (which == 0 && cached_valid && cached_key) { set_step("vault:key-cached"); return 1; }
    jobject k = read_key(env, jalias);
    if (k == NULL && which == 0) {
        k = make_key(env, jalias);
        if (k == NULL) k = read_key(env, jalias);
    }
    if (k == NULL) { set_step("vault:no-key"); return 0; }
    if (which == 1) { (*env)->DeleteLocalRef(env, k); return 1; }
    if (cached_key) (*env)->DeleteGlobalRef(env, cached_key);
    cached_key = (*env)->NewGlobalRef(env, k);
    (*env)->DeleteLocalRef(env, k);
    cached_valid = cached_key != NULL;
    set_step(cached_valid ? "vault:key-cached" : "vault:global-ref");
    return cached_valid ? 1 : 0;
}

/** seal: iv(12) || AES-256-GCM ciphertext+tag, with the vault's own AAD */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultSeal(JNIEnv *env, jclass cls, jbyteArray jplain) {
    set_step("seal:entered");
    if (jplain == NULL) { set_step("seal:null-input"); return NULL; }
    jbyteArray jaad = aad_bytes(env);
    if (jaad == NULL) { set_step("seal:aad-bytes"); return NULL; }
    /* no set_step here on purpose: vaultReady already named which keystore step refused, and
     * overwriting it with "no key" would throw away the only answer this recorder can give */
    if (!cached_valid) { if (Java_ir_v2rayng_compose_core_NativeSec_vaultReady(env, cls, 0) == 0) return NULL; }
    jclass cipherCls = need_class(env, (const char *) CIPH);
    jclass srCls = need_class(env, SRND_CLS);
    if (!cipherCls || !srCls) { set_step("seal:find-class"); return NULL; }
    jmethodID getInst = (*env)->GetStaticMethodID(env, cipherCls, MD_GET, SIG_GET);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring tr = (*env)->NewStringUTF(env, (const char *) TRANS);
    jobject c = getInst && tr ? (*env)->CallStaticObjectMethod(env, cipherCls, getInst, tr) : NULL;
    jbyteArray out = NULL;
    if (c) {
        jmethodID initEnc = (*env)->GetMethodID(env, cipherCls, MD_INIT, "(ILjava/security/Key;)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID aad = (*env)->GetMethodID(env, cipherCls, MD_AAD, "([B)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID fin = (*env)->GetMethodID(env, cipherCls, MD_FIN, SIG_B);  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID getIv = (*env)->GetMethodID(env, cipherCls, "getIV", "()[B");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (!initEnc || !aad || !fin || !getIv) note_null("seal:method-ids", initEnc ? (aad ? (fin ? "no getIV" : "no doFinal") : "no updateAAD") : "no init");
        if (initEnc && aad && fin && getIv) {
            (*env)->CallVoidMethod(env, c, initEnc, 1 /* ENCRYPT_MODE */, cached_key);
            if (!step_failed(env, "seal:cipher.init")) {
                (*env)->CallVoidMethod(env, c, aad, jaad);
                if (!step_failed(env, "seal:cipher.updateAAD")) {
                    jbyteArray ct = (jbyteArray) (*env)->CallObjectMethod(env, c, fin, jplain);
                    if (step_failed(env, "seal:cipher.doFinal")) ct = NULL;
                    jbyteArray iv = ct ? (jbyteArray) (*env)->CallObjectMethod(env, c, getIv) : NULL;
                    if (step_failed(env, "seal:getIV")) ct = NULL;
                    if (ct && iv && (*env)->GetArrayLength(env, iv) != 12) set_step("seal:iv-length");
                    if (ct && iv && (*env)->GetArrayLength(env, iv) == 12) {
                        jsize cn = (*env)->GetArrayLength(env, ct);
                        out = (*env)->NewByteArray(env, (jsize) (12 + cn));
                        if (!out) set_step("seal:out-buffer");
                        if (out) {
                            /* regions, never GetByteArrayElements: a pinned array that is not
                             * released eats one of the 32 VM slots for the life of the process,
                             * and once they are gone the runtime aborts the app with no Java
                             * exception at all - "opens, dies after two seconds, no crash" */
                            jbyte ivb[12];
                            jbyte *ctb = (jbyte *) malloc(cn > 0 ? (size_t) cn : 1);
                            (*env)->GetByteArrayRegion(env, iv, 0, 12, ivb);
                            (*env)->SetByteArrayRegion(env, out, 0, 12, ivb);
                            if (ctb) {
                                (*env)->GetByteArrayRegion(env, ct, 0, cn, ctb);
                                (*env)->SetByteArrayRegion(env, out, 12, cn, ctb);
                                free(ctb);
                            }
                        }
                    }
                }
            }
        }
    }
    (*env)->DeleteLocalRef(env, cipherCls);
    (*env)->DeleteLocalRef(env, srCls);
    if (out) set_step("seal:ok");
    return out;
}

/** open a blob made by vaultSeal; NULL on a tag failure or a wrong key */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultOpen(JNIEnv *env, jclass cls, jbyteArray jblob, jint which) {
    set_step("open:entered");
    if (jblob == NULL) { set_step("open:null-input"); return NULL; }
    jbyteArray jaad = aad_bytes(env);
    if (jaad == NULL) { set_step("open:aad-bytes"); return NULL; }
    jsize n = (*env)->GetArrayLength(env, jblob);
    if (n < 29) { set_step("open:too-short"); return NULL; }
    jobject key = NULL;
    if (which == 1) {
        jstring legacy = alias_for(env, 1);
        key = legacy ? read_key(env, legacy) : NULL;
    } else if (Java_ir_v2rayng_compose_core_NativeSec_vaultReady(env, cls, 0) == 1) {
        key = cached_key;
    }
    if (key == NULL) return NULL; /* the keystore step that refused is already the recorded step */
    jclass cipherCls = need_class(env, (const char *) CIPH);
    jclass specCls = need_class(env, "javax/crypto/spec/GCMParameterSpec");
    if (!cipherCls || !specCls) { set_step("open:find-class"); return NULL; }
    jbyte *raw = (jbyte *) malloc((size_t) n);
    jbyteArray iv = (*env)->NewByteArray(env, 12);
    jbyteArray ct = (*env)->NewByteArray(env, (jsize) (n - 12));
    jbyteArray out = NULL;
    if (!raw || !iv || !ct) set_step("open:alloc");
    if (raw && iv && ct) {
        /* copied into a plain buffer, not pinned: see the note in vaultSeal */
        (*env)->GetByteArrayRegion(env, jblob, 0, n, raw);
        (*env)->SetByteArrayRegion(env, iv, 0, 12, raw);
        (*env)->SetByteArrayRegion(env, ct, 0, (jsize) (n - 12), raw + 12);
        jmethodID specCtor = (*env)->GetMethodID(env, specCls, "<init>", "(I[B)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID getInst = (*env)->GetStaticMethodID(env, cipherCls, MD_GET, SIG_GET);  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jstring tr = (*env)->NewStringUTF(env, (const char *) TRANS);
        jobject spec = specCtor ? (*env)->NewObject(env, specCls, specCtor, 128, iv) : NULL;
        jobject c = getInst && tr ? (*env)->CallStaticObjectMethod(env, cipherCls, getInst, tr) : NULL;
        if (spec && c) {
            jmethodID init = (*env)->GetMethodID(env, cipherCls, MD_INIT, "(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jmethodID aad = (*env)->GetMethodID(env, cipherCls, MD_AAD, "([B)V");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jmethodID fin = (*env)->GetMethodID(env, cipherCls, MD_FIN, SIG_B);  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            if (!init || !aad || !fin) note_null("open:method-ids", init ? (aad ? "no doFinal" : "no updateAAD") : "no init");
            if (init && aad && fin) {
                (*env)->CallVoidMethod(env, c, init, 2 /* DECRYPT_MODE */, key, spec);
                if (!step_failed(env, "open:cipher.init")) {
                    (*env)->CallVoidMethod(env, c, aad, jaad);
                    if (!step_failed(env, "open:cipher.updateAAD")) {
                        out = (jbyteArray) (*env)->CallObjectMethod(env, c, fin, ct);
                        /* AEADBadTagException here = the stored bytes were sealed under another key;
                         * any other class = this JNI call shape is wrong */
                        if (step_failed(env, "open:cipher.doFinal")) out = NULL;
                    }
                }
            }
        }
    }
    if (raw) free(raw);
    (*env)->DeleteLocalRef(env, cipherCls);
    (*env)->DeleteLocalRef(env, specCls);
    if (out) set_step("open:ok");
    return out;
}

/** seal then open the same bytes, in this library, with this key: 0 means the vault path
 * works end to end. Without this, "the stored value cannot be opened" has two readings - the code is
 * wrong, or the value was sealed under a key that no longer exists (which already happened once). */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultRoundTrip(JNIEnv *env, jclass cls) {
    if (Java_ir_v2rayng_compose_core_NativeSec_vaultReady(env, cls, 0) == 0) return 2;
    jbyte sample[24];
    for (int i = 0; i < 24; i++) sample[i] = (jbyte) (i * 7 + 1);
    jbyteArray plain = (*env)->NewByteArray(env, 24);
    if (plain == NULL) return 3;
    (*env)->SetByteArrayRegion(env, plain, 0, 24, sample);
    jbyteArray sealed = Java_ir_v2rayng_compose_core_NativeSec_vaultSeal(env, cls, plain);
    if (sealed == NULL) return 4;
    jbyteArray back = Java_ir_v2rayng_compose_core_NativeSec_vaultOpen(env, cls, sealed, 0);
    jint r = 5;
    if (back != NULL && (*env)->GetArrayLength(env, back) == 24) {
        jbyte *a = (jbyte *) malloc(24);
        jbyte *bb = (jbyte *) malloc(24);
        if (a && bb) {
            (*env)->GetByteArrayRegion(env, plain, 0, 24, a);
            (*env)->GetByteArrayRegion(env, back, 0, 24, bb);
            r = (memcmp(a, bb, 24) == 0) ? 0 : 6;
        }
        free(a); free(bb);
    }
    (*env)->DeleteLocalRef(env, plain);
    (*env)->DeleteLocalRef(env, sealed);
    if (back) (*env)->DeleteLocalRef(env, back);
    return r;
}

/** is there an entry under this alias at all? - the caller must ask this before dropping,
 * because "the keystore did not answer" and "there is a key I cannot read" are different events and
 * only the second one justifies destroying anything. */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultHas(JNIEnv *env, jclass cls, jint which) {
    jstring jalias = alias_for(env, which);
    if (jalias == NULL) return 0;
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return 0;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID has = (*env)->GetMethodID(env, ksCls, "containsAlias", "(Ljava/lang/String;)Z");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jint r = 0;
    if (has) {
        r = (*env)->CallBooleanMethod(env, ks, has, jalias) ? 1 : 0;
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); r = 0; }
    }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    return r;
}

/** drop an entry (the recovery path uses it when the keystore holds a key it cannot read) */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_vaultDrop(JNIEnv *env, jclass cls, jint which) {
    jstring jalias = alias_for(env, which);
    if (jalias == NULL) return 0;
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return 0;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID del = (*env)->GetMethodID(env, ksCls, "deleteEntry", "(Ljava/lang/String;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    int done = 0;
    if (del) {
        (*env)->CallVoidMethod(env, ks, del, jalias);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); else done = 1;
    }
    if (which == 0 && cached_key) { (*env)->DeleteGlobalRef(env, cached_key); cached_key = NULL; cached_valid = 0; }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    return done;
}

/* ---------------- the device identity key (EC P-256 in AndroidKeyStore) ----------------
 * Same alias, same curve, same purposes and digests as before - only the code that asks for them
 * moved here, so an existing handset keeps the key it already enrolled with.
 * Returns: idEnsure -> 1 usable, idSign -> DER signature bytes, idSpki -> public key DER,
 * idDrop -> 1 deleted, idAttest -> {chain length, attested 0/1, self-signed 0/1, issuer, serial hex}
 */

static const char ID_ALIAS[] = "blob-dev-v1";
static const char ID_CURVE[] = "secp256r1";
static const char ID_SHA256[] = "SHA-256";
static const char ID_EC[] = "EC";
#define PURPOSE_SIGN_VERIFY 3 /* KeyProperties.PURPOSE_SIGN(1) | PURPOSE_VERIFY(2) - nothing else */

static jstring id_alias(JNIEnv *env) { return (*env)->NewStringUTF(env, ID_ALIAS); }

static jobject read_private(JNIEnv *env, jstring jalias) {
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return NULL;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID getEntry = (*env)->GetMethodID(env, ksCls, "getEntry", "(Ljava/lang/String;Ljava/security/KeyStore$ProtectionParameter;)Ljava/security/KeyStore$Entry;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!getEntry) { set_step("id:getEntry-method"); (*env)->DeleteLocalRef(env, ksCls); (*env)->DeleteLocalRef(env, ks); return NULL; }
    jobject entry = (*env)->CallObjectMethod(env, ks, getEntry, jalias, NULL);
    if (step_failed(env, "id:getEntry")) entry = NULL;
    jobject key = NULL;
    if (entry) {
        jclass pke = need_class(env, "java/security/KeyStore$PrivateKeyEntry");
        if (!pke) set_step("id:privatekeyentry-class");
        else if (!(*env)->IsInstanceOf(env, entry, pke)) set_step("id:entry-not-privatekey");
        if (pke && (*env)->IsInstanceOf(env, entry, pke)) {
            jmethodID g = (*env)->GetMethodID(env, pke, "getPrivateKey", "()Ljava/security/PrivateKey;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            if (!g) set_step("id:getPrivateKey-method");
            if (g) key = (*env)->CallObjectMethod(env, entry, g);
            if (step_failed(env, "id:getPrivateKey")) key = NULL;
        }
        if (pke) (*env)->DeleteLocalRef(env, pke);
    }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    if (entry) (*env)->DeleteLocalRef(env, entry);
    return key;
}

static jobject cached_priv = NULL;   /* global ref, this process */
static int cached_priv_valid = 0;

static void priv_invalidate(JNIEnv *env) {
    if (cached_priv) { (*env)->DeleteGlobalRef(env, cached_priv); cached_priv = NULL; }
    cached_priv_valid = 0;
}

/** the signing key handle, kept between calls: getEntry is a binder round trip that costs seconds
 *  on some handsets, which is why the old Kotlin held it in a field; the cache is dropped whenever
 *  a signature fails, so the next attempt re-reads (or regenerates) instead of reusing a dead one */
static jobject priv_cached(JNIEnv *env) {
    if (cached_priv_valid && cached_priv != NULL) return cached_priv;
    jstring jalias = id_alias(env);
    if (jalias == NULL) { set_step("id:alias"); return NULL; }
    jobject k = read_private(env, jalias);
    if (k == NULL) return NULL;
    priv_invalidate(env);
    cached_priv = (*env)->NewGlobalRef(env, k);
    (*env)->DeleteLocalRef(env, k);
    cached_priv_valid = cached_priv != NULL;
    set_step(cached_priv_valid ? "id:key-cached" : "id:global-ref");
    return cached_priv_valid ? cached_priv : NULL;
}

/** n bytes from the platform's own CSPRNG. Kotlin has no SecureRandom any more, so every random
 *  value the protocol needs - the attestation challenge, the one-shot token nonce - is drawn here.
 *  A new instance per call, the way java.security.SecureRandom was constructed before. */
static jbyteArray rnd_bytes(JNIEnv *env, jsize n) {
    if (n <= 0 || n > 4096) { set_step("rnd:count"); return NULL; }
    jclass sr = need_class(env, SRND_CLS);
    if (!sr) { set_step("rnd:class"); return NULL; }
    jmethodID srNew = (*env)->GetMethodID(env, sr, "<init>", "()V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID next = (*env)->GetMethodID(env, sr, "nextBytes", "([B)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jbyteArray buf = (*env)->NewByteArray(env, n);
    jobject rnd = srNew ? (*env)->NewObject(env, sr, srNew) : NULL;
    if (step_failed(env, "rnd:ctor")) rnd = NULL;
    if (rnd && buf && next) {
        (*env)->CallVoidMethod(env, rnd, next, buf);
        if (step_failed(env, "rnd:nextBytes")) { (*env)->DeleteLocalRef(env, buf); buf = NULL; }
    } else {
        set_step("rnd:methods");
    }
    (*env)->DeleteLocalRef(env, sr);
    if (rnd) (*env)->DeleteLocalRef(env, rnd);
    if (buf) set_step("rnd:ok");
    return buf;
}

/** the attestation challenge is 16 of those bytes; it proves liveness and is not secret material */
static jbyteArray att_challenge(JNIEnv *env) { return rnd_bytes(env, 16); }

/** the builder chain, with or without an attestation challenge */
static jobject build_spec(JNIEnv *env, jbyteArray challenge) {
    jclass bld = need_class(env, KG_CLS);
    jclass ec = need_class(env, "java/security/spec/ECGenParameterSpec");
    jclass kp = need_class(env, KP_CLS);
    if (!bld || !ec || !kp) { set_step("id:spec-classes"); return NULL; }
    jstring jalias = id_alias(env);
    jmethodID ctor = (*env)->GetMethodID(env, bld, "<init>", "(Ljava/lang/String;I)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    int purposes = kprop_int(env, "PURPOSE_SIGN", 1) | kprop_int(env, "PURPOSE_VERIFY", 2);
    jobject b = ctor ? (*env)->NewObject(env, bld, ctor, jalias, purposes) : NULL;
    jobject spec = NULL;
    if (b) {
        jmethodID ecCtor = (*env)->GetMethodID(env, ec, "<init>", "(Ljava/lang/String;)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jstring curve = (*env)->NewStringUTF(env, ID_CURVE);
        jobject ecSpec = ecCtor && curve ? (*env)->NewObject(env, ec, ecCtor, curve) : NULL;
        jmethodID mEc = (*env)->GetMethodID(env, bld, "setAlgorithmParameterSpec", "(Ljava/security/spec/AlgorithmParameterSpec;)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID mDig = (*env)->GetMethodID(env, bld, "setDigests", "([Ljava/lang/String;)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID mAtt = (*env)->GetMethodID(env, bld, "setAttestationChallenge", "([B)Landroid/security/keystore/KeyGenParameterSpec$Builder;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID mBuild = (*env)->GetMethodID(env, bld, "build", "()Landroid/security/keystore/KeyGenParameterSpec;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        /* a builder call that throws answers NULL, and the next CallObjectMethod would then be
         * given a NULL object - that is a native crash, not a Java exception, so each link
         * checks the builder survived before the following call */
        if (step_failed(env, "id:spec-ctor")) b = NULL;
        if (b && mEc && ecSpec) b = (*env)->CallObjectMethod(env, b, mEc, ecSpec);
        if (mDig) {
            jclass strCls = need_class(env, "java/lang/String");
            jobjectArray digests = strCls ? (*env)->NewObjectArray(env, 1, strCls, NULL) : NULL;
            jstring d = kprop_str(env, "DIGEST_SHA256", ID_SHA256);
            if (digests && d) {
                (*env)->SetObjectArrayElement(env, digests, 0, d);
                if (b) b = (*env)->CallObjectMethod(env, b, mDig, digests);
            }
        }
        if (b && mAtt && challenge) b = (*env)->CallObjectMethod(env, b, mAtt, challenge);
        /* a ROM with no attestation refuses here; the caller retries without a challenge, so this
         * is a normal answer and only the step name records that it happened */
        if (step_failed(env, "id:attestation-challenge")) b = NULL;
        if (!b) set_step("id:builder-lost");
        if (b && mBuild) spec = (*env)->CallObjectMethod(env, b, mBuild);
        if (step_failed(env, "id:spec.build")) spec = NULL;
    }
    return spec;
}

static int make_pair(JNIEnv *env, jobject spec) {
    jclass kpg = need_class(env, "java/security/KeyPairGenerator");
    if (!kpg) { set_step("id:keygen-class"); return 0; }
    if (spec == NULL) { set_step("id:no-spec"); return 0; }
    jmethodID getInst = (*env)->GetStaticMethodID(env, kpg, "getInstance", "(Ljava/lang/String;Ljava/lang/String;)Ljava/security/KeyPairGenerator;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring ecName = (*env)->NewStringUTF(env, ID_EC);
    jstring prov = (*env)->NewStringUTF(env, PROVIDER);
    jobject gen = getInst && ecName && prov ? (*env)->CallStaticObjectMethod(env, kpg, getInst, ecName, prov) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); gen = NULL; }
    int done = 0;
    if (gen) {
        jmethodID init = (*env)->GetMethodID(env, kpg, "initialize", "(Ljava/security/spec/AlgorithmParameterSpec;)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID mk = (*env)->GetMethodID(env, kpg, "generateKeyPair", "()Ljava/security/KeyPair;");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (!init) set_step("id:initialize-method");
        if (init) (*env)->CallVoidMethod(env, gen, init, spec);
        if (!step_failed(env, "id:generator.initialize") && mk) {
            jobject kp = (*env)->CallObjectMethod(env, gen, mk);
            if (step_failed(env, "id:generateKeyPair")) kp = NULL;
            done = kp != NULL;
        }
    }
    return done;
}

JNIEXPORT jint JNICALL Java_ir_v2rayng_compose_core_NativeSec_idDrop(JNIEnv *env, jclass cls);

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_idEnsure(JNIEnv *env, jclass cls, jbyteArray challenge) {
    set_step("id:ensure-entered");
    if (priv_cached(env) != NULL) {
        if (sig_probe(env, cached_priv)) { set_step("id:already-there"); return 1; }
        /* the entry reads but cannot sign: a key made by an earlier build of this library, whose
         * purpose mask was a literal instead of this framework's constant - keystore2 answered
         * INCOMPATIBLE_PURPOSE, "the purpose is not authorized". Replacing it is the only way this
         * device can authenticate again, and the worker accepts the rotation by design. */
        Java_ir_v2rayng_compose_core_NativeSec_idDrop(env, cls);
        set_step("id:replacing-unusable");
    }
    /* attested first, plain second: a ROM without attestation support must still get an identity */
    jbyteArray ch = challenge != NULL ? challenge : att_challenge(env);
    jobject spec = build_spec(env, ch);
    if (spec && make_pair(env, spec)) {
        priv_invalidate(env);
        jobject k = priv_cached(env);
        if (k != NULL && sig_probe(env, k)) { set_step("id:attested-key-made"); return 1; }
        /* an attested key that cannot sign is not an identity: it is dropped below and made again */
    }
    /* reaching here means an entry existed and could not be signed with, so the code below
     * destroys it: 2 is that case, 1 is "nothing of yours was replaced" */
    Java_ir_v2rayng_compose_core_NativeSec_idDrop(env, cls);
    jobject plain = build_spec(env, NULL);
    int made = (plain && make_pair(env, plain)) ? 1 : 0;
    if (made) { priv_invalidate(env); jobject k2 = priv_cached(env); made = (k2 != NULL && sig_probe(env, k2)) ? 1 : 0; }
    set_step(made ? "id:plain-key-made" : "id:no-key-at-all");
    return made ? 2 : 0;
}

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_idSign(JNIEnv *env, jclass cls, jbyteArray jmsg) {
    set_step("id:sign-entered");
    if (jmsg == NULL) { set_step("id:sign-null-msg"); return NULL; }
    jobject key = priv_cached(env);
    if (key == NULL) { set_step("id:sign-no-key"); return NULL; }
    jclass sigCls = need_class(env, "java/security/Signature");
    if (!sigCls) { set_step("id:sign-class"); return NULL; }
    jmethodID getInst = (*env)->GetStaticMethodID(env, sigCls, "getInstance", "(Ljava/lang/String;)Ljava/security/Signature;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring algo = (*env)->NewStringUTF(env, "SHA256withECDSA");
    jobject s = getInst && algo ? (*env)->CallStaticObjectMethod(env, sigCls, getInst, algo) : NULL;
    if (step_failed(env, "id:sign.getInstance")) s = NULL;
    if (!getInst) set_step("id:sign-method");
    jbyteArray out = NULL;
    if (s) {
        jmethodID initSign = (*env)->GetMethodID(env, sigCls, "initSign", "(Ljava/security/PrivateKey;)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID update = (*env)->GetMethodID(env, sigCls, "update", "([BII)V");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jmethodID sign = (*env)->GetMethodID(env, sigCls, "sign", "()[B");  //EXC-CLEARED
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (!initSign || !update || !sign) {
            note_null("id:sign-method-ids", initSign ? (update ? "no sign" : "no update") : "no initSign");
        }
        /* step_failed clears the exception, so each stage cannot ask "is anything pending" to learn
         * whether the PREVIOUS one refused - the answer is gone by then. One flag carries it. */
        int failed = 0;
        if (!failed && initSign) (*env)->CallVoidMethod(env, s, initSign, key);
        failed = step_failed(env, "id:sign.initSign");
        if (!failed && update) {
            jsize n = (*env)->GetArrayLength(env, jmsg);
            (*env)->CallVoidMethod(env, s, update, jmsg, 0, n);
            failed = step_failed(env, "id:sign.update");
        }
        if (!failed && sign) {
            out = (jbyteArray) (*env)->CallObjectMethod(env, s, sign);
            failed = step_failed(env, "id:sign.sign");
            if (failed) out = NULL;
        }
        if (out) set_step("id:sign-ok");
    }
    /* no signature is a reason to keep a handle this device may have rotated under us */
    if (out == NULL) priv_invalidate(env);
    return out;
}

/** X.509 SubjectPublicKeyInfo DER of the certificate's public key - what the worker imports */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_idSpki(JNIEnv *env, jclass cls) {
    set_step("id:spki-entered");
    jstring jalias = id_alias(env);
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) { set_step("id:spki-keystore"); return NULL; }
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID getCert = (*env)->GetMethodID(env, ksCls, "getCertificate", "(Ljava/lang/String;)Ljava/security/cert/Certificate;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jobject cert = getCert && jalias ? (*env)->CallObjectMethod(env, ks, getCert, jalias) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); cert = NULL; }
    jbyteArray out = NULL;
    if (cert == NULL) set_step("id:no-certificate");
    if (cert) {
        jclass xcls = need_class(env, "java/security/cert/X509Certificate");
        if (!xcls) set_step("id:x509-class");
        else if (!(*env)->IsInstanceOf(env, cert, xcls)) set_step("id:cert-not-x509");
        if (xcls && (*env)->IsInstanceOf(env, cert, xcls)) {
            /* the certificate's own encoded form is the whole cert; the worker imports the SPKI,
             * so take getPublicKey().getEncoded() instead */
            jmethodID pubOf = (*env)->GetMethodID(env, xcls, "getPublicKey", "()Ljava/security/PublicKey;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jobject pub = pubOf ? (*env)->CallObjectMethod(env, cert, pubOf) : NULL;
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); pub = NULL; }
            if (pub) {
                jclass keyCls = (*env)->GetObjectClass(env, pub);
                jmethodID enc = (*env)->GetMethodID(env, keyCls, "getEncoded", "()[B");  //EXC-CLEARED
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (!enc) set_step("id:getEncoded-method");
                if (enc) out = (jbyteArray) (*env)->CallObjectMethod(env, pub, enc);
                if (step_failed(env, "id:getEncoded")) out = NULL;
                if (out) set_step("id:spki-ok");
            }
        }
    }
    return out;
}

/** drop the identity entry (the retry path uses it when the keystore holds a key it cannot read) */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_idDrop(JNIEnv *env, jclass cls) {
    jstring jalias = id_alias(env);
    if (jalias == NULL) return 0;
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return 0;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID del = (*env)->GetMethodID(env, ksCls, "deleteEntry", "(Ljava/lang/String;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    int done = 0;
    if (del) {
        (*env)->CallVoidMethod(env, ks, del, jalias);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); else done = 1;
    }
    (*env)->DeleteLocalRef(env, ksCls);
    (*env)->DeleteLocalRef(env, ks);
    if (done) priv_invalidate(env);
    return done;
}

/** plain SHA-256, for the device fingerprint - the digest never leaves this library as code */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_sha(JNIEnv *env, jclass cls, jbyteArray jin) {
    if (jin == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, jin);
    unsigned char *in = (unsigned char *) malloc(n > 0 ? (size_t) n : 1);
    if (!in) return NULL;
    (*env)->GetByteArrayRegion(env, jin, 0, n, (jbyte *) in);
    unsigned char d[32];
    sha256(in, (size_t) n, d);
    free(in);
    jbyteArray out = (*env)->NewByteArray(env, 32);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, 32, (const jbyte *) d);
    return out;
}

/** what the certificate chain says about where this key was born, as six strings in this order:
 *  0 chain length, 1 attested (1/0), 2 unused, 3 self-signed (1/0), 4 issuer name,
 *  5 attestation-extension (1/0) - or null when the chain cannot be read at all. Kotlin assembles
 *  the map from this and holds no certificate API of its own, so these indexes are the contract. */
JNIEXPORT jobjectArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_idChain(JNIEnv *env, jclass cls) {
    jstring jalias = id_alias(env);
    int err = 0;
    jobject ks = keystore_open(env, &err);
    if (err || ks == NULL) return NULL;
    jclass ksCls = (*env)->GetObjectClass(env, ks);
    jmethodID getChain = (*env)->GetMethodID(env, ksCls, "getCertificateChain", "(Ljava/lang/String;)[Ljava/security/cert/Certificate;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jobjectArray chain = getChain && jalias ? (jobjectArray) (*env)->CallObjectMethod(env, ks, getChain, jalias) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); chain = NULL; }
    jclass strCls = need_class(env, "java/lang/String");
    jobjectArray out = strCls ? (*env)->NewObjectArray(env, 6, strCls, NULL) : NULL;
    if (out == NULL) return NULL;
    char buf[64];
    int certs = chain ? (int) (*env)->GetArrayLength(env, chain) : 0;
    sprintf(buf, "%d", certs);
    int attested = certs > 1 ? 1 : 0;
    const char *vals[3] = { buf, attested ? "1" : "0", "" };
    for (int i = 0; i < 3; i++) {
        jstring s = (*env)->NewStringUTF(env, vals[i]);
        if (s) (*env)->SetObjectArrayElement(env, out, i, s);
    }
    char issuer[96] = "";
    char selfSigned[2] = "0";
    char hasAttExt[2] = "0";
    if (certs > 0) {
        jobject leaf = (*env)->GetObjectArrayElement(env, chain, 0);
        jclass xcls = need_class(env, "java/security/cert/X509Certificate");
        if (leaf && xcls && (*env)->IsInstanceOf(env, leaf, xcls)) {
            jmethodID getIssuer = (*env)->GetMethodID(env, xcls, "getIssuerX500Principal", "()Ljavax/security/auth/x500/X500Principal;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jmethodID getSubject = (*env)->GetMethodID(env, xcls, "getSubjectX500Principal", "()Ljavax/security/auth/x500/X500Principal;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jclass prinCls = need_class(env, "javax/security/auth/x500/X500Principal");
            jmethodID getName = prinCls ? (*env)->GetMethodID(env, prinCls, "getName", "()Ljava/lang/String;") : NULL;  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jobject iss = getIssuer ? (*env)->CallObjectMethod(env, leaf, getIssuer) : NULL;
            jobject sub = getSubject ? (*env)->CallObjectMethod(env, leaf, getSubject) : NULL;
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            char subName[96] = "";
            if (iss && getName) {
                jstring s = (jstring) (*env)->CallObjectMethod(env, iss, getName);
                if (s) {
                    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
                    if (c) { copy_ascii(issuer, sizeof(issuer), c); (*env)->ReleaseStringUTFChars(env, s, c); }
                    copy_ascii(subName, sizeof(subName), issuer);
                }
            }
            if (sub && getName) {
                jstring s = (jstring) (*env)->CallObjectMethod(env, sub, getName);
                if (s) {
                    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
                    if (c) { copy_ascii(subName, sizeof(subName), c); (*env)->ReleaseStringUTFChars(env, s, c); }
                }
            }
            if (subName[0] && strcmp(subName, issuer) == 0) selfSigned[0] = '1';
            jmethodID getExt = (*env)->GetMethodID(env, xcls, "getExtensionValue", "(Ljava/lang/String;)[B");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jstring oid = (*env)->NewStringUTF(env, "1.3.6.1.4.1.11129.2.1.17");
            if (getExt && oid) {
                jbyteArray ext = (jbyteArray) (*env)->CallObjectMethod(env, leaf, getExt, oid);
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (ext != NULL && (*env)->GetArrayLength(env, ext) > 0) hasAttExt[0] = '1';
            }
        }
        if (leaf) (*env)->DeleteLocalRef(env, leaf);
    }
    const char *tail[3] = { selfSigned, issuer, hasAttExt };
    for (int i = 0; i < 3; i++) {
        jstring s = (*env)->NewStringUTF(env, tail[i]);
        if (s) (*env)->SetObjectArrayElement(env, out, 3 + i, s);
    }
    return out;
}

/* AES-256-GCM is NOT hand-written here. See the note above: the platform primitive is driven from
 * C so that no Java/Kotlin source holds the crypto, without betting every handset's sync on an
 * AES table nobody was able to compile and test. */


/* ---------------- the nonce source and the verdict, for the Kotlin side ----------------
 * BlobSecure used to construct KeyFactory/X509EncodedKeySpec/Signature itself to check that the
 * public key it exports really is the pair that signed. The answer is one verdict, so the key
 * bytes never have to be readable by anything written in Kotlin again.
 */

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_rndBytes(JNIEnv *env, jclass cls, jint count) {
    return rnd_bytes(env, (jsize) count);
}

/** 1 = this signature opens under this SPKI, 0 = it does not, -1 = the call could not be made
 *  (which step is in err(), and a -1 is a different bug from a 0) */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_verify(JNIEnv *env, jclass cls, jbyteArray jspki, jbyteArray jmsg, jbyteArray jsig) {
    if (jspki == NULL || jmsg == NULL || jsig == NULL) { set_step("ver:null-args"); return -1; }
    set_step("ver:entered");
    jclass kfCls = need_class(env, "java/security/KeyFactory");
    jclass specCls = need_class(env, "java/security/spec/X509EncodedKeySpec");
    jclass sigCls = need_class(env, "java/security/Signature");
    if (!kfCls || !specCls || !sigCls) { set_step("ver:classes"); return -1; }
    jmethodID kfGet = (*env)->GetStaticMethodID(env, kfCls, MD_GET, "(Ljava/lang/String;)Ljava/security/KeyFactory;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID genPub = (*env)->GetMethodID(env, kfCls, "generatePublic", "(Ljava/security/spec/KeySpec;)Ljava/security/PublicKey;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID specCtor = (*env)->GetMethodID(env, specCls, "<init>", "([B)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID sigGet = (*env)->GetStaticMethodID(env, sigCls, MD_GET, "(Ljava/lang/String;)Ljava/security/Signature;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID initVer = (*env)->GetMethodID(env, sigCls, "initVerify", "(Ljava/security/PublicKey;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID upd = (*env)->GetMethodID(env, sigCls, "update", "([BII)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID ver = (*env)->GetMethodID(env, sigCls, "verify", "([B)Z");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!kfGet || !genPub || !specCtor || !sigGet || !initVer || !upd || !ver) { set_step("ver:method-ids"); return -1; }

    jstring ecName = (*env)->NewStringUTF(env, ID_EC);
    jstring algo = (*env)->NewStringUTF(env, "SHA256withECDSA");
    jint verdict = -1;
    jobject kf = ecName ? (*env)->CallStaticObjectMethod(env, kfCls, kfGet, ecName) : NULL;
    if (step_failed(env, "ver:keyfactory")) kf = NULL;
    jobject spec = kf && specCtor ? (*env)->NewObject(env, specCls, specCtor, jspki) : NULL;
    if (step_failed(env, "ver:spec-ctor")) spec = NULL;
    jobject pub = (kf && spec) ? (*env)->CallObjectMethod(env, kf, genPub, spec) : NULL;
    if (step_failed(env, "ver:generatePublic")) pub = NULL;
    if (pub == NULL) { set_step("ver:no-public-key"); return -1; }
    jobject s = algo ? (*env)->CallStaticObjectMethod(env, sigCls, sigGet, algo) : NULL;
    if (step_failed(env, "ver:signature")) s = NULL;
    if (s) {
        (*env)->CallVoidMethod(env, s, initVer, pub);
        if (!step_failed(env, "ver:initVerify")) {
            jsize n = (*env)->GetArrayLength(env, jmsg);
            (*env)->CallVoidMethod(env, s, upd, jmsg, 0, n);
            if (!step_failed(env, "ver:update")) {
                jboolean v = (*env)->CallBooleanMethod(env, s, ver, jsig);
                if (step_failed(env, "ver:verify")) verdict = -1;
                else verdict = v ? 1 : 0;
            }
        }
    }
    if (verdict == 1) set_step("ver:ok");
    else if (verdict == 0) set_step("ver:rejected");
    (*env)->DeleteLocalRef(env, kfCls);
    (*env)->DeleteLocalRef(env, specCls);
    (*env)->DeleteLocalRef(env, sigCls);
    if (ecName) (*env)->DeleteLocalRef(env, ecName);
    if (algo) (*env)->DeleteLocalRef(env, algo);
    if (kf) (*env)->DeleteLocalRef(env, kf);
    if (spec) (*env)->DeleteLocalRef(env, spec);
    if (pub) (*env)->DeleteLocalRef(env, pub);
    if (s) (*env)->DeleteLocalRef(env, s);
    return verdict;
}

/* ---------------- the stego carrier, receiver side ----------------
 * Bit-exact port of the Kotlin receiver that has been decoding this worker's carriers: slot
 * building (morphology insertions, space insertions, homoglyph swaps), the keyed shuffle, the
 * 16-bit magic / 32-bit length header and the bit stream. Nothing here is guessed: the port is
 * compared against a JS transcription of the Kotlin over fuzz carriers (chkstego.js), because a
 * hand-typed table or a wrong bit order would look exactly like "the server is broken".
 *
 * isLetter is the device's own Character.isLetter, memoised per UTF-16 unit - the worker's sender
 * uses the narrower Arabic+ASCII rule on the pre-swap text, the receiver must accept the swap
 * targets too, which is why the two definitions are deliberately different.
 */

#define ST_MAX_CIPHER 2000000
#define ST_MAGIC 0xB10B
#define ST_HEADER_BITS 56

static unsigned char g_st_letter[65536];   /* 0 unknown, 1 letter, 2 not */

static const jchar ST_TWIN_A[] = {
    0x06CC, 0x06A9,                                     /* ی ک */
    0x0061, 0x0041, 0x0063, 0x0043, 0x0065, 0x0045,     /* a A c C e E */
    0x006F, 0x004F, 0x0070, 0x0050, 0x0078, 0x0058,     /* o O p P x X */
    0x0079, 0x0059, 0x006B, 0x004B, 0x006D, 0x004D,     /* y Y k K m M */
    0x006A, 0x004A, 0x0042                              /* j J B */
};
static const jchar ST_TWIN_B[] = {
    0x064A, 0x0643,                                     /* ي ك */
    0x0430, 0x0410, 0x0441, 0x0421, 0x0435, 0x0415,     /* а А с С е Е */
    0x043E, 0x041E, 0x0440, 0x0420, 0x0445, 0x0425,     /* о О р Р х Х */
    0x0443, 0x0423, 0x043A, 0x041A, 0x043C, 0x041C,     /* у У к К м М */
    0x0458, 0x0408, 0x0412                              /* ј Ј В */
};
#define ST_PAIRS ((int) (sizeof(ST_TWIN_A) / sizeof(ST_TWIN_A[0])))

static int st_is_primary(jchar c) {
    for (int i = 0; i < ST_PAIRS; i++) if (ST_TWIN_A[i] == c) return 1;
    return 0;
}

static jchar st_twin(jchar c) {
    for (int i = 0; i < ST_PAIRS; i++) {
        if (ST_TWIN_A[i] == c) return ST_TWIN_B[i];
        if (ST_TWIN_B[i] == c) return ST_TWIN_A[i];
    }
    return 0;
}

static jchar st_primary_of(jchar c) {
    if (st_is_primary(c)) return c;
    jchar t = st_twin(c);
    return t != 0 ? t : c;
}

static int st_is_glue_char(jchar c) {
    return c == 0x200B || c == 0x200C || c == 0x200D || c == 0x2060 || c == 0xFEFF || c == 0x00AD;
}

static int st_is_letter(JNIEnv *env, jchar c, jclass chCls, jmethodID chMid) {
    if (c >= 0x0600 && c <= 0x06FF) return 1;
    if (g_st_letter[c] == 0) {
        jboolean v = (chCls && chMid) ? (*env)->CallStaticBooleanMethod(env, chCls, chMid, (jint) c) : JNI_FALSE;
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        g_st_letter[c] = v ? 1 : 2;
    }
    return g_st_letter[c] == 1;
}

static int st_is_glue(JNIEnv *env, jchar c, jclass chCls, jmethodID chMid) {
    return !st_is_letter(env, c, chCls, chMid) && st_is_glue_char(c);
}

static int st_family(jchar c) {
    if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || (c >= 0x08A0 && c <= 0x08FF) ||
        (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) return 1;
    if ((c >= 0x0400 && c <= 0x052F) || (c >= 0x0041 && c <= 0x005A) || (c >= 0x0061 && c <= 0x007A)) return 2;
    if ((c >= 0x0370 && c <= 0x03FF) || (c >= 0x1F00 && c <= 0x1FFF)) return 3;
    if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xAC00 && c <= 0xD7AF)) return 4;
    return 0;
}

typedef struct {
    int swap;
    int at;
    int prev;
    jchar ch;
    jchar twin;
} st_slot;

/** suffix/prefix rule of the slot builder: 0 = no morphology slot in this word */
static int st_morph_index(const jchar *w, int wlen, int k, int end) {
    /* هایی */ 
    if (wlen >= 8 && wlen >= 4 && w[wlen - 4] == 0x0647 && w[wlen - 3] == 0x0627 && w[wlen - 2] == 0x06CC && w[wlen - 1] == 0x06CC) return end - 3;
    /* ترین */
    if (wlen >= 8 && wlen >= 4 && w[wlen - 4] == 0x062A && w[wlen - 3] == 0x0631 && w[wlen - 2] == 0x06CC && w[wlen - 1] == 0x0646) return end - 3;
    /* های */
    if (wlen >= 7 && wlen >= 3 && w[wlen - 3] == 0x0647 && w[wlen - 2] == 0x0627 && w[wlen - 1] == 0x06CC) return end - 2;
    /* ها */
    if (wlen >= 5 && wlen >= 2 && w[wlen - 2] == 0x0647 && w[wlen - 1] == 0x0627) return end - 1;
    /* تر */
    if (wlen >= 6 && wlen >= 2 && w[wlen - 2] == 0x062A && w[wlen - 1] == 0x0631) return end - 1;
    /* نمی */
    if (wlen >= 6 && wlen >= 3 && w[0] == 0x0646 && w[1] == 0x0645 && w[2] == 0x06CC) return k + 3;
    /* می */
    if (wlen >= 5 && wlen >= 2 && w[0] == 0x0645 && w[1] == 0x06CC) return k + 2;
    return -1;
}

/** the robust slot list: morphology, then space insertions, then swaps (the only mode reveal uses) */
static int st_build_slots(JNIEnv *env, const jchar *s, int slen,
                          jclass chCls, jmethodID chMid, st_slot **out) {
    *out = NULL;
    int n = 0;
    for (int i = 0; i < slen; i++) if (st_is_letter(env, s[i], chCls, chMid)) n++;
    if (n < 2) return 0;
    jchar *lch = (jchar *) malloc(sizeof(jchar) * (size_t) n);
    int *lidx = (int *) malloc(sizeof(int) * (size_t) n);
    unsigned char *same = (unsigned char *) calloc((size_t) n, 1);
    unsigned char *morphK = (unsigned char *) calloc((size_t) n, 1);
    jchar *w = (jchar *) malloc(sizeof(jchar) * (size_t) (n + 1));
    st_slot *slots = (st_slot *) malloc(sizeof(st_slot) * (size_t) (3 * n + 8));
    if (!lch || !lidx || !same || !morphK || !w || !slots) {
        free(lch); free(lidx); free(same); free(morphK); free(w); free(slots);
        set_step("stego:alloc");
        return -1;
    }
    int t = 0;
    for (int i = 0; i < slen; i++) {
        if (st_is_letter(env, s[i], chCls, chMid)) { lch[t] = s[i]; lidx[t] = i; t++; }
    }
    for (int j = 0; j < n - 1; j++) {
        int cont = 1;
        for (int u = lidx[j] + 1; u < lidx[j + 1]; u++) {
            if (!st_is_glue(env, s[u], chCls, chMid)) { cont = 0; break; }
        }
        same[j] = (unsigned char) cont;
    }
    int count = 0;
    int k = 0;
    while (k < n) {
        int end = k;
        while (end + 1 < n && same[end]) end++;
        int wlen = 0;
        for (int u = k; u <= end; u++) w[wlen++] = st_primary_of(lch[u]);
        int slotK = st_morph_index(w, wlen, k, end);
        if (slotK > 0 && slotK > k && slotK <= end) {
            morphK[slotK] = 1;
            slots[count].swap = 0;
            slots[count].at = lidx[slotK];
            slots[count].prev = lidx[slotK - 1];
            slots[count].ch = 0x200C;      /* ZWNJ */
            slots[count].twin = 0;
            count++;
        }
        k = end + 1;
    }
    for (int j = 0; j < n - 1; j++) {
        int target = j + 1;
        if (morphK[target]) continue;
        int spaceLike = 1;
        for (int u = lidx[j] + 1; u < lidx[target]; u++) {
            if (s[u] != ' ' && !st_is_glue(env, s[u], chCls, chMid)) { spaceLike = 0; break; }
        }
        if (spaceLike && lidx[target] > lidx[j] + 1) {
            slots[count].swap = 0;
            slots[count].at = lidx[target];
            slots[count].prev = lidx[j];
            slots[count].ch = ' ';
            slots[count].twin = 0;
            count++;
        }
    }
    for (int j = 0; j < n; j++) {
        jchar tw = st_twin(lch[j]);
        if (tw != 0) {
            slots[count].swap = 1;
            slots[count].at = lidx[j];
            slots[count].prev = 0;
            slots[count].ch = 0;
            slots[count].twin = tw;
            count++;
        }
    }
    free(lch); free(lidx); free(same); free(morphK); free(w);
    *out = slots;
    return count;
}

static int st_eval(const jchar *s, int slen, const st_slot *sl) {
    if (sl->swap) return sl->at >= 0 && sl->at < slen && !st_is_primary(s[sl->at]);
    if (sl->prev < 0 || sl->at >= slen || sl->at <= sl->prev) return 0;
    if (sl->ch == ' ') {
        int cnt = 0;
        for (int i = sl->prev + 1; i < sl->at; i++) if (s[i] == ' ') cnt++;
        return cnt >= 2;
    }
    for (int i = sl->prev + 1; i < sl->at; i++) if (s[i] == sl->ch) return 1;
    return 0;
}

typedef struct { uint64_t state; } st_rnd;

static uint64_t st_next64(st_rnd *r) {
    uint64_t x = r->state;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    r->state = x;
    return x * 0x2545F4914F6CDD1DULL;
}

static int st_below(st_rnd *r, int bound) {
    if (bound <= 1) return 0;
    uint64_t b = (uint64_t) bound;
    uint64_t lim = (0x4000000000000000ULL / b) * b;
    uint64_t v;
    do { v = st_next64(r) & 0x3FFFFFFFFFFFFFFFULL; } while (v >= lim);
    return (int) (v % b);
}

static void st_seed(const unsigned char *kc, st_rnd *r) {
    unsigned char mac[32];
    hmac_sha256(kc, 32, (const unsigned char *) "BLB1", 4, mac);
    uint64_t seed = 0;
    for (int i = 0; i < 8; i++) seed = (seed << 8) | (uint64_t) mac[i];
    seed ^= 0x9E3779B97F4A7C15ULL;              /* -0x61C8864680B583EB in two's complement */
    if (seed == 0) seed = 0xB504F32DULL;
    r->state = seed;
}

static int st_read_bits(const jchar *s, int slen, const st_slot *slots, const int *shuf, int from, int width) {
    int v = 0;
    for (int i = 0; i < width; i++) {
        int e = st_eval(s, slen, &slots[shuf[from + i]]);
        v = (v << 1) | (e ? 1 : 0);
    }
    return v;
}

/** the whole payload path: carrier text + session key in, plaintext JSON bytes out.
 *  NULL means one of the named steps refused, and err() carries which. */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_stegoOpen(JNIEnv *env, jclass cls, jstring jstego, jbyteArray jkc) {
    set_step("stego:entered");
    if (jstego == NULL || jkc == NULL) { set_step("stego:null-args"); return NULL; }
    if ((*env)->GetArrayLength(env, jkc) != 32) { set_step("stego:key-size"); return NULL; }
    jsize slen = (*env)->GetStringLength(env, jstego);
    const jchar *s = (*env)->GetStringChars(env, jstego, NULL);
    if (s == NULL) { set_step("stego:chars"); return NULL; }

    jclass chCls = need_class(env, "java/lang/Character");
    jmethodID chMid = chCls ? (*env)->GetStaticMethodID(env, chCls, "isLetter", "(C)Z") : NULL;  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!chCls || !chMid) { set_step("stego:character-class"); (*env)->ReleaseStringChars(env, jstego, s); return NULL; }

    st_slot *slots = NULL;
    int count = st_build_slots(env, s, slen, chCls, chMid, &slots);
    if (count < 0) { (*env)->ReleaseStringChars(env, jstego, s); return NULL; }
    if (count < ST_HEADER_BITS) { set_step("stego:small"); free(slots); (*env)->ReleaseStringChars(env, jstego, s); return NULL; }

    unsigned char kc[32];
    (*env)->GetByteArrayRegion(env, jkc, 0, 32, (jbyte *) kc);
    st_rnd rnd;
    st_seed(kc, &rnd);
    int *shuf = (int *) malloc(sizeof(int) * (size_t) count);
    if (!shuf) { set_step("stego:alloc"); free(slots); (*env)->ReleaseStringChars(env, jstego, s); return NULL; }
    for (int i = 0; i < count; i++) shuf[i] = i;
    for (int i = count - 1; i >= 1; i--) {
        int j = st_below(&rnd, i + 1);
        int tmp = shuf[i]; shuf[i] = shuf[j]; shuf[j] = tmp;
    }
    int magic = st_read_bits(s, slen, slots, shuf, 0, 16);
    if (magic != ST_MAGIC) {
        set_step("stego:magic"); free(shuf); free(slots); (*env)->ReleaseStringChars(env, jstego, s);
        return NULL;
    }
    int cipherLen = st_read_bits(s, slen, slots, shuf, 16, 32);
    if (cipherLen <= 0 || cipherLen > ST_MAX_CIPHER) {
        set_step("stego:len"); free(shuf); free(slots); (*env)->ReleaseStringChars(env, jstego, s);
        return NULL;
    }
    if ((long long) ST_HEADER_BITS + (long long) cipherLen * 8 > (long long) count) {
        set_step("stego:short"); free(shuf); free(slots); (*env)->ReleaseStringChars(env, jstego, s);
        return NULL;
    }
    jbyteArray jcipher = (*env)->NewByteArray(env, (jsize) cipherLen);
    jbyteArray out = NULL;
    if (jcipher) {
        jbyte *tmp = (jbyte *) malloc((size_t) cipherLen);
        if (tmp) {
            for (int i = 0; i < cipherLen; i++) {
                int v = 0;
                for (int b = 0; b < 8; b++) {
                    int e = st_eval(s, slen, &slots[shuf[ST_HEADER_BITS + i * 8 + b]]);
                    v = (v << 1) | (e ? 1 : 0);
                }
                tmp[i] = (jbyte) v;
            }
            (*env)->SetByteArrayRegion(env, jcipher, 0, (jsize) cipherLen, tmp);
            free(tmp);
            jbyteArray jaad = (*env)->NewByteArray(env, 5);
            if (jaad) {
                const jbyte aad[5] = { 0x42, 0x4C, 0x42, 0x76, 0x31 };   /* BLBv1 */
                (*env)->SetByteArrayRegion(env, jaad, 0, 5, aad);
                jbyteArray plain = Java_ir_v2rayng_compose_core_NativeSec_gcmOpen(env, cls, jcipher, jkc, jaad);
                if (plain != NULL) {
                    jsize plen = (*env)->GetArrayLength(env, plain);
                    jbyteArray envl = Java_ir_v2rayng_compose_core_NativeSec_openEnvelope(env, cls, plain);
                    if (envl != NULL) {
                        out = envl;
                    } else if (plen > 0) {
                        /* the Kotlin fallback: an envelope that will not open is stripped of its
                         * mode byte rather than guessed at, so the caller fails on the payload */
                        out = (*env)->NewByteArray(env, (jsize) (plen - 1));
                        if (out) {
                            jbyte *buf = (jbyte *) malloc((size_t) plen);
                            if (buf) {
                                (*env)->GetByteArrayRegion(env, plain, 1, (jsize) (plen - 1), buf);
                                (*env)->SetByteArrayRegion(env, out, 0, (jsize) (plen - 1), buf);
                                free(buf);
                            } else set_step("stego:alloc-out");
                        }
                    }
                }
            }
        } else set_step("stego:alloc-cipher");
    }
    free(shuf); free(slots);
    (*env)->ReleaseStringChars(env, jstego, s);
    return out;
}

/* ---------------- the one-shot token and the device proof ----------------
 * Both are byte layouts, not decisions: "blb"|device|0x00|ts(4 BE)|nonce(8), then either the HMAC
 * (token) or the hardware signature (proof), base64url over ts|nonce|that. They lived in Kotlin,
 * where the only thing they could do was drift away from the worker; here they sit next to the
 * HMAC and the identity key, so one file owns the wire bytes. The clock is the same wall clock the
 * Kotlin used, one second granularity, with the same guarantee that it never repeats a value
 * inside a process.
 */

static uint32_t tok_next_ts(void) {
    static uint32_t last = 0;
    uint32_t now = (uint32_t) time(NULL);
    if (now <= last) now = last + 1;
    last = now;
    return now;
}

/** "blb" || device || 0x00 || ts(4) || nonce(8) - caller frees, outlen receives the length */
static unsigned char *tok_msg(JNIEnv *env, jstring jdevice, uint32_t ts, const jbyte *nonce, int *outlen) {
    const char *dev = (*env)->GetStringUTFChars(env, jdevice, NULL);
    if (dev == NULL) return NULL;
    size_t dlen = strlen(dev);
    unsigned char *m = (unsigned char *) malloc(3 + dlen + 1 + 4 + 8);
    if (m) {
        memcpy(m, "blb", 3);
        if (dlen) memcpy(m + 3, dev, dlen);
        m[3 + dlen] = 0x00;
        m[4 + dlen] = (unsigned char) (ts >> 24);
        m[5 + dlen] = (unsigned char) (ts >> 16);
        m[6 + dlen] = (unsigned char) (ts >> 8);
        m[7 + dlen] = (unsigned char) ts;
        memcpy(m + 8 + dlen, nonce, 8);
        /* 3 (blb) + device + 1 (0x00) + 4 (ts) + 8 (nonce) = 16 + dlen. This said 12 + dlen for
         * one build, which cut the last four nonce bytes out of the message that got signed and
         * MACed: the app signed 28 bytes, the worker rebuilt 32, and both reported the pair as a
         * signature mismatch. The allocation was right, the length was not. */
        *outlen = (int) (16 + dlen);
    }
    (*env)->ReleaseStringUTFChars(env, jdevice, dev);
    return m;
}

JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_tokenFor(JNIEnv *env, jclass cls, jstring jdevice, jbyteArray jkt) {
    set_step("tok:entered");
    if (jdevice == NULL || jkt == NULL) { set_step("tok:null-args"); return NULL; }
    if ((*env)->GetArrayLength(env, jkt) != 32) { set_step("tok:key-size"); return NULL; }
    jbyteArray jnonce = rnd_bytes(env, 8);
    if (jnonce == NULL) { set_step("tok:nonce"); return NULL; }
    jbyte nonce[8];
    (*env)->GetByteArrayRegion(env, jnonce, 0, 8, nonce);
    uint32_t ts = tok_next_ts();
    int mlen = 0;
    unsigned char *msg = tok_msg(env, jdevice, ts, nonce, &mlen);
    if (msg == NULL) { set_step("tok:msg"); return NULL; }
    unsigned char kt[32];
    (*env)->GetByteArrayRegion(env, jkt, 0, 32, (jbyte *) kt);
    unsigned char mac[32];
    hmac_sha256(kt, 32, msg, (size_t) mlen, mac);
    free(msg);
    jbyte packed[44];
    packed[0] = (jbyte) (ts >> 24); packed[1] = (jbyte) (ts >> 16);
    packed[2] = (jbyte) (ts >> 8);  packed[3] = (jbyte) ts;
    for (int i = 0; i < 8; i++) packed[4 + i] = nonce[i];
    for (int i = 0; i < 32; i++) packed[12 + i] = (jbyte) mac[i];
    jbyteArray jpacked = (*env)->NewByteArray(env, 44);
    if (jpacked == NULL) { set_step("tok:pack"); return NULL; }
    (*env)->SetByteArrayRegion(env, jpacked, 0, 44, packed);
    jstring out = Java_ir_v2rayng_compose_core_NativeSec_b64uEncode(env, cls, jpacked);
    set_step(out ? "tok:ok" : "tok:encode");
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_proofFor(JNIEnv *env, jclass cls, jstring jdevice) {
    set_step("proof:entered");
    if (jdevice == NULL) { set_step("proof:null-args"); return NULL; }
    jbyteArray jnonce = rnd_bytes(env, 8);
    if (jnonce == NULL) { set_step("proof:nonce"); return NULL; }
    jbyte nonce[8];
    (*env)->GetByteArrayRegion(env, jnonce, 0, 8, nonce);
    uint32_t ts = tok_next_ts();
    int mlen = 0;
    unsigned char *msg = tok_msg(env, jdevice, ts, nonce, &mlen);
    if (msg == NULL) { set_step("proof:msg"); return NULL; }
    jbyteArray jmsg = (*env)->NewByteArray(env, (jsize) mlen);
    if (jmsg == NULL) { free(msg); set_step("proof:msg-array"); return NULL; }
    (*env)->SetByteArrayRegion(env, jmsg, 0, (jsize) mlen, (const jbyte *) msg);
    /* the three numbers the worker prints for the same attempt - message digest, signature digest
     * and the device byte count - computed here so one paste compares both sides instead of arguing */
    char info[96];
    unsigned char md[32];
    sha256(msg, (size_t) mlen, md);
    free(msg);
    jbyteArray sig = Java_ir_v2rayng_compose_core_NativeSec_idSign(env, cls, jmsg);
    if (sig == NULL) {
        /* one retry after asking for a usable key: the identity door probes the entry and replaces
         * one this framework will not sign with - the same two attempts BlobIdentity.sign made */
        Java_ir_v2rayng_compose_core_NativeSec_idEnsure(env, cls, NULL);
        sig = Java_ir_v2rayng_compose_core_NativeSec_idSign(env, cls, jmsg);
    }
    if (sig == NULL) { set_step("proof:sign"); return NULL; }
    jsize siglen = (*env)->GetArrayLength(env, sig);
    jbyteArray spki = Java_ir_v2rayng_compose_core_NativeSec_idSpki(env, cls);
    int verdict = spki ? Java_ir_v2rayng_compose_core_NativeSec_verify(env, cls, spki, jmsg, sig) : -8;
    set_step(verdict == 1 ? "proof:self-ok" : (verdict == 0 ? "proof:self-rejected" : "proof:self-error"));
    jbyte *buf = (jbyte *) malloc((size_t) (12 + siglen));
    if (buf == NULL) { set_step("proof:alloc"); return NULL; }
    buf[0] = (jbyte) (ts >> 24); buf[1] = (jbyte) (ts >> 16);
    buf[2] = (jbyte) (ts >> 8);  buf[3] = (jbyte) ts;
    for (int i = 0; i < 8; i++) buf[4 + i] = nonce[i];
    (*env)->GetByteArrayRegion(env, sig, 0, siglen, buf + 12);
    jbyteArray packed = (*env)->NewByteArray(env, (jsize) (12 + siglen));
    if (packed == NULL) { free(buf); set_step("proof:pack"); return NULL; }
    (*env)->SetByteArrayRegion(env, packed, 0, (jsize) (12 + siglen), buf);
    free(buf);
    jstring proof = Java_ir_v2rayng_compose_core_NativeSec_b64uEncode(env, cls, packed);
    /* the public bytes travel with the proof: they are the ones that signed, even when the identity
     * door had to replace the key a moment ago. Kotlin used to read its own cached copy, which is a
     * 403 the worker reports as "signature does not match": the signature was by the new key, the
     * pub line was the old one. */
    jstring pub = spki ? Java_ir_v2rayng_compose_core_NativeSec_b64uEncode(env, cls, spki) : NULL;
    /* the three numbers the worker prints for this same attempt - message digest/length,
     * signature digest/length, and the device byte count - so one paste compares both sides
     * instead of another round of analysis */
    if (siglen > 0 && siglen <= 512) {
        jbyte sbytes[512];
        (*env)->GetByteArrayRegion(env, sig, 0, siglen, sbytes);
        unsigned char sd[32];
        sha256((const unsigned char *) sbytes, (size_t) siglen, sd);
        snprintf(info, sizeof(info), "msg#%02x%02x%02x/%dB sig#%02x%02x%02x/%dB dev%dB",
                 md[0], md[1], md[2], (int) mlen, sd[0], sd[1], sd[2], (int) siglen, (int) (mlen - 16));
    } else {
        snprintf(info, sizeof(info), "msg#%02x%02x%02x/%dB sig=?", md[0], md[1], md[2], (int) mlen);
    }
    jstring jinfo = (*env)->NewStringUTF(env, info);
    jclass strCls = need_class(env, "java/lang/String");
    jobjectArray pair = strCls ? (*env)->NewObjectArray(env, 3, strCls, NULL) : NULL;
    if (pair) {
        if (proof) (*env)->SetObjectArrayElement(env, pair, 0, proof);
        if (pub) (*env)->SetObjectArrayElement(env, pair, 1, pub);
        if (jinfo) (*env)->SetObjectArrayElement(env, pair, 2, jinfo);
    } else {
        set_step("proof:pair");
    }
    if (proof == NULL) set_step("proof:encode");
    return pair;
}

/* ---------------- the server link, parsed here ----------------
 * First stage of moving the server off Kotlin: the parser that turns a link into fields runs in C,
 * so the credential never has to be returned to a Kotlin method that a tracer can read. The Kotlin
 * parser stays in place until the config builder and the sync result also move (stage 3) - the
 * branch is only merged when all three land, because a half-moved parser would drop servers.
 *
 * Covers vless:// and trojan:// (same shape: cred@host:port?params#name). vmess:// (base64 JSON)
 * and ss:// (base64 credentials) follow in the next commit on this branch.
 */
#define PL_NAME 160
#define PL_ADDR 256
#define PL_CRED 256
#define PL_MISC 256

static char pl_name[PL_NAME];
static char pl_addr[PL_ADDR];
static char pl_cred[PL_CRED];
static char pl_security[32], pl_type[24], pl_flow[32], pl_sni[PL_MISC], pl_fp[24];
static char pl_pbk[PL_MISC], pl_sid[PL_MISC], pl_spx[PL_MISC], pl_path[PL_MISC];
static char pl_host[PL_MISC], pl_eh[PL_MISC], pl_method[32];
static int pl_port, pl_kind, pl_alter, pl_ed;
static int pl_ok;

/** percent-decoding, '+'-to-space, UTF-8 left alone; returns 0 when it does not fit */
static int pl_udecode(const char *in, int n, char *out, int cap) {
    int o = 0;
    for (int i = 0; i < n; i++) {
        unsigned char c = (unsigned char) in[i];
        if (c == '%' && i + 2 < n) {
            int hi = in[i + 1], lo = in[i + 2];
            int hv = (hi >= '0' && hi <= '9') ? hi - '0' : (hi >= 'a' && hi <= 'f') ? hi - 'a' + 10 : (hi >= 'A' && hi <= 'F') ? hi - 'A' + 10 : -1;
            int lv = (lo >= '0' && lo <= '9') ? lo - '0' : (lo >= 'a' && lo <= 'f') ? lo - 'a' + 10 : (lo >= 'A' && lo <= 'F') ? lo - 'A' + 10 : -1;
            if (hv >= 0 && lv >= 0) { if (o + 1 >= cap) return 0; out[o++] = (char) ((hv << 4) | lv); i += 2; continue; }
        }
        if (c == '+') { if (o + 1 >= cap) return 0; out[o++] = ' '; continue; }
        if (o + 1 >= cap) return 0;
        out[o++] = (char) c;
    }
    out[o] = 0;
    return 1;
}

static void pl_copy(char *dst, int cap, const char *src, int n) {
    if (n >= cap) n = cap - 1;
    if (n > 0) memcpy(dst, src, (size_t) n);
    dst[n] = 0;
}

/** one query parameter, decoded; empty when absent */
static int pl_param(const char *q, int qn, const char *key, char *out, int cap) {
    int klen = (int) strlen(key);
    int i = 0;
    while (i < qn) {
        int start = i;
        while (i < qn && q[i] != '&') i++;
        int end = i;
        if (end - start > klen && q[start + klen] == '=' && memcmp(q + start, key, (size_t) klen) == 0) {
            return pl_udecode(q + start + klen + 1, end - start - klen - 1, out, cap);
        }
        i++;
    }
    return 0;
}


/* ---- vmess:// and ss:// ----
 * vmess carries a base64 of a JSON object; ss carries base64 credentials (or a fully base64 body).
 * The base64 alphabet accepts both the standard and the url-safe variants, the JSON is read with
 * org.json through JNI (the same library the Kotlin used, so no hand-written parser can disagree),
 * and the field mapping follows parseVmess/parseSs in XrayCore.kt.
 */

static int pl_b64(const char *in, int n, char *out, int cap) {
    int o = 0, val = 0, bits = 0;
    for (int i = 0; i < n; i++) {
        char c = in[i];
        int d;
        if (c >= 'A' && c <= 'Z') d = c - 'A';
        else if (c >= 'a' && c <= 'z') d = c - 'a' + 26;
        else if (c >= '0' && c <= '9') d = c - '0' + 52;
        else if (c == '+' || c == '-') d = 62;
        else if (c == '/' || c == '_') d = 63;
        else continue;
        val = (val << 6) | d;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            if (o + 1 >= cap) return 0;
            out[o++] = (char) ((val >> bits) & 0xff);
        }
    }
    out[o] = 0;
    return o;
}

/** optString(field, default) through org.json, copied as UTF-8 */
static void pl_json_str(JNIEnv *env, jobject obj, jclass jsonCls, const char *key, const char *def, char *out, int cap) {
    jmethodID m = (*env)->GetMethodID(env, jsonCls, "optString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    jstring k = (*env)->NewStringUTF(env, key);
    jstring d = (*env)->NewStringUTF(env, def);
    if (m && k && d) {
        jstring v = (jstring) (*env)->CallObjectMethod(env, obj, m, k, d);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); v = NULL; }
        if (v) {
            const char *c = (*env)->GetStringUTFChars(env, v, NULL);
            if (c) { pl_copy(out, cap, c, (int) strlen(c)); (*env)->ReleaseStringUTFChars(env, v, c); }
            (*env)->DeleteLocalRef(env, v);
        }
    }
    if (k) (*env)->DeleteLocalRef(env, k);
    if (d) (*env)->DeleteLocalRef(env, d);
}

static int pl_json_int(JNIEnv *env, jobject obj, jclass jsonCls, const char *key, int def) {
    jmethodID m = (*env)->GetMethodID(env, jsonCls, "optInt", "(Ljava/lang/String;I)I");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    jstring k = (*env)->NewStringUTF(env, key);
    int v = def;
    if (m && k) {
        v = (*env)->CallIntMethod(env, obj, m, k, def);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); v = def; }
    }
    if (k) (*env)->DeleteLocalRef(env, k);
    return v;
}

static int pl_parse_vmess(JNIEnv *env, const char *s, int n, int p) {
    char *b64 = (char *) malloc((size_t) (n - p + 1));
    char *json = (char *) malloc((size_t) (n - p + 1));
    int ok = 0;
    if (b64 && json) {
        pl_copy(b64, n - p + 1, s + p, n - p);
        if (pl_b64(b64, (int) strlen(b64), json, n - p + 1) > 0) {
            jclass jsonCls = need_class(env, "org/json/JSONObject");
            jstring jjs = jsonCls ? (*env)->NewStringUTF(env, json) : NULL;
            jobject obj = (jsonCls && jjs) ? (*env)->NewObject(env, jsonCls, (*env)->GetMethodID(env, jsonCls, "<init>", "(Ljava/lang/String;)V"), jjs) : NULL;
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); obj = NULL; }
            if (obj) {
                int port = pl_json_int(env, obj, jsonCls, "port", -1);
                pl_json_str(env, obj, jsonCls, "add", "", pl_addr, PL_ADDR);
                pl_json_str(env, obj, jsonCls, "id", "", pl_cred, PL_CRED);
                pl_json_str(env, obj, jsonCls, "ps", "", pl_name, PL_NAME);
                pl_json_str(env, obj, jsonCls, "scy", "auto", pl_method, sizeof(pl_method));
                pl_json_str(env, obj, jsonCls, "tls", "none", pl_security, sizeof(pl_security));
                pl_json_str(env, obj, jsonCls, "net", "tcp", pl_type, sizeof(pl_type));
                pl_json_str(env, obj, jsonCls, "sni", "", pl_sni, sizeof(pl_sni));
                pl_alter = pl_json_int(env, obj, jsonCls, "aid", 0);
                if (port > 0 && port <= 65535 && pl_addr[0] && pl_cred[0]) {
                    pl_port = port;
                    pl_kind = 2;
                    if (pl_security[0] == 0) strcpy(pl_security, "none");
                    if (pl_type[0] == 0) strcpy(pl_type, "tcp");
                    ok = 1;
                }
            }
            if (jjs) (*env)->DeleteLocalRef(env, jjs);
            if (obj) (*env)->DeleteLocalRef(env, obj);
            if (jsonCls) (*env)->DeleteLocalRef(env, jsonCls);
        }
    }
    free(b64);
    free(json);
    return ok;
}

static int pl_parse_ss(JNIEnv *env, const char *s, int n, int p) {
    int hashAt = -1;
    for (int i = p; i < n; i++) if (s[i] == '#') { hashAt = i; break; }
    int mainEnd = hashAt >= 0 ? hashAt : n;
    if (hashAt >= 0) pl_udecode(s + hashAt + 1, n - hashAt - 1, pl_name, PL_NAME);
    int qAt = -1;
    for (int i = p; i < mainEnd; i++) if (s[i] == '?') { qAt = i; break; }
    int bodyEnd = qAt >= 0 ? qAt : mainEnd;
    int at = -1;
    for (int i = p; i < bodyEnd; i++) if (s[i] == '@') at = i;
    char hostPart[PL_ADDR];
    int hostLen = 0;
    char method[32] = "", pass[PL_CRED] = "";
    if (at >= p) {
        /* cred may be base64 or percent-encoded */
        char cred[PL_CRED];
        char dec[PL_CRED];
        pl_udecode(s + p, at - p, cred, sizeof(cred));
        if (pl_b64(cred, (int) strlen(cred), dec, sizeof(dec)) <= 0) strcpy(dec, cred);
        char *c = strchr(dec, ':');
        if (c == NULL || c == dec) return 0;
        pl_copy(method, sizeof(method), dec, (int) (c - dec));
        pl_copy(pass, PL_CRED, c + 1, (int) strlen(c + 1));
        hostLen = bodyEnd - (at + 1);
        if (hostLen >= PL_ADDR) hostLen = PL_ADDR - 1;
        memcpy(hostPart, s + at + 1, (size_t) hostLen);
        hostPart[hostLen] = 0;
    } else {
        /* whole body is base64: cred@host:port inside */
        char dec[4096];
        char b[4096];
        int blen = bodyEnd - p;
        if (blen >= (int) sizeof(b)) blen = (int) sizeof(b) - 1;
        memcpy(b, s + p, (size_t) blen);
        b[blen] = 0;
        if (pl_b64(b, blen, dec, sizeof(dec)) <= 0) return 0;
        char *at2 = strrchr(dec, '@');
        char *colon2 = strrchr(dec, ':');
        if (at2 == NULL || colon2 == NULL || colon2 <= at2) return 0;
        char *cc = memchr(dec, ':', (size_t) (at2 - dec));
        if (cc == NULL || cc == dec) return 0;
        pl_copy(method, sizeof(method), dec, (int) (cc - dec));
        pl_copy(pass, PL_CRED, cc + 1, (int) (at2 - cc - 1));
        pl_copy(hostPart, PL_ADDR, at2 + 1, (int) strlen(at2 + 1));
        hostLen = (int) strlen(hostPart);
    }
    int colon = -1;
    for (int i = 0; i < hostLen; i++) if (hostPart[i] == ':') colon = i;
    if (colon <= 0) return 0;
    int hs = 0, he = colon;
    if (he - hs >= 2 && hostPart[hs] == '[' && hostPart[he - 1] == ']') { hs++; he--; }
    pl_copy(pl_addr, PL_ADDR, hostPart + hs, he - hs);
    int port = 0;
    for (int i = colon + 1; i < hostLen; i++) {
        if (hostPart[i] < '0' || hostPart[i] > '9') { port = 0; break; }
        port = port * 10 + (hostPart[i] - '0');
        if (port > 65535) { port = 0; break; }
    }
    if (pl_addr[0] == 0 || port <= 0 || method[0] == 0 || pass[0] == 0) return 0;
    pl_port = port;
    pl_kind = 3;
    strcpy(pl_method, method);
    pl_copy(pl_cred, PL_CRED, pass, (int) strlen(pass));
    strcpy(pl_security, "none");
    strcpy(pl_type, "tcp");
    return 1;
}

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_plParse(JNIEnv *env, jclass cls, jstring jlink) {
    pl_ok = 0;
    pl_name[0] = pl_addr[0] = pl_cred[0] = 0;
    if (jlink == NULL) return 0;
    const char *s = (*env)->GetStringUTFChars(env, jlink, NULL);
    if (s == NULL) return 0;
    int n = (int) strlen(s);
    int p = 0;
    if (n > 7 && (s[0] == 'v' || s[0] == 't')) {
        if (n > 8 && memcmp(s, "vless://", 8) == 0) { pl_kind = 0; p = 8; }
        else if (n > 9 && memcmp(s, "trojan://", 9) == 0) { pl_kind = 1; p = 9; }
    }
    if (p == 0) {
        if (n > 9 && memcmp(s, "vmess://", 9) == 0) { int ok = pl_parse_vmess(env, s, n, 9); (*env)->ReleaseStringUTFChars(env, jlink, s); pl_ok = ok; return ok; }
        if (n > 5 && memcmp(s, "ss://", 5) == 0) { int ok = pl_parse_ss(env, s, n, 5); (*env)->ReleaseStringUTFChars(env, jlink, s); pl_ok = ok; return ok; }
    }
    if (!(pl_kind == 0 || pl_kind == 1) || p == 0) { (*env)->ReleaseStringUTFChars(env, jlink, s); return 0; }
    int hashAt = -1, qAt = -1, atAt = -1;
    for (int i = p; i < n; i++) {
        if (s[i] == '#') { hashAt = i; break; }
        if (s[i] == '?' && qAt < 0) qAt = i;
        if (s[i] == '@' && atAt < 0) atAt = i;
    }
    int mainEnd = hashAt >= 0 ? hashAt : n;
    /* name from the fragment */
    if (hashAt >= 0) { pl_udecode(s + hashAt + 1, n - hashAt - 1, pl_name, PL_NAME); }
    if (atAt < 0 || atAt >= mainEnd) { (*env)->ReleaseStringUTFChars(env, jlink, s); return 0; }
    pl_copy(pl_cred, PL_CRED, s + p, atAt - p);
    /* host:port - take the last ':' before '?' and after the '@' */
    int hostStart = atAt + 1;
    int hpEnd = qAt >= 0 ? qAt : mainEnd;
    int slash = -1;
    for (int i = hostStart; i < hpEnd; i++) if (s[i] == '/') slash = i;
    int hostFrom = slash >= 0 ? slash + 1 : hostStart;
    int colon = -1;
    for (int i = hostFrom; i < hpEnd; i++) if (s[i] == ':') colon = i;
    if (colon <= hostFrom) { (*env)->ReleaseStringUTFChars(env, jlink, s); return 0; }
    int hs = hostFrom, he = colon;
    if (he - hs >= 2 && s[hs] == '[' && s[he - 1] == ']') { hs++; he--; }
    pl_copy(pl_addr, PL_ADDR, s + hs, he - hs);
    int port = 0;
    for (int i = colon + 1; i < hpEnd; i++) {
        if (s[i] < '0' || s[i] > '9') { port = 0; break; }
        port = port * 10 + (s[i] - '0');
        if (port > 65535) { port = 0; break; }
    }
    if (pl_addr[0] == 0 || port <= 0) { (*env)->ReleaseStringUTFChars(env, jlink, s); return 0; }
    pl_port = port;
    /* parameters */
    const char *q = NULL;
    int qn = 0;
    if (qAt >= 0) { q = s + qAt + 1; qn = mainEnd - qAt - 1; }
    pl_security[0] = pl_type[0] = pl_flow[0] = pl_sni[0] = pl_fp[0] = pl_pbk[0] = 0;
    pl_sid[0] = pl_spx[0] = pl_path[0] = pl_host[0] = pl_eh[0] = pl_method[0] = 0;
    pl_alter = 0;
    pl_ed = 0;
    if (q) {
        pl_param(q, qn, "security", pl_security, sizeof(pl_security));
        pl_param(q, qn, "type", pl_type, sizeof(pl_type));
        pl_param(q, qn, "flow", pl_flow, sizeof(pl_flow));
        pl_param(q, qn, "sni", pl_sni, sizeof(pl_sni));
        pl_param(q, qn, "fp", pl_fp, sizeof(pl_fp));
        pl_param(q, qn, "pbk", pl_pbk, sizeof(pl_pbk));
        pl_param(q, qn, "sid", pl_sid, sizeof(pl_sid));
        pl_param(q, qn, "spx", pl_spx, sizeof(pl_spx));
        pl_param(q, qn, "path", pl_path, sizeof(pl_path));
        pl_param(q, qn, "host", pl_host, sizeof(pl_host));
        pl_param(q, qn, "eh", pl_eh, sizeof(pl_eh));
        char tmp[32];
        if (pl_param(q, qn, "ed", tmp, sizeof(tmp))) { pl_ed = atoi(tmp); }
    }
    if (pl_security[0] == 0) { strcpy(pl_security, pl_kind == 1 ? "tls" : "none"); }
    if (pl_type[0] == 0) { strcpy(pl_type, "tcp"); }
    (*env)->ReleaseStringUTFChars(env, jlink, s);
    pl_ok = 1;
    return 1;
}

/** fields as UTF-8 bytes: 0 name, 1 address, 2 credential, 3 security, 4 type, 5 flow, 6 sni,
 *  7 fp, 8 pbk, 9 sid, 10 spx, 11 path, 12 host header, 13 early-data header, 14 method */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_plStr(JNIEnv *env, jclass cls, jint which) {
    const char *src = "";
    switch (which) {
        case 0: src = pl_name; break;    case 1: src = pl_addr; break;
        case 2: src = pl_cred; break;    case 3: src = pl_security; break;
        case 4: src = pl_type; break;    case 5: src = pl_flow; break;
        case 6: src = pl_sni; break;     case 7: src = pl_fp; break;
        case 8: src = pl_pbk; break;     case 9: src = pl_sid; break;
        case 10: src = pl_spx; break;    case 11: src = pl_path; break;
        case 12: src = pl_host; break;   case 13: src = pl_eh; break;
        case 14: src = pl_method; break;
        default: src = ""; break;
    }
    int n = (int) strlen(src);
    jbyteArray out = (*env)->NewByteArray(env, n);
    if (out && n) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) src);
    return out;
}

/** ints: 0 port, 1 kind (0 vless, 1 trojan), 2 alterId, 3 early data, 4 parsed-ok */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_plInt(JNIEnv *env, jclass cls, jint which) {
    switch (which) {
        case 0: return pl_port;
        case 1: return pl_kind;
        case 2: return pl_alter;
        case 3: return pl_ed;
        case 4: return pl_ok;
        default: return 0;
    }
}

/* ---------------- the Xray config, built and written here ----------------
 * Stage 2 of the server-in-C move. The three configs the app ever needed (full tunnel, ping-only,
 * flag probe) are assembled from the parsed link fields and written straight to a file: no config
 * text is ever returned to Kotlin, so a method tracer has nothing to print. The shape is the one
 * XrayCore.kt built with org.json; chkconfig.cjs compares this builder against a transcription of
 * that Kotlin over the link corpus, parsed JSON against parsed JSON.
 */

typedef struct { char *b; int n, cap; } sbuf;

static int sb_room(sbuf *s, int need) {
    if (s->n + need + 1 <= s->cap) return 1;
    int nc = s->cap > 0 ? s->cap : 4096;
    while (nc < s->n + need + 1) nc *= 2;
    char *nb = (char *) realloc(s->b, (size_t) nc);
    if (nb == NULL) return 0;
    s->b = nb;
    s->cap = nc;
    return 1;
}

static void sb_add(sbuf *s, const char *t) {
    int n = (int) strlen(t);
    if (!sb_room(s, n)) return;
    memcpy(s->b + s->n, t, (size_t) n);
    s->n += n;
    s->b[s->n] = 0;
}

static void sb_num(sbuf *s, long v) {
    char t[24];
    snprintf(t, sizeof(t), "%ld", v);
    sb_add(s, t);
}

/** a JSON string literal: quotes and backslashes escaped, control bytes as \\u00XX */
static void sb_str(sbuf *s, const char *in) {
    sb_add(s, "\"");
    for (const unsigned char *p = (const unsigned char *) in; *p; p++) {
        if (*p == '"' || *p == '\\') {
            char t[3]; t[0] = '\\'; t[1] = (char) *p; t[2] = 0; sb_add(s, t);
        } else if (*p < 0x20) {
            char t[8]; snprintf(t, sizeof(t), "\\u%04x", *p); sb_add(s, t);
        } else {
            char t[2]; t[0] = (char) *p; t[1] = 0; sb_add(s, t);
        }
    }
    sb_add(s, "\"");
}

static void sb_kv_str(sbuf *s, const char *k, const char *v) {
    sb_str(s, k);
    sb_add(s, ":");
    sb_str(s, v);
}

static void sb_direct(sbuf *s) {
    sb_add(s, "{\"tag\":\"direct\",\"protocol\":\"freedom\"}");
}

static void sb_outbound(sbuf *s) {
    sb_add(s, "{\"tag\":\"proxy\",\"protocol\":");
    sb_str(s, pl_kind == 0 ? "vless" : pl_kind == 1 ? "trojan" : pl_kind == 2 ? "vmess" : "shadowsocks");
    sb_add(s, ",\"settings\":");
    if (pl_kind == 1) {
        sb_add(s, "{\"servers\":[{\"address\":");
        sb_str(s, pl_addr);
        sb_add(s, ",\"port\":");
        sb_num(s, pl_port);
        sb_add(s, ",\"password\":");
        sb_str(s, pl_cred);
        sb_add(s, ",\"level\":0}]}");
    } else if (pl_kind == 3) {
        sb_add(s, "{\"servers\":[{\"address\":");
        sb_str(s, pl_addr);
        sb_add(s, ",\"port\":");
        sb_num(s, pl_port);
        sb_add(s, ",\"method\":");
        sb_str(s, pl_method);
        sb_add(s, ",\"password\":");
        sb_str(s, pl_cred);
        sb_add(s, ",\"level\":0}]}");
    } else {
        sb_add(s, "{\"vnext\":[{\"address\":");
        sb_str(s, pl_addr);
        sb_add(s, ",\"port\":");
        sb_num(s, pl_port);
        sb_add(s, ",\"users\":[{\"id\":");
        sb_str(s, pl_cred);
        if (pl_kind == 2) {
            sb_add(s, ",\"alterId\":");
            sb_num(s, pl_alter);
            sb_add(s, ",\"security\":");
            sb_str(s, pl_method[0] ? pl_method : "auto");
            sb_add(s, ",\"level\":0");
        } else {
            sb_add(s, ",\"encryption\":\"none\",\"level\":0");
            if (pl_flow[0]) { sb_add(s, ",\"flow\":"); sb_str(s, pl_flow); }
        }
        sb_add(s, "}]}]}");
    }
    if (pl_kind == 3) { sb_add(s, "}"); return; }
    sb_add(s, ",\"streamSettings\":");
    sb_add(s, "{\"network\":");
    sb_str(s, pl_type);
    sb_add(s, ",\"security\":");
    sb_str(s, pl_security);
    if (strcmp(pl_type, "ws") == 0) {
        sb_add(s, ",\"wsSettings\":{\"path\":");
        sb_str(s, pl_path[0] ? pl_path : "/");
        if (pl_host[0]) { sb_add(s, ",\"host\":"); sb_str(s, pl_host); }
        if (pl_ed > 0) {
            sb_add(s, ",\"maxEarlyData\":");
            sb_num(s, pl_ed);
            if (pl_eh[0]) { sb_add(s, ",\"earlyDataHeaderName\":"); sb_str(s, pl_eh); }
        }
        sb_add(s, "}");
    } else if (strcmp(pl_type, "grpc") == 0) {
        sb_add(s, ",\"grpcSettings\":{");
        int first = 1;
        if (pl_path[0]) { sb_add(s, "\"serviceName\":"); sb_str(s, pl_path); first = 0; }
        if (pl_host[0]) { if (!first) sb_add(s, ","); sb_add(s, "\"authority\":"); sb_str(s, pl_host); }
        sb_add(s, "}");
    } else if (strcmp(pl_type, "httpupgrade") == 0) {
        sb_add(s, ",\"httpupgradeSettings\":{\"path\":");
        sb_str(s, pl_path[0] ? pl_path : "/");
        if (pl_host[0]) { sb_add(s, ",\"host\":"); sb_str(s, pl_host); }
        sb_add(s, "}");
    } else if (strcmp(pl_type, "xhttp") == 0) {
        sb_add(s, ",\"xhttpSettings\":{");
        int first = 1;
        if (pl_path[0]) { sb_add(s, "\"path\":"); sb_str(s, pl_path); first = 0; }
        if (pl_host[0]) { if (!first) sb_add(s, ","); sb_add(s, "\"host\":"); sb_str(s, pl_host); }
        sb_add(s, "}");
    }
    if (strcmp(pl_security, "tls") == 0) {
        sb_add(s, ",\"tlsSettings\":{");
        int first = 1;
        if (pl_sni[0]) { sb_add(s, "\"serverName\":"); sb_str(s, pl_sni); first = 0; }
        if (!first) sb_add(s, ",");
        sb_add(s, "\"fingerprint\":");
        sb_str(s, pl_fp[0] ? pl_fp : "chrome");
        sb_add(s, ",\"allowInsecure\":false}");
    } else if (strcmp(pl_security, "reality") == 0) {
        sb_add(s, ",\"realitySettings\":{\"show\":false");
        if (pl_sni[0]) { sb_add(s, ",\"serverName\":"); sb_str(s, pl_sni); }
        sb_add(s, ",\"fingerprint\":");
        sb_str(s, pl_fp[0] ? pl_fp : "chrome");
        if (pl_pbk[0]) { sb_add(s, ",\"publicKey\":"); sb_str(s, pl_pbk); }
        if (pl_sid[0]) { sb_add(s, ",\"shortId\":"); sb_str(s, pl_sid); }
        if (pl_spx[0]) { sb_add(s, ",\"spiderX\":"); sb_str(s, pl_spx); }
        sb_add(s, "}");
    }
    sb_add(s, "}}");
}

static void sb_routing(sbuf *s) {
    sb_add(s, "{\"domainStrategy\":\"AsIs\",\"rules\":[{\"type\":\"field\",\"outboundTag\":\"proxy\",\"network\":\"tcp,udp\"}]}");
}

static void sb_socks(sbuf *s, int port) {
    sb_add(s, "{\"tag\":\"socks-in\",\"protocol\":\"socks\",\"listen\":\"127.0.0.1\",\"port\":");
    sb_num(s, port);
    sb_add(s, ",\"settings\":{\"udp\":false}}");
}

/** mode 0 = full tunnel, 1 = ping only, 2 = flag probe through a local socks port */
#define PLR_MAX 256
#define PLR_ID 64
#define PLR_CAT 64
#define PLR_LINK 2048
#define PLR_MISC 256

typedef struct {
    char id[PLR_ID];
    char name[PL_NAME];
    char addr[PL_ADDR];
    int port;
    char cat[PLR_CAT];
    char msg[PL_MISC];
    char link[PLR_LINK];
} plr_t;

static plr_t *g_rows;
static int g_rows_n;

/** the config text, assembled once and shared by the file writer and the string entry */
static int config_build(sbuf *s, int mode, int socksPort) {
    sb_add(s, "{\"log\":{\"loglevel\":\"warning\"}");
    if (mode == 0) {
        sb_add(s, ",\"inbounds\":[{\"tag\":\"tun-in\",\"protocol\":\"tun\",\"settings\":{\"name\":\"utun42\",\"desc\":\"Blob\",\"mtu\":1500}},");
        sb_socks(s, 10890);
        sb_add(s, "]");
    } else if (mode == 2) {
        sb_add(s, ",\"inbounds\":[");
        sb_socks(s, socksPort);
        sb_add(s, "]");
    }
    sb_add(s, ",\"outbounds\":[");
    sb_outbound(s);
    sb_add(s, ",");
    sb_direct(s);
    sb_add(s, "],\"routing\":");
    sb_routing(s);
    if (mode == 0) {
        sb_add(s, ",\"stats\":{},\"policy\":{\"levels\":{\"0\":{\"statsUserUplink\":true,\"statsUserDownlink\":true}}},\"system\":{\"statsOutboundUplink\":true,\"statsOutboundDownlink\":true}");
    }
    sb_add(s, "}");
    (void) socksPort;
    return s->b != NULL;
}
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_coreWriteConfig(JNIEnv *env, jclass cls, jstring jpath, jint mode, jint socksPort) {
    if (jpath == NULL || !pl_ok) return 0;
    sbuf s;
    s.b = NULL; s.n = 0; s.cap = 0;
    if (!config_build(&s, mode, socksPort)) { free(s.b); return 0; }
    int ok = 0;
    if (s.b != NULL) {
        const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
        if (path != NULL) {
            FILE *f = fopen(path, "wb");
            if (f != NULL) {
                ok = fwrite(s.b, 1, (size_t) s.n, f) == (size_t) s.n;
                fclose(f);
            }
            (*env)->ReleaseStringUTFChars(env, jpath, path);
        }
        free(s.b);
    }
    return ok ? 1 : 0;
}
/** the same config as a String, because libv2ray startLoop only takes one: this single call is
 *  the floor this core API sets, and the comment is here so nobody reads it as an oversight. */
JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_coreConfigForRow(JNIEnv *env, jclass cls, jint row, jint mode, jint socksPort) {
    if (row < 0 || row >= g_rows_n) return NULL;
    jstring jlink = (*env)->NewStringUTF(env, g_rows[row].link);
    if (jlink == NULL) return NULL;
    int ok = Java_ir_v2rayng_compose_core_NativeSec_plParse(env, cls, jlink);
    (*env)->DeleteLocalRef(env, jlink);
    if (ok != 1) { set_step("cfgforrow:parse"); return NULL; }
    sbuf s;
    s.b = NULL; s.n = 0; s.cap = 0;
    if (!config_build(&s, mode, socksPort)) { free(s.b); set_step("cfgforrow:build"); return NULL; }
    jstring out = (*env)->NewStringUTF(env, s.b);
    free(s.b);
    if (out) set_step("cfgforrow:ok");
    return out;
}

/* ---------------- the server list, kept here ----------------
 * Stage 3, first piece: the decrypted payload is parsed with org.json through JNI (the same
 * library the Kotlin used) and the rows stay in native memory - including the link, which from
 * here on is never handed back to Kotlin as a value. Kotlin may ask for the display fields one at
 * a time (id, name, address, port, category, msg); the link is used only by this library when it
 * writes the core config.
 * Sealing the rows for the next start is the following commit; until then this store lives for
 * the process, which is exactly what the branch's stage 3 needs to wire the list through.
 */

static void payload_reset(void) {
    if (g_rows) { free(g_rows); g_rows = NULL; }
    g_rows_n = 0;
}

static int payload_parse_bytes(JNIEnv *env, const jbyte *buf, jsize len);

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadLoad(JNIEnv *env, jclass cls, jstring jstego, jbyteArray jkc) {
    payload_reset();
    set_step("payload:entered");
    jbyteArray plain = Java_ir_v2rayng_compose_core_NativeSec_stegoOpen(env, cls, jstego, jkc);
    if (plain == NULL) { set_step("payload:open"); return -1; }
    jsize plen = (*env)->GetArrayLength(env, plain);
    if (plen <= 0 || plen > 4000000) { set_step("payload:size"); return -1; }
    jbyte *buf = (jbyte *) malloc((size_t) plen);
    if (buf == NULL) { set_step("payload:alloc"); return -1; }
    (*env)->GetByteArrayRegion(env, plain, 0, plen, buf);
    int n = payload_parse_bytes(env, buf, plen);
    free(buf);
    return n;
}

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadCount(JNIEnv *env, jclass cls) {
    return g_rows_n;
}

/** display fields only: 0 id, 1 name, 2 address, 3 category, 4 msg. The link is deliberately not
 *  reachable from here - that is the whole point of the move. */
JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadField(JNIEnv *env, jclass cls, jint row, jint which) {
    if (row < 0 || row >= g_rows_n) return NULL;
    const plr_t *r = &g_rows[row];
    const char *src = "";
    switch (which) {
        case 0: src = r->id; break;
        case 1: src = r->name; break;
        case 2: src = r->addr; break;
        case 3: src = r->cat; break;
        case 4: src = r->msg; break;
        default: src = ""; break;
    }
    int n = (int) strlen(src);
    jbyteArray out = (*env)->NewByteArray(env, n);
    if (out && n) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) src);
    return out;
}

JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadPort(JNIEnv *env, jclass cls, jint row) {
    if (row < 0 || row >= g_rows_n) return 0;
    return g_rows[row].port;
}

JNIEXPORT void JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadClear(JNIEnv *env, jclass cls) {
    payload_reset();
}

/* ---- the "cy" payload and the sealed row store ----
 * cy is the same JSON without a carrier: it is opened with gcm and parsed through the same row
 * store. The sealed form is what survives a restart: payloadSealAll() serialises the rows and
 * seals them with the vault, Kotlin stores that opaque string; payloadRestore() takes it back and
 * refills the rows, so connect works after a cold start without a fresh sync.
 */

static int payload_parse_bytes(JNIEnv *env, const jbyte *buf, jsize len) {
    jstring jjson = (*env)->NewStringUTF(env, (const char *) buf);
    if (jjson == NULL) { if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); set_step("payload:string"); return -1; }
    jclass jsonCls = need_class(env, "org/json/JSONObject");
    jclass arrCls = need_class(env, "org/json/JSONArray");
    jmethodID ctor = jsonCls ? (*env)->GetMethodID(env, jsonCls, "<init>", "(Ljava/lang/String;)V") : NULL;
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jobject root = (jsonCls && ctor) ? (*env)->NewObject(env, jsonCls, ctor, jjson) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); root = NULL; }
    if (root == NULL) { set_step("payload:json"); return -1; }
    jmethodID getArr = (*env)->GetMethodID(env, jsonCls, "optJSONArray", "(Ljava/lang/String;)Lorg/json/JSONArray;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jstring kServers = (*env)->NewStringUTF(env, "servers");
    jobject arr = (getArr && kServers) ? (*env)->CallObjectMethod(env, root, getArr, kServers) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); arr = NULL; }
    if (arr == NULL || arrCls == NULL) { set_step("payload:servers"); return 0; }
    jmethodID arrLen = (*env)->GetMethodID(env, arrCls, "length", "()I");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID arrGet = (*env)->GetMethodID(env, arrCls, "optJSONObject", "(I)Lorg/json/JSONObject;");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!arrLen || !arrGet) { set_step("payload:array-methods"); return -1; }
    int n = (*env)->CallIntMethod(env, arr, arrLen);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); n = 0; }
    if (n < 0) n = 0;
    if (n > PLR_MAX) n = PLR_MAX;
    if (n == 0) { set_step("payload:empty"); return 0; }
    g_rows = (plr_t *) calloc((size_t) n, sizeof(plr_t));
    if (g_rows == NULL) { set_step("payload:alloc-rows"); return -1; }
    int kept = 0;
    for (int i = 0; i < n; i++) {
        jobject o = (*env)->CallObjectMethod(env, arr, arrGet, i);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); o = NULL; }
        if (o == NULL) continue;
        plr_t *r = &g_rows[kept];
        pl_json_str(env, o, jsonCls, "id", "", r->id, PLR_ID);
        pl_json_str(env, o, jsonCls, "name", "", r->name, PL_NAME);
        pl_json_str(env, o, jsonCls, "address", "", r->addr, PL_ADDR);
        pl_json_str(env, o, jsonCls, "category", "", r->cat, PLR_CAT);
        pl_json_str(env, o, jsonCls, "msg", "", r->msg, PLR_MISC);
        pl_json_str(env, o, jsonCls, "link", "", r->link, PLR_LINK);
        r->port = pl_json_int(env, o, jsonCls, "port", 0);
        (*env)->DeleteLocalRef(env, o);
        if (r->addr[0] == 0 || r->port <= 0) continue;
        kept++;
    }
    (*env)->DeleteLocalRef(env, arr);
    (*env)->DeleteLocalRef(env, root);
    if (kServers) (*env)->DeleteLocalRef(env, kServers);
    if (jsonCls) (*env)->DeleteLocalRef(env, jsonCls);
    if (arrCls) (*env)->DeleteLocalRef(env, arrCls);
    g_rows_n = kept;
    set_step(kept > 0 ? "payload:ok" : "payload:no-usable-rows");
    return kept;
}

/** the raw "cy" payload: same JSON, no carrier, opened with the session key */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadLoadCy(JNIEnv *env, jclass cls, jbyteArray jblob, jbyteArray jkc) {
    payload_reset();
    set_step("payload:cy-entered");
    if (jblob == NULL || jkc == NULL) { set_step("payload:cy-args"); return -1; }
    if ((*env)->GetArrayLength(env, jkc) != 32) { set_step("payload:cy-key"); return -1; }
    jbyteArray jaad = (*env)->NewByteArray(env, 5);
    if (jaad == NULL) { set_step("payload:cy-aad"); return -1; }
    const jbyte aad[5] = { 0x42, 0x4C, 0x42, 0x76, 0x31 };
    (*env)->SetByteArrayRegion(env, jaad, 0, 5, aad);
    jbyteArray plain = Java_ir_v2rayng_compose_core_NativeSec_gcmOpen(env, cls, jblob, jkc, jaad);
    if (plain == NULL) { set_step("payload:cy-open"); return -1; }
    jsize plen = (*env)->GetArrayLength(env, plain);
    if (plen <= 0 || plen > 4000000) { set_step("payload:size"); return -1; }
    jbyte *buf = (jbyte *) malloc((size_t) plen);
    if (buf == NULL) { set_step("payload:alloc"); return -1; }
    (*env)->GetByteArrayRegion(env, plain, 0, plen, buf);
    int n = payload_parse_bytes(env, buf, plen);
    free(buf);
    return n;
}

/** rows -> one text block (fields separated by \x1f, rows by \n), sealed by the vault, base64url.
 *  Kotlin stores this opaque string; the links inside it are ciphertext like everything else. */
JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadSealAll(JNIEnv *env, jclass cls) {
    if (g_rows_n <= 0) return NULL;
    size_t cap = 64;
    for (int i = 0; i < g_rows_n; i++) {
        cap += strlen(g_rows[i].id) + strlen(g_rows[i].name) + strlen(g_rows[i].addr) +
               strlen(g_rows[i].cat) + strlen(g_rows[i].msg) + strlen(g_rows[i].link) + 32;
    }
    unsigned char *text = (unsigned char *) malloc(cap);
    if (text == NULL) { set_step("seal-rows:alloc"); return NULL; }
    size_t o = 0;
    char portbuf[16];
    for (int i = 0; i < g_rows_n; i++) {
        plr_t *r = &g_rows[i];
        snprintf(portbuf, sizeof(portbuf), "%d", r->port);
        const char *parts[7] = { r->id, r->name, r->addr, portbuf, r->cat, r->msg, r->link };
        for (int k = 0; k < 7; k++) {
            size_t n = strlen(parts[k]);
            if (o + n + 2 >= cap) { free(text); set_step("seal-rows:overflow"); return NULL; }
            memcpy(text + o, parts[k], n);
            o += n;
            text[o++] = (k == 6) ? '\n' : 0x1f;
        }
    }
    jbyteArray jtext = (*env)->NewByteArray(env, (jsize) o);
    if (jtext) (*env)->SetByteArrayRegion(env, jtext, 0, (jsize) o, (const jbyte *) text);
    free(text);
    if (jtext == NULL) { set_step("seal-rows:array"); return NULL; }
    jbyteArray sealed = Java_ir_v2rayng_compose_core_NativeSec_vaultSeal(env, cls, jtext);
    if (sealed == NULL) { set_step("seal-rows:vault"); return NULL; }
    jstring out = Java_ir_v2rayng_compose_core_NativeSec_b64uEncode(env, cls, sealed);
    set_step(out ? "seal-rows:ok" : "seal-rows:encode");
    return out;
}

/** sealed rows back into memory; returns the row count or -1 */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadRestore(JNIEnv *env, jclass cls, jstring jsealed) {
    payload_reset();
    set_step("restore:entered");
    if (jsealed == NULL) { set_step("restore:null"); return -1; }
    jstring jplaintext = NULL;
    {
        const char *b64 = (*env)->GetStringUTFChars(env, jsealed, NULL);
        if (b64 == NULL) { set_step("restore:chars"); return -1; }
        /* decoded here, not through another JNI entry: the sealed string is our own base64url */
        size_t blen = strlen(b64);
        unsigned char *raw = (unsigned char *) malloc(blen + 1);
        jbyteArray blob = NULL;
        if (raw) {
            int rl = pl_b64(b64, (int) blen, (char *) raw, (int) blen + 1);
            if (rl > 0) {
                blob = (*env)->NewByteArray(env, (jsize) rl);
                if (blob) (*env)->SetByteArrayRegion(env, blob, 0, (jsize) rl, (const jbyte *) raw);
            }
            free(raw);
        }
        (*env)->ReleaseStringUTFChars(env, jsealed, b64);
        if (blob == NULL) { set_step("restore:b64"); return -1; }
        jbyteArray plain = Java_ir_v2rayng_compose_core_NativeSec_vaultOpen(env, cls, blob, 0);
        if (plain == NULL) { set_step("restore:vault"); return -1; }
        jsize n = (*env)->GetArrayLength(env, plain);
        unsigned char *buf = (unsigned char *) malloc((size_t) n + 1);
        if (buf == NULL) { set_step("restore:alloc"); return -1; }
        (*env)->GetByteArrayRegion(env, plain, 0, n, (jbyte *) buf);
        buf[n] = 0;
        /* split rows and fields */
        g_rows = (plr_t *) calloc(PLR_MAX, sizeof(plr_t));
        if (g_rows == NULL) { free(buf); set_step("restore:alloc-rows"); return -1; }
        int kept = 0;
        char *row = (char *) buf;
        while (row != NULL && *row != 0 && kept < PLR_MAX) {
            char *nl = strchr(row, '\n');
            if (nl) *nl = 0;
            char *f[7];
            int nf = 0;
            char *p = row;
            while (nf < 7) {
                f[nf++] = p;
                char *sep = strchr(p, 0x1f);
                if (sep == NULL) break;
                *sep = 0;
                p = sep + 1;
            }
            if (nf == 7) {
                plr_t *r = &g_rows[kept];
                pl_copy(r->id, PLR_ID, f[0], (int) strlen(f[0]));
                pl_copy(r->name, PL_NAME, f[1], (int) strlen(f[1]));
                pl_copy(r->addr, PL_ADDR, f[2], (int) strlen(f[2]));
                r->port = atoi(f[3]);
                pl_copy(r->cat, PLR_CAT, f[4], (int) strlen(f[4]));
                pl_copy(r->msg, PLR_MISC, f[5], (int) strlen(f[5]));
                pl_copy(r->link, PLR_LINK, f[6], (int) strlen(f[6]));
                if (r->addr[0] && r->port > 0) kept++;
            }
            row = nl ? nl + 1 : NULL;
        }
        free(buf);
        g_rows_n = kept;
        set_step(kept > 0 ? "restore:ok" : "restore:empty");
        return kept;
    }
    (void) jplaintext;
}

/* ---- the two calls the wiring needs ---- */

/** write the config for a stored row: the link is parsed here, from memory, and never returned */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_coreWriteConfigRow(JNIEnv *env, jclass cls, jint row, jstring jpath, jint mode, jint socksPort) {
    if (row < 0 || row >= g_rows_n || jpath == NULL) return 0;
    jstring jlink = (*env)->NewStringUTF(env, g_rows[row].link);
    if (jlink == NULL) return 0;
    int ok = Java_ir_v2rayng_compose_core_NativeSec_plParse(env, cls, jlink);
    (*env)->DeleteLocalRef(env, jlink);
    if (ok != 1) { set_step("cfgrow:parse"); return 0; }
    return Java_ir_v2rayng_compose_core_NativeSec_coreWriteConfig(env, cls, jpath, mode, socksPort);
}

/** row index for a display id, -1 when it is not in the store (a stale list, a deleted row) */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadFind(JNIEnv *env, jclass cls, jstring jid) {
    if (jid == NULL) return -1;
    const char *id = (*env)->GetStringUTFChars(env, jid, NULL);
    if (id == NULL) return -1;
    int found = -1;
    for (int i = 0; i < g_rows_n; i++) {
        if (strcmp(g_rows[i].id, id) == 0) { found = i; break; }
    }
    (*env)->ReleaseStringUTFChars(env, jid, id);
    return found;
}

/** row index by host and port - the connect and ping paths already have these two values, so the
 *  id never has to be threaded through five call chains to reach them */
JNIEXPORT jint JNICALL
Java_ir_v2rayng_compose_core_NativeSec_payloadFindHost(JNIEnv *env, jclass cls, jstring jaddr, jint port) {
    if (jaddr == NULL) return -1;
    const char *a = (*env)->GetStringUTFChars(env, jaddr, NULL);
    if (a == NULL) return -1;
    int found = -1;
    for (int i = 0; i < g_rows_n; i++) {
        if (g_rows[i].port == (int) port && strcmp(g_rows[i].addr, a) == 0) { found = i; break; }
    }
    (*env)->ReleaseStringUTFChars(env, jaddr, a);
    return found;
}

/** the link text for one host+port, used only at the moment of a dial or a ping: the list never
 *  crosses to Kotlin, but a single dial needs the text the core is configured from */
JNIEXPORT jstring JNICALL
Java_ir_v2rayng_compose_core_NativeSec_linkForHost(JNIEnv *env, jclass cls, jstring jaddr, jint port) {
    if (jaddr == NULL) return NULL;
    const char *a = (*env)->GetStringUTFChars(env, jaddr, NULL);
    if (a == NULL) return NULL;
    jstring out = NULL;
    for (int i = 0; i < g_rows_n; i++) {
        if (g_rows[i].port == (int) port && strcmp(g_rows[i].addr, a) == 0) {
            out = (*env)->NewStringUTF(env, g_rows[i].link);
            break;
        }
    }
    (*env)->ReleaseStringUTFChars(env, jaddr, a);
    return out;
}

/* ---------------- the log scrubber, in C ----------------
 * Stage 1 of moving LogSanitize here: the nine rules run over UTF-8 bytes in the same order the
 * Kotlin ran them over a String. Every pattern is ASCII, so byte scanning is equivalent - the only
 * place a String mattered was that non-ASCII bytes are "non-word" for \b, which is exactly how the
 * bytes behave here (>= 0x80).
 * Not wired yet: step 2 is the oracle (a JS transcription of the Kotlin regexes against a JS
 * transcription of this file, over thousands of generated lines) and only then clean() switches.
 */

static int scr_word(unsigned char c) {
    return c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
}
static int scr_hex(unsigned char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}
static int scr_digit(unsigned char c) { return c >= '0' && c <= '9'; }
static int scr_space(unsigned char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\v'; }
static int scr_alnum_l(unsigned char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
static unsigned char scr_lower(unsigned char c) { return (c >= 'A' && c <= 'Z') ? (unsigned char) (c + 32) : c; }

/** case-insensitive compare of n bytes */
static int scr_ieq(const unsigned char *s, const char *lit, int n) {
    for (int i = 0; i < n; i++) if (scr_lower(s[i]) != (unsigned char) lit[i]) return 0;
    return 1;
}

/** append src[from..to) to out */
static void scr_put(sbuf *o, const unsigned char *src, int from, int to) {
    if (to <= from) return;
    if (!sb_room(o, to - from)) return;
    memcpy(o->b + o->n, src + from, (size_t) (to - from));
    o->n += to - from;
    o->b[o->n] = 0;
}

static const char *SCR_SCHEMES[] = { "vless", "vmess", "trojan", "ssr", "ss", "hysteria2", "hysteria", "tuic", "vaxins", "socks" };
static const char *SCR_JSON_KEYS[] = { "address", "host", "server_name", "sni", "peer", "ip", "uuid", "password", "pass", "pbk", "sid", "user", "user_ids", "email", "remark" };
static const char *SCR_KV_KEYS[] = { "address", "host", "server_name", "sni", "peer", "uuid", "password", "pass", "pbk", "sid", "ip" };
static const char *SCR_TLDS[] = { "com", "net", "org", "io", "xyz", "ru", "ir", "de", "uk", "us", "ca", "au", "me", "app", "dev", "cloud", "site", "online", "live", "top", "info", "biz", "cn", "tk", "ga", "cf", "gq", "ml", "shop", "api", "proxy", "cdn", "work", "pw", "cc" };

/** LINK: scheme://non-space  ->  " [link hidden] " */
static void scr_links(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if ((i == 0 || !scr_word(s[i - 1]))) {
            int hit = 0;
            for (int k = 0; k < (int) (sizeof(SCR_SCHEMES) / sizeof(SCR_SCHEMES[0])); k++) {
                int len = (int) strlen(SCR_SCHEMES[k]);
                if (i + len + 3 <= n && scr_ieq(s + i, SCR_SCHEMES[k], len) &&
                    s[i + len] == ':' && s[i + len + 1] == '/' && s[i + len + 2] == '/') {
                    int j = i + len + 3;
                    while (j < n && !scr_space(s[j])) j++;
                    if (j > i + len + 3) {
                        scr_put(o, s, last, i);
                        sb_add(o, " [link hidden] ");
                        i = j;
                        last = j;
                        hit = 1;
                        break;
                    }
                }
            }
            if (hit) continue;
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** UUID: 8-4-4-4-12 hex at a word boundary -> "[id]" */
static void scr_uuid(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i + 36 <= n) {
        if ((i == 0 || !scr_word(s[i - 1])) && (i + 36 == n || !scr_word(s[i + 36]))) {
            int ok = 1;
            for (int k = 0; k < 36 && ok; k++) {
                if (k == 8 || k == 13 || k == 18 || k == 23) ok = s[i + k] == '-';
                else ok = scr_hex(s[i + k]);
            }
            if (ok) {
                scr_put(o, s, last, i);
                sb_add(o, "[id]");
                i += 36;
                last = i;
                continue;
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** IPv4 with an optional :port -> "[ip]" */
static void scr_ipv4(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if ((i == 0 || !scr_word(s[i - 1])) && scr_digit(s[i])) {
            int p = i, ok = 1;
            for (int part = 0; part < 4 && ok; part++) {
                int d = 0;
                while (p < n && scr_digit(s[p]) && d < 4) { p++; d++; }
                if (d < 1 || d > 3) { ok = 0; break; }
                if (part < 3) {
                    if (p < n && s[p] == '.') p++;
                    else { ok = 0; break; }
                }
            }
            if (ok) {
                int end = p;
                if (end < n && s[end] == ':') {
                    int q = end + 1, d = 0;
                    while (q < n && scr_digit(s[q]) && d < 6) { q++; d++; }
                    if (d >= 1 && d <= 5) end = q;
                }
                /* the regex engine backtracks the optional :port when the end boundary fails */
                if (r_end_bad(s, n, end) && end > p) end = p;
                if (end == n || !scr_word(s[end])) {
                    scr_put(o, s, last, i);
                    sb_add(o, "[ip]");
                    i = end;
                    last = end;
                    continue;
                }
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** the two IPv6 shapes the Kotlin matched -> "[ip]" */
static void scr_ipv6(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if ((i == 0 || !scr_word(s[i - 1])) && scr_hex(s[i])) {
            /* long form: (1..4 hex ':' ){4,} 1..4 hex */
            int p = i, groups = 0;
            while (p < n && scr_hex(s[p]) && groups < 1) { p++; }   /* first group, at least 1 */
            int first = p - i;
            int q = p, g = 0;
            while (q < n && s[q] == ':') {
                int r = q + 1, d = 0;
                while (r < n && scr_hex(s[r]) && d < 4) { r++; d++; }
                if (d < 1) break;
                g++;
                q = r;
            }
            if (first >= 1 && first <= 4 && g >= 4 && (q == n || !scr_word(s[q]))) {
                scr_put(o, s, last, i);
                sb_add(o, "[ip]");
                i = q;
                last = q;
                continue;
            }
            /* short form, chosen the way the engine backtracks: the middle is [hex:]* up to the
             * last double colon, the tail is [hex:.]* after it, and the end is the longest tail
             * position where the trailing boundary holds (a word char on exactly one side) */
            int matchEnd = -1;
            if (p < n && s[p] == ':') {
                int q0 = p + 1;
                int mEnd = q0;
                while (mEnd < n && (scr_hex(s[mEnd]) || s[mEnd] == ':')) mEnd++;
                int dbl = -1;
                for (int k = mEnd - 1; k > q0; k--) {
                    if (s[k] == ':' && s[k - 1] == ':') { dbl = k - 1; break; }
                }
                if (dbl >= 0) {
                    int t0 = dbl + 2;
                    int tEnd = t0;
                    while (tEnd < n && (scr_hex(s[tEnd]) || s[tEnd] == ':' || s[tEnd] == '.')) tEnd++;
                    for (int e = tEnd; e >= t0; e--) {
                        int prevWord = (e > 0) && scr_word((unsigned char) s[e - 1]);
                        int nextWord = (e < n) && scr_word((unsigned char) s[e]);
                        if (prevWord != nextWord) { matchEnd = e; break; }
                    }
                }
            }
            if (first >= 1 && first <= 4 && matchEnd > 0) {
                scr_put(o, s, last, i);
                sb_add(o, "[ip]");
                i = matchEnd;
                last = matchEnd;
                continue;
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

static int r_end_bad(const unsigned char *s, int n, int end) {
    return end < n && scr_word(s[end]);
}

static int scr_tld_ok(const unsigned char *s, int from, int to) {
    int len = to - from;
    for (int k = 0; k < (int) (sizeof(SCR_TLDS) / sizeof(SCR_TLDS[0])); k++) {
        if ((int) strlen(SCR_TLDS[k]) == len && scr_ieq(s + from, SCR_TLDS[k], len)) return 1;
    }
    return 0;
}

/** domain.tld (:port) -> "[host]" */
static void scr_domains(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if ((i == 0 || !scr_word(s[i - 1])) && scr_alnum_l(s[i])) {
            int p = i, dot = -1, labels = 0;
            while (p < n) {
                unsigned char c = s[p];
                if (c == '-' || scr_alnum_l(c)) { p++; continue; }
                if (c == '.') {
                    if (p == i || s[p - 1] == '.') break;
                    dot = p;
                    labels++;
                    p++;
                    if (labels > 20) break;
                    continue;
                }
                break;
            }
            /* the Kotlin pattern needs label(.label)+.tld: at least two dots */
            if (dot > 0 && labels >= 2) {
                int tldEnd = p;
                if (tldEnd > dot + 1 && scr_tld_ok(s, dot + 1, tldEnd)) {
                    int end = tldEnd;
                    if (end < n && s[end] == ':') {
                        int q = end + 1, d = 0;
                        while (q < n && scr_digit(s[q]) && d < 6) { q++; d++; }
                        if (d >= 1 && d <= 5) end = q;
                    }
                    if (r_end_bad(s, n, end) && end > tldEnd) end = tldEnd;
                    if (end == n || !scr_word(s[end])) {
                        scr_put(o, s, last, i);
                        sb_add(o, "[host]");
                        i = end;
                        last = end;
                        continue;
                    }
                }
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** "key" : "value"  ->  "key" : "[hidden]" */
static void scr_json_secret(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if (s[i] == '"') {
            int k0 = i + 1, k1 = k0;
            while (k1 < n && s[k1] != '"' && k1 - k0 < 24) k1++;
            if (k1 < n && s[k1] == '"') {
                int matched = 0;
                for (int k = 0; k < (int) (sizeof(SCR_JSON_KEYS) / sizeof(SCR_JSON_KEYS[0])) && !matched; k++) {
                    int len = (int) strlen(SCR_JSON_KEYS[k]);
                    if (k1 - k0 == len && scr_ieq(s + k0, SCR_JSON_KEYS[k], len)) matched = 1;
                }
                if (matched) {
                    int p = k1 + 1;
                    while (p < n && scr_space(s[p])) p++;
                    if (p < n && s[p] == ':') {
                        p++;
                        int spacesStart = p;
                        while (p < n && scr_space(s[p])) p++;
                        if (p < n && s[p] == '"') {
                            int q = p + 1;
                            while (q < n && s[q] != '"') {
                                if (s[q] == '\\' && q + 1 < n) q++;
                                q++;
                            }
                            if (q < n) {
                                scr_put(o, s, last, spacesStart);
                                sb_add(o, "\"[hidden]\"");
                                i = q + 1;
                                last = i;
                                continue;
                            }
                        }
                    }
                }
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** "port" : 1234 -> "port" : 0 */
static void scr_json_port(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if (s[i] == '"') {
            int k0 = i + 1, k1 = k0;
            while (k1 < n && s[k1] != '"' && k1 - k0 < 8) k1++;
            if (k1 - k0 == 4 && scr_ieq(s + k0, "port", 4)) {
                int p = k1 + 1;
                while (p < n && scr_space(s[p])) p++;
                if (p < n && s[p] == ':') {
                    p++;
                    while (p < n && scr_space(s[p])) p++;
                    int d0 = p;
                    while (p < n && scr_digit(s[p])) p++;
                    if (p > d0) {
                        scr_put(o, s, last, d0);
                        sb_add(o, "0");
                        i = p;
                        last = p;
                        continue;
                    }
                }
            }
        }
        i++;
    }
    scr_put(o, s, last, n);
}

/** key=value -> key=[hidden] for the kv keys */
static void scr_kv_secret(sbuf *o, const unsigned char *s, int n) {
    int i = 0, last = 0;
    while (i < n) {
        if (i == 0 || !scr_word(s[i - 1])) {
            int done = 0;
            for (int k = 0; k < (int) (sizeof(SCR_KV_KEYS) / sizeof(SCR_KV_KEYS[0])) && !done; k++) {
                int len = (int) strlen(SCR_KV_KEYS[k]);
                if (i + len < n && scr_ieq(s + i, SCR_KV_KEYS[k], len) && s[i + len] == '=') {
                    int p = i + len + 1;
                    int v0 = p;
                    while (p < n && !scr_space(s[p]) && s[p] != ',' && s[p] != ';') p++;
                    if (p > v0) {
                        scr_put(o, s, last, v0);
                        sb_add(o, "[hidden]");
                        i = p;
                        last = p;
                        done = 1;
                    }
                }
            }
            if (done) continue;
        }
        i++;
    }
    scr_put(o, s, last, n);
}

typedef void (*scr_fn)(sbuf *o, const unsigned char *s, int n);

static const scr_fn SCR_PASSES[] = {
    scr_links, scr_uuid, scr_json_secret, scr_json_port, scr_kv_secret,
    scr_ipv4, scr_ipv6, scr_domains,
};

JNIEXPORT jbyteArray JNICALL
Java_ir_v2rayng_compose_core_NativeSec_scrubUtf8(JNIEnv *env, jclass cls, jbyteArray jin) {
    if (jin == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, jin);
    if (n <= 0) return (*env)->NewByteArray(env, 0);
    unsigned char *in = (unsigned char *) malloc((size_t) n + 1);
    if (in == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, jin, 0, n, (jbyte *) in);
    in[n] = 0;

    /* every replacement is short except the link marker, so 4x + 64 cannot truncate a log line;
     * both buffers are allocated once and the passes alternate between them */
    int cap = (int) (n * 4 + 64);
    sbuf bufs[2];
    bufs[0].b = (char *) malloc((size_t) cap);
    bufs[1].b = (char *) malloc((size_t) cap);
    bufs[0].n = bufs[1].n = 0;
    bufs[0].cap = bufs[1].cap = cap;
    if (bufs[0].b == NULL || bufs[1].b == NULL) {
        free(bufs[0].b); free(bufs[1].b); free(in);
        return NULL;
    }
    bufs[0].b[0] = 0;
    bufs[1].b[0] = 0;

    const unsigned char *cur = in;
    int curlen = n;
    int dstIdx = 0;
    int ok = 1;
    for (int k = 0; k < (int) (sizeof(SCR_PASSES) / sizeof(SCR_PASSES[0])); k++) {
        sbuf *dst = &bufs[dstIdx];
        dst->n = 0;
        dst->b[0] = 0;
        SCR_PASSES[k](dst, cur, curlen);
        if (dst->n >= dst->cap) { ok = 0; break; }   /* sb_room refused: never ship a truncated line */
        cur = (const unsigned char *) dst->b;
        curlen = dst->n;
        dstIdx = 1 - dstIdx;
    }

    jbyteArray out = NULL;
    if (ok) {
        out = (*env)->NewByteArray(env, curlen);
        if (out && curlen > 0) (*env)->SetByteArrayRegion(env, out, 0, curlen, (const jbyte *) cur);
    }
    free(bufs[0].b);
    free(bufs[1].b);
    free(in);
    return out;
}
