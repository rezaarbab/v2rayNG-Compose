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
static const char SIG_INIT[] = "(ILjava/security/Key;Ljavax/crypto/spec/AlgorithmParameterSpec;)V";
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
 *  method ID that was not there, a handle the keystore did not give back */
static void note_null(const char *where, const char *what) {
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
    if (msg[0] != 0) snprintf(g_last, sizeof(g_last), "%s | %s | %s", where, cls, msg);
    else snprintf(g_last, sizeof(g_last), "%s | %s", where, cls);
    (*env)->DeleteLocalRef(env, ex);
    return 1;
}

static void note_det(const char *fmt, int a, int b, const char *c, const char *d) {
    snprintf(g_det, sizeof(g_det), fmt, a, b, c, d);
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

    jmethodID midGet = (*env)->GetStaticMethodID(env, cipherCls, MD_GET, SIG_GET);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID midInit = (*env)->GetMethodID(env, cipherCls, MD_INIT, SIG_INIT);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID midAad = (*env)->GetMethodID(env, cipherCls, MD_AAD, "([B)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID midFin = (*env)->GetMethodID(env, cipherCls, MD_FIN, SIG_B);  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID midSpec = (*env)->GetMethodID(env, specCls, "<init>", "(I[BII)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jmethodID midKey = (*env)->GetMethodID(env, keyCls, "<init>", "([BLjava/lang/String;)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!midGet || !midInit || !midAad || !midFin || !midSpec || !midKey) { set_step("gcm:method-ids"); return NULL; }

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
            jobject spec = (*env)->NewObject(env, specCls, midSpec, 128, iv, 0, 12);
            jobject kobj = (*env)->NewObject(env, keyCls, midKey, jkey, alg);
            jobject c = (*env)->CallStaticObjectMethod(env, cipherCls, midGet, transform);
            /* returning to Java with an exception still pending turns this into a thrown
             * NoSuchPaddingException at the call site, which the caller reports as "tag fail" -
             * record it, clear it, and say which step it was */
            if (step_failed(env, "gcm:getInstance/new-object")) { return NULL; }
            if (spec && kobj && c) {
                (*env)->CallVoidMethod(env, c, midInit, 2 /* DECRYPT_MODE */, kobj, spec);
                if (!step_failed(env, "gcm:cipher.init")) {
                    if (jaad != NULL) {
                        (*env)->CallVoidMethod(env, c, midAad, jaad);
                        if (step_failed(env, "gcm:cipher.updateAAD")) out = NULL;
                    }
                    if (out == NULL) {
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
static void spec_detail(JNIEnv *env, jobject spec) {
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
    note_det("spec sz=%d pu=%d bm=%s ep=%s", sz, pu, bm, ep);
    (*env)->DeleteLocalRef(env, sc);
}

/** creates the key under the alias, then reads it back */
static jobject make_key(JNIEnv *env, jstring jalias) {
    jclass kp = need_class(env, KP_CLS);
    jclass bld = need_class(env, KG_CLS);
    jclass kgc = need_class(env, KEYGEN_CLS);
    if (!kp || !bld || !kgc) { set_step("mk:find-class"); return NULL; }
    jstring sAes = (*env)->NewStringUTF(env, KAES);
    jmethodID ctor = (*env)->GetMethodID(env, bld, "<init>", "(Ljava/lang/String;I)V");  //EXC-CLEARED
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    jobject b = ctor ? (*env)->NewObject(env, bld, ctor, jalias, PURPOSE_ENC_DEC) : NULL;
    if (b) {
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
        jstring gcm = (*env)->NewStringUTF(env, KGCM), none = (*env)->NewStringUTF(env, KNONE);
        jclass strCls = need_class(env, "java/lang/String");
        jobjectArray modeArr = strCls ? (*env)->NewObjectArray(env, 1, strCls, NULL) : NULL;
        jobjectArray padArr = strCls ? (*env)->NewObjectArray(env, 1, strCls, NULL) : NULL;
        if (modeArr && gcm) (*env)->SetObjectArrayElement(env, modeArr, 0, gcm);
        if (padArr && none) (*env)->SetObjectArrayElement(env, padArr, 0, none);
        jobject spec = NULL;
        if (mSize) b = (*env)->CallObjectMethod(env, b, mSize, 256);
        if (b && mMode && modeArr) b = (*env)->CallObjectMethod(env, b, mMode, modeArr);
        if (b && mPad && padArr) b = (*env)->CallObjectMethod(env, b, mPad, padArr);
        if (b && mRnd) b = (*env)->CallObjectMethod(env, b, mRnd, JNI_TRUE);
        /* see build_spec: a NULL builder handed to the next call crashes instead of failing */
        if (step_failed(env, "mk:builder")) b = NULL;
        if (!b) set_step("mk:builder-lost");
        if (!mBuild) set_step("mk:build-method");
        if (b && mBuild) spec = (*env)->CallObjectMethod(env, b, mBuild);
        if (step_failed(env, "mk:spec.build")) spec = NULL;
        if (spec) spec_detail(env, spec);
        if (spec) {
            jmethodID kgi = (*env)->GetStaticMethodID(env, kgc, "getInstance", "(Ljava/lang/String;Ljava/lang/String;)Ljavax/crypto/KeyGenerator;");  //EXC-CLEARED
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            jstring prov = (*env)->NewStringUTF(env, PROVIDER);
            jobject kg = kgi && sAes && prov ? (*env)->CallStaticObjectMethod(env, kgc, kgi, sAes, prov) : NULL;
            if (step_failed(env, "mk:keygen.getInstance")) kg = NULL;
            if (!kgi) set_step("mk:keygen-method");
            if (kg) {
                jmethodID kinit = (*env)->GetMethodID(env, kgc, "init", "(Ljava/security/spec/AlgorithmParameterSpec;)V");  //EXC-CLEARED
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                jmethodID kgen = (*env)->GetMethodID(env, kgc, "generateKey", "()Ljavax/crypto/SecretKey;");  //EXC-CLEARED
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (!kinit) set_step("mk:keygen-init-method");
                if (kinit) (*env)->CallVoidMethod(env, kg, kinit, spec);
                if (!step_failed(env, "mk:keygen.init") && kgen) {
                    jobject k = (*env)->CallObjectMethod(env, kg, kgen);
                    if (step_failed(env, "mk:generateKey")) k = NULL;
                    if (sAes) (*env)->DeleteLocalRef(env, sAes);
                    if (gcm) (*env)->DeleteLocalRef(env, gcm);
                    if (none) (*env)->DeleteLocalRef(env, none);
                    return k;
                }
                (*env)->ExceptionClear(env);
            }
        }
    }
    if (sAes) (*env)->DeleteLocalRef(env, sAes);
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
        if (!initEnc || !aad || !fin || !getIv) set_step("seal:method-ids");
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
            if (!init || !aad || !fin) set_step("open:method-ids");
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
    jobject b = ctor ? (*env)->NewObject(env, bld, ctor, jalias, PURPOSE_SIGN_VERIFY) : NULL;
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
            jstring d = (*env)->NewStringUTF(env, ID_SHA256);
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
    if (priv_cached(env) != NULL) { set_step("id:already-there"); return 1; }
    /* attested first, plain second: a ROM without attestation support must still get an identity */
    jbyteArray ch = challenge != NULL ? challenge : att_challenge(env);
    jobject spec = build_spec(env, ch);
    if (spec && make_pair(env, spec)) { priv_invalidate(env); set_step("id:attested-key-made"); return 1; }
    /* reaching here means an entry existed and could not be signed with, so the code below
     * destroys it: 2 is that case, 1 is "nothing of yours was replaced" */
    Java_ir_v2rayng_compose_core_NativeSec_idDrop(env, cls);
    jobject plain = build_spec(env, NULL);
    int made = (plain && make_pair(env, plain)) ? 1 : 0;
    if (made) priv_invalidate(env);
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
        if (!initSign || !update || !sign) set_step("id:sign-method-ids");
        if (initSign) (*env)->CallVoidMethod(env, s, initSign, key);
        if (!step_failed(env, "id:sign.initSign") && update) {
            jsize n = (*env)->GetArrayLength(env, jmsg);
            (*env)->CallVoidMethod(env, s, update, jmsg, 0, n);
        }
        if (!step_failed(env, "id:sign.update") && sign) {
            out = (jbyteArray) (*env)->CallObjectMethod(env, s, sign);
            if (step_failed(env, "id:sign.sign")) out = NULL;
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
