package ir.v2rayng.compose.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class VlessLink(
    val uuid: String,
    val address: String,
    val port: Int,
    val security: String = "none",
    val type: String = "tcp",
    val flow: String = "",
    val sni: String = "",
    val fp: String = "",
    val pbk: String = "",
    val sid: String = "",
    val spx: String = "",
    val name: String = "",
)

enum class RealState { IDLE, CONNECTING, CONNECTED, BROKEN }

data class RealStatus(
    val state: RealState = RealState.IDLE,
    val up: Long = 0L,
    val down: Long = 0L,
    val upSpeed: Double = 0.0,
    val downSpeed: Double = 0.0,
    val message: String = "",
)

object XrayBridge {
    private val _status = MutableStateFlow(RealStatus())
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<ir.v2rayng.compose.data.LogLine>>(emptyList())
    val logs = _logs.asStateFlow()

    fun addLog(level: String, msg: String) {
        if (msg.isBlank()) return
        val line = ir.v2rayng.compose.data.LogLine(ir.v2rayng.compose.data.TimeKit.now(), level, msg.take(400))
        _logs.value = (_logs.value + line).takeLast(60)
    }

    fun setState(s: RealState, msg: String = "") {
        _status.value = _status.value.copy(state = s, message = msg)
    }

    fun setTraffic(up: Long, down: Long, upSpeed: Double, downSpeed: Double) {
        _status.value = _status.value.copy(up = up, down = down, upSpeed = upSpeed, downSpeed = downSpeed)
    }

    fun reset() {
        _status.value = RealStatus()
    }
}

private fun hostPort(uri: String, start: Int): Pair<String, Int>? {
    val end = uri.indexOf('?', start).let { if (it < 0) uri.length else it }
    val tail = uri.substring(start, end).takeLastWhile { it != '/' }
    val at = tail.lastIndexOf('@') + 1
    val hostPart = tail.substring(at)
    val colon = hostPart.lastIndexOf(':')
    if (colon <= 0) return null
    val host = hostPart.substring(0, colon).trimEnd('[', ']').trimStart('[', ' ')
    val port = hostPart.substring(colon + 1).trim().toIntOrNull() ?: return null
    if (host.isBlank()) return null
    return host to port
}

fun parseVlessLink(raw: String): VlessLink? {
    val uri = raw.trim()
    if (!uri.startsWith("vless://", ignoreCase = true)) return null
    try {
        val rest = uri.substring("vless://".length)
        val hashIndex = rest.indexOf('#')
        val main = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest
        val fragment = if (hashIndex >= 0) rest.substring(hashIndex + 1) else ""
        val name = java.net.URLDecoder.decode(fragment, "UTF-8").trim()
        val queryIndex = main.indexOf('?')
        val cidEnd = if (queryIndex >= 0) queryIndex else main.length
        val hp = hostPort(main, 0) ?: return null
        val uuid = main.substring(0, main.indexOf('@').takeIf { it in 1 until cidEnd } ?: cidEnd).trim()
        if (uuid.isBlank()) return null
        val params = if (queryIndex >= 0) {
            main.substring(queryIndex + 1).split('&')
                .mapNotNull { p ->
                    val eq = p.indexOf('=')
                    if (eq <= 0) null else {
                        val k = p.substring(0, eq)
                        val v = java.net.URLDecoder.decode(p.substring(eq + 1), "UTF-8")
                        k to v
                    }
                }
                .toMap()
        } else {
            emptyMap()
        }
        val portHdr = hp.second
        val address = hp.first
        val security = params["security"]?.takeIf { it.isNotBlank() } ?: "none"
        val type = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp"
        val flow = params["flow"] ?: ""
        val sni = params["sni"] ?: params["host"] ?: ""
        return VlessLink(
            uuid = uuid,
            address = address,
            port = portHdr,
            security = security,
            type = type,
            flow = flow,
            sni = sni,
            fp = params["fp"] ?: "",
            pbk = params["pbk"] ?: "",
            sid = params["sid"] ?: "",
            spx = params["spx"] ?: "",
            name = name,
        )
    } catch (_: Exception) {
        return null
    }
}

fun buildXrayConfig(link: VlessLink): String {
    val user = JSONObject()
        .put("id", link.uuid)
        .put("encryption", "none")
        .put("level", 0)
    if (link.flow.isNotBlank()) user.put("flow", link.flow)

    val vnext = JSONObject()
        .put("address", link.address)
        .put("port", link.port)
        .put("users", JSONArray().put(user))

    val stream = JSONObject()
        .put("network", link.type)
        .put("security", link.security)
    when (link.security) {
        "tls" -> {
            val tls = JSONObject()
            if (link.sni.isNotBlank()) tls.put("serverName", link.sni)
            tls.put("allowInsecure", false)
            stream.put("tlsSettings", tls)
        }
        "reality" -> {
            val rs = JSONObject().put("show", false)
            if (link.sni.isNotBlank()) rs.put("serverName", link.sni)
            if (link.fp.isNotBlank()) rs.put("fingerprint", link.fp)
            if (link.pbk.isNotBlank()) rs.put("publicKey", link.pbk)
            if (link.sid.isNotBlank()) rs.put("shortId", link.sid)
            if (link.spx.isNotBlank()) rs.put("spiderX", link.spx)
            stream.put("realitySettings", rs)
        }
    }

    val tunIn = JSONObject()
        .put("tag", "tun-in")
        .put("protocol", "tun")
        .put("address", "198.18.0.1/30")
        .put("mtu", 1500)
        .put("stack", "gvisor")

    val config = JSONObject()
        .put(
            "log",
            JSONObject().put("loglevel", "warning"),
        )
        .put("inbounds", JSONArray().put(tunIn))
        .put(
            "outbounds",
            JSONArray()
                .put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "vless")
                        .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
                        .put("streamSettings", stream),
                )
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom")),
        )
        .put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put("outboundTag", "proxy")
                            .put("network", "tcp,udp"),
                    ),
                ),
        )
    return config.toString()
}

data class TrafficCounter(val up: Long, val down: Long)

fun parseTrafficStats(raw: String, prevUp: Long, prevDown: Long): TrafficCounter {
    var up = prevUp
    var down = prevDown
    if (raw.isNotBlank()) {
        var newUp = 0L
        var newDown = 0L
        for (chunk in raw.split(';')) {
            val parts = chunk.split(',')
            if (parts.size < 3) continue
            val value = parts[2].trim().toLongOrNull() ?: continue
            when (parts[0].trim()) {
                "proxy" -> {
                    if (parts[1].trim() == "up") newUp = value
                    if (parts[1].trim() == "down") newDown = value
                }
                "tun-in" -> {
                    if (parts[1].trim() == "up") newUp = value
                    if (parts[1].trim() == "down") newDown = value
                }
            }
        }
        if (newUp > 0 || newDown > 0 || prevUp > 0 || prevDown > 0) {
            if (newUp >= prevUp) up = newUp
            if (newDown >= prevDown) down = newDown
        }
    }
    return TrafficCounter(up, down)
}
