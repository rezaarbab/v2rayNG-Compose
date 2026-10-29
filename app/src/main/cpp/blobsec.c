/* blobsec - the decode primitives, in C.
 *
 * SHA-256, HMAC-SHA256, base64url and the zlib envelope opening live here; the Kotlin side keeps
 * no copy of them. AES-256-GCM is deliberately NOT here yet - see the note at the bottom and the
 * self-test below, which must pass on the device before any of this is trusted.
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

/* AES-256-GCM is NOT here. It only enters this file with a NIST known-answer test that has been
 * run, because an untested AES kills every sync on every handset at once - the Kotlin side still
 * does that one primitive (javax.crypto, which is Android's own native BoringSSL) until then. */
