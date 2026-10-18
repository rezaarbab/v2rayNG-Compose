package ir.v2rayng.compose.core

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class LinkKind { VLESS, VMESS, TROJAN, SS }

data class ProxyLink(
    val kind: LinkKind,
    val name: String,
    val address: String,
    val port: Int,
    val uuidOrPassword: String,
    val security: String = "none",
    val type: String = "tcp",
    val flow: String = "",
    val sni: String = "",
    val fp: String = "",
    val pbk: String = "",
    val sid: String = "",
    val spx: String = "",
    val method: String = "",
    val alterId: Int = 0,
    val path: String = "",
    val hostHeader: String = "",
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

    private val resetReq = java.util.concurrent.atomic.AtomicBoolean(false)

    fun requestCounterReset() {
        resetReq.set(true)
    }

    fun takeCounterReset(): Boolean = resetReq.compareAndSet(true, false)

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

private fun b64Any(s: String): String? {
    val t = s.trim().replace('-', '+').replace('_', '/').trimEnd('=')
    val padded = t + "=".repeat((4 - t.length % 4) % 4)
    return try {
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

private fun urlDecode(s: String): String = try {
    java.net.URLDecoder.decode(s, "UTF-8")
} catch (_: Exception) {
    s
}

private fun paramsOf(main: String): Map<String, String> {
    val q = main.indexOf('?')
    if (q < 0) return emptyMap()
    return main.substring(q + 1).split('&')
        .mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq) to urlDecode(p.substring(eq + 1))
        }
        .toMap()
}

private fun hostPortOf(main: String, afterIndex: Int): Pair<String, Int>? {
    val end = main.indexOf('?', afterIndex).let { if (it < 0) main.length else it }
    val tail = main.substring(afterIndex, end).takeLastWhile { it != '/' }
    val colon = tail.lastIndexOf(':')
    if (colon <= 0) return null
    val host = tail.substring(0, colon).trim('[', ']', ' ')
    val port = tail.substring(colon + 1).trim().toIntOrNull() ?: return null
    if (host.isBlank()) return null
    return host to port
}

fun parseProxyLink(raw: String): ProxyLink? {
    val uri = raw.trim()
    return try {
        when {
            uri.startsWith("vless://", true) -> parseVlessLike(uri, LinkKind.VLESS)
            uri.startsWith("trojan://", true) -> parseVlessLike(uri, LinkKind.TROJAN)
            uri.startsWith("vmess://", true) -> parseVmess(uri)
            uri.startsWith("ss://", true) -> parseSs(uri)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseVlessLike(uri: String, kind: LinkKind): ProxyLink? {
    val rest = uri.substringAfter("://")
    val hashIndex = rest.indexOf('#')
    val main = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest
    val fragment = if (hashIndex >= 0) rest.substring(hashIndex + 1) else ""
    val name = urlDecode(fragment).trim()
    val queryIndex = main.indexOf('?')
    val at = main.indexOf('@')
    if (at <= 0) return null
    val cred = main.substring(0, at).trim()
    val hp = hostPortOf(main, at + 1) ?: return null
    val params = paramsOf(main)
    val security = params["security"]?.takeIf { it.isNotBlank() } ?: if (kind == LinkKind.TROJAN) "tls" else "none"
    return ProxyLink(
        kind = kind,
        name = name,
        address = hp.first,
        port = hp.second,
        uuidOrPassword = cred,
        security = security,
        type = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp",
        flow = params["flow"] ?: "",
        sni = params["sni"] ?: "",
        fp = params["fp"] ?: "",
        pbk = params["pbk"] ?: "",
        sid = params["sid"] ?: "",
        spx = params["spx"] ?: "",
        path = params["path"] ?: "",
        hostHeader = params["host"] ?: "",
    )
}

private fun parseVmess(uri: String): ProxyLink? {
    val payload = uri.substringAfter("vmess://", "").trim()
    val json = JSONObject(b64Any(payload) ?: return null)
    val port = json.optInt("port", -1)
    val add = json.optString("add", "").trim()
    val id = json.optString("id", "").trim()
    if (port <= 0 || add.isBlank() || id.isBlank()) return null
    return ProxyLink(
        kind = LinkKind.VMESS,
        name = json.optString("ps", "").trim(),
        address = add,
        port = port,
        uuidOrPassword = id,
        alterId = json.optInt("aid", 0),
        security = json.optString("tls", "none").takeIf { it.isNotBlank() } ?: "none",
        type = json.optString("net", "tcp").takeIf { it.isNotBlank() } ?: "tcp",
        sni = json.optString("sni", ""),
        method = json.optString("scy", "auto"),
    )
}

private fun parseSs(uri: String): ProxyLink? {
    val rest = uri.substringAfter("ss://", "")
    val hashIndex = rest.indexOf('#')
    val name = if (hashIndex >= 0) urlDecode(rest.substring(hashIndex + 1)).trim() else ""
    val main = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest
    val queryIndex = main.indexOf('?')
    val body = if (queryIndex >= 0) main.substring(0, queryIndex) else main
    val at = body.lastIndexOf('@')
    var method = ""
    var password = ""
    var hostPart = body
    if (at >= 0) {
        val cred = body.substring(0, at)
        val decoded = b64Any(cred) ?: urlDecode(cred)
        val c = decoded.indexOf(':')
        if (c > 0) {
            method = decoded.substring(0, c)
            password = decoded.substring(c + 1)
        }
        hostPart = body.substring(at + 1)
    } else {
        val decoded = b64Any(body) ?: return null
        val c = decoded.lastIndexOf(':')
        val atd = decoded.lastIndexOf('@')
        if (atd <= 0 || c <= atd) return null
        val cred = decoded.substring(0, atd)
        val cc = cred.indexOf(':')
        if (cc <= 0) return null
        method = cred.substring(0, cc)
        password = cred.substring(cc + 1)
        hostPart = decoded.substring(atd + 1)
    }
    val colon = hostPart.lastIndexOf(':')
    if (colon <= 0) return null
    val host = hostPart.substring(0, colon).trim('[', ']', ' ')
    val port = hostPart.substring(colon + 1).trim().toIntOrNull() ?: return null
    if (method.isBlank() || password.isBlank()) return null
    return ProxyLink(
        kind = LinkKind.SS,
        name = name,
        address = host,
        port = port,
        uuidOrPassword = password,
        method = method,
    )
}

fun buildXrayConfig(link: ProxyLink): String {
    val outbound = when (link.kind) {
        LinkKind.VLESS -> {
            val user = JSONObject()
                .put("id", link.uuidOrPassword)
                .put("encryption", "none")
                .put("level", 0)
            if (link.flow.isNotBlank()) user.put("flow", link.flow)
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", link.address)
                                .put("port", link.port)
                                .put("users", JSONArray().put(user)),
                        ),
                    ),
                )
                .put("streamSettings", streamOf(link))
        }
        LinkKind.VMESS -> JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", link.address)
                            .put("port", link.port)
                            .put(
                                "users",
                                JSONArray().put(
                                    JSONObject()
                                        .put("id", link.uuidOrPassword)
                                        .put("alterId", link.alterId)
                                        .put("security", link.method.ifBlank { "auto" })
                                        .put("level", 0),
                                ),
                            ),
                    ),
                ),
            )
            .put("streamSettings", streamOf(link))
        LinkKind.TROJAN -> JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", link.address)
                            .put("port", link.port)
                            .put("password", link.uuidOrPassword)
                            .put("level", 0),
                    ),
                ),
            )
            .put("streamSettings", streamOf(link))
        LinkKind.SS -> JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", link.address)
                            .put("port", link.port)
                            .put("method", link.method)
                            .put("password", link.uuidOrPassword)
                            .put("level", 0),
                    ),
                ),
            )
    }

    val tunSettings = JSONObject()
        .put("name", "utun42")
        .put("desc", "Blob")
        .put("mtu", 1500)

    val tunIn = JSONObject()
        .put("tag", "tun-in")
        .put("protocol", "tun")
        .put("settings", tunSettings)

    val config = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("inbounds", JSONArray().put(tunIn))
        .put(
            "outbounds",
            JSONArray()
                .put(outbound)
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
        .put("stats", JSONObject())
        .put(
            "policy",
            JSONObject()
                .put(
                    "levels",
                    JSONObject().put(
                        "0",
                        JSONObject()
                            .put("statsUserUplink", true)
                            .put("statsUserDownlink", true),
                    ),
                )
                .put(
                    "system",
                    JSONObject()
                        .put("statsOutboundUplink", true)
                        .put("statsOutboundDownlink", true),
                ),
        )
    return config.toString()
}

