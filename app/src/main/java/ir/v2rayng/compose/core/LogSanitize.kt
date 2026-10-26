package ir.v2rayng.compose.core

/**
 * Nothing that identifies a VPN endpoint may reach the visible log: no host, IP, port, link
 * scheme or user id. The Xray core prints those in its own lines and the log tailer copies them
 * verbatim, so every line is scrubbed centrally before it is stored.
 */
object LogSanitize {

    private val LINK = Regex("""(?i)\b(vless|vmess|trojan|ss|ssr|hysteria2?|tuic|vaxins|socks)://\S+""")
    private val UUID = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val IPV4 = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d{1,5})?\b""")
    // two conservative shapes, so plain timestamps (17:36:01.931827) and short colon runs survive
    private val IPV6 = Regex("""\b[0-9a-fA-F]{1,4}:[0-9a-fA-F:]*::[0-9a-fA-F:.]*\b""")
    private val IPV6_LONG = Regex("""\b(?:[0-9a-fA-F]{1,4}:){4,}[0-9a-fA-F]{1,4}\b""")
    private val DOMAIN = Regex("""(?i)\b[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)+\.(com|net|org|io|xyz|ru|ir|de|uk|us|ca|au|me|app|dev|cloud|site|online|live|top|info|biz|cn|tk|ga|cf|gq|ml|shop|api|proxy|cdn|work|pw|cc)(:\d{1,5})?\b""")
    private val JSON_SECRET = Regex("""(?i)("(?:address|host|server_name|sni|peer|ip|uuid|password|pass|pbk|sid|user|user_ids?|email|remark)"\s*:\s*)"[^"]*"""")
    private val JSON_PORT = Regex("""(?i)("(?:port)"\s*:\s*)\d+""")
    private val KV_SECRET = Regex("""(?i)\b(address|host|server_name|sni|peer|uuid|password|pass|pbk|sid|ip)=([^\s,;]+)""")

    /** Deliberately no generic "word.word" or "word:digits" rules: they would eat the Java stack
     *  traces (Handler.java:1029) that the crash recorder exists to show. */
    fun clean(msg: String): String {
        if (msg.isBlank()) return msg
        var s = LINK.replace(msg, " [link hidden] ")
        s = UUID.replace(s, "[id]")
        s = JSON_SECRET.replace(s) { m -> m.groupValues[1] + "\"[hidden]\"" }
        s = JSON_PORT.replace(s) { m -> m.groupValues[1] + "0" }
        s = KV_SECRET.replace(s) { m -> m.groupValues[1] + "=[hidden]" }
        s = IPV4.replace(s, "[ip]")
        s = IPV6.replace(s, "[ip]")
        s = IPV6_LONG.replace(s, "[ip]")
        s = DOMAIN.replace(s, "[host]")
        return s
    }
}
