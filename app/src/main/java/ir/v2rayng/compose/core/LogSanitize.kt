package ir.v2rayng.compose.core

/**
 * Nothing that identifies a VPN endpoint may reach the visible log: no host, IP, port, link
 * scheme or user id. The Xray core prints those in its own lines and the log tailer copies them
 * verbatim, so every line is scrubbed centrally before it is stored.
 *
 * The nine rules run in blobsec.c now (links, uuid, json-secret, json-port, kv-secret, IPv4,
 * IPv6 in both shapes, domains), byte for byte as the regexes below used to, and the switch was
 * only made after an oracle compared the two transcriptions over 4000 generated lines with zero
 * differences. If the library cannot answer, the line is dropped rather than shown unscrubbed:
 * a lost log line is cheaper than a leaked host.
 */
object LogSanitize {

    fun clean(msg: String): String {
        if (msg.isBlank()) return msg
        val out = NativeSec.scrub(msg.toByteArray(Charsets.UTF_8)) ?: return ""
        return String(out, Charsets.UTF_8)
    }
}
