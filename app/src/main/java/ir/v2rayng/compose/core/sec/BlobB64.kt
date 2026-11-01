package ir.v2rayng.compose.core.sec

import ir.v2rayng.compose.core.NativeSec

/**
 * base64 and base64url, both directions, in C - one alphabet, one padding rule.
 *
 * This file used to hold the stego slot engine, the envelope and the GCM open. They moved into
 * blobsec.c, and from stage 3 on nothing here turns a carrier into plaintext: the sync path calls
 * the row loader, which opens, parses and cuts the server-free copy inside the library. What the
 * app still needs on its side is the encoding the wire speaks.
 */
object BlobB64 {
    fun decode(s: String): ByteArray? = NativeSec.b64decode(s)?.takeIf { it.isNotEmpty() || s.isEmpty() }

    fun encode(b: ByteArray): String = NativeSec.b64url(b) ?: ""
}