private fun streamOf(link: ProxyLink): JSONObject {
    val stream = JSONObject()
        .put("network", link.type)
        .put("security", link.security)
    when (link.type) {
        "ws" -> {
            val ws = JSONObject().put("path", link.path.ifBlank { "/" })
            if (link.hostHeader.isNotBlank()) {
                ws.put("headers", JSONObject().put("Host", link.hostHeader))
            }
            stream.put("wsSettings", ws)
        }
        "grpc" -> {
            val g = JSONObject()
            if (link.path.isNotBlank()) g.put("serviceName", link.path)
            if (link.hostHeader.isNotBlank()) g.put("authority", link.hostHeader)
            stream.put("grpcSettings", g)
        }
        "httpupgrade" -> {
            val h = JSONObject().put("path", link.path.ifBlank { "/" })
            if (link.hostHeader.isNotBlank()) h.put("host", link.hostHeader)
            stream.put("httpupgradeSettings", h)
        }
    }
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
    return stream
}

data class TrafficCounter(val up: Long, val down: Long)

fun parseTrafficDeltas(raw: String): TrafficCounter {
    var up = 0L
    var down = 0L
    if (raw.isNotBlank()) {
        for (chunk in raw.split(';')) {
            val parts = chunk.split(',')
            if (parts.size < 3) continue
            val dir = parts[1].trim()
            val value = parts[2].trim().toLongOrNull() ?: continue
            if (parts[0].trim() != "proxy") continue
            when (dir) {
                "uplink" -> up += value
                "downlink" -> down += value
            }
        }
    }
    return TrafficCounter(up, down)
}
