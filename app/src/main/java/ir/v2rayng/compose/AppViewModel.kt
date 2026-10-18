package ir.v2rayng.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.AppInfo
import ir.v2rayng.compose.data.LogLine
import ir.v2rayng.compose.data.ServerProfile
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.packList
import ir.v2rayng.compose.data.packFloatList
import ir.v2rayng.compose.data.packLongList
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.data.parseLongList
import ir.v2rayng.compose.data.parseFloatList
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.ui.S
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

object AppCtx {
    @Volatile
    lateinit var app: android.content.Context
}

class AppViewModel(private val store: SettingsStore) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var connectJob: Job? = null
    private var trafficJob: Job? = null

    init {
        val saved = store.loadServers()
        if (saved != null) {
            _state.value = _state.value.copy(
                servers = saved,
                selectedId = if (saved.any { it.id == store.loadSelectedId() }) store.loadSelectedId() else saved.first().id,
            )
        }
        if (_state.value.servers.isNotEmpty()) {
            pingAll()
        }
        var lastRealState = ir.v2rayng.compose.core.RealState.IDLE
        viewModelScope.launch {
            ir.v2rayng.compose.core.XrayBridge.status.collect { st ->
                val real = _state.value.servers.firstOrNull { it.id == _state.value.selectedId }?.link != null ||
                    _state.value.selectedId.startsWith("rv-")
                if (real || st.state != ir.v2rayng.compose.core.RealState.IDLE) {
                    if (st.state != lastRealState) {
                        lastRealState = st.state
                        when (st.state) {
                            ir.v2rayng.compose.core.RealState.CONNECTING -> log("INFO", "Core connecting${if (st.message.isNotBlank()) " - ${st.message}" else ""}")
                            ir.v2rayng.compose.core.RealState.CONNECTED -> log("INFO", S.LOG_CONNECTED)
                            ir.v2rayng.compose.core.RealState.BROKEN -> log("ERROR", "${S.LOG_FAILED}${if (st.message.isNotBlank()) " - ${st.message}" else ""}")
                            ir.v2rayng.compose.core.RealState.IDLE -> log("INFO", S.LOG_DISCONNECTED)
                        }
                        if (st.state == ir.v2rayng.compose.core.RealState.CONNECTED && _settings.value.autoFlag) {
                            val sel = _state.value.servers.firstOrNull { it.id == _state.value.selectedId }
                            if (sel?.link != null) {
                                viewModelScope.launch {
                                    val code = serverCountry(sel.host.substringBefore(":"))
                                    if (code != null) {
                                        applyFlag(code)
                                        put { s ->
                                            s.copy(
                                                servers = s.servers.map { if (it.id == sel.id) it.copy(country = code) else it },
                                                ipCountry = code,
                                            )
                                        }
                                        log("INFO", S.LOG_IP_OK.format(code))
                                    } else {
                                        log("WARN", S.LOG_IP_FAIL)
                                    }
                                }
                            }
                        }
                    }
                    put { s ->
                        val targetId = s.selectedId
                        if (st.state == ir.v2rayng.compose.core.RealState.CONNECTED) {
                            appUp += st.upSpeed.toLong()
                            appDown += st.downSpeed.toLong()
                        }
                        s.copy(
                            vpnState = when (st.state) {
                                ir.v2rayng.compose.core.RealState.IDLE -> if (real) VpnState.IDLE else s.vpnState
                                ir.v2rayng.compose.core.RealState.CONNECTING -> VpnState.CONNECTING
                                ir.v2rayng.compose.core.RealState.CONNECTED -> VpnState.CONNECTED
                                ir.v2rayng.compose.core.RealState.BROKEN -> VpnState.BROKEN
                            },
                            traffic = if (st.state == ir.v2rayng.compose.core.RealState.CONNECTED) {
                                s.traffic.copy(
                                    up = appUp,
                                    down = appDown,
                                    upSpeed = st.upSpeed,
                                    downSpeed = st.downSpeed,
                                )
                            } else {
                                s.traffic.copy(upSpeed = 0.0, downSpeed = 0.0)
                            },
                            servers = s.servers.map { srv ->
                                if (srv.id == targetId) srv.copy(up = st.up, down = st.down) else srv
                            },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            ir.v2rayng.compose.core.XrayBridge.logs.collect { lines ->
                if (lines.isNotEmpty()) {
                    val known = _state.value.logs.size
                    val fresh = lines.takeLast(maxOf(0, lines.size)).filter { new ->
                        _state.value.logs.none { old -> old.time == new.time && old.msg == new.msg }
                    }
                    if (fresh.isNotEmpty()) {
                        put { s -> s.copy(logs = (s.logs + fresh).takeLast(240)) }
                    }
                }
            }
        }
    }

    private fun put(f: (AppState) -> AppState) = _state.update(f)

    private fun setSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        _settings.value = s
        store.save(s)
    }

    fun setDark(v: Boolean) = setSettings { it.copy(dark = v) }
    fun setAccent(i: Int) = setSettings { it.copy(accent = i) }
    fun setCustomAccent(v: Long) = setSettings { it.copy(customAccent = v) }
    fun setStickerStyle(i: Int) = setSettings { it.copy(stickerStyle = i) }
    fun setWallpaperStyle(i: Int) = setSettings { it.copy(wallpaperStyle = i) }
    fun setWallpaper(i: Int) = setSettings { it.copy(wallpaperIndex = i) }
    fun setWallpaperColor(v: Long) = setSettings { it.copy(wallpaperColor = v) }
    fun setWallpaperSize(v: Int) = setSettings { it.copy(wallpaperSize = v) }
    fun setMainColor(v: Long) = setSettings { it.copy(mainColor = v) }
    fun setMainSize(v: Int) = setSettings { it.copy(mainSize = v) }
    fun setMainIndex(v: Int) = setSettings { it.copy(mainIndex = v) }
    fun setFollowFinger(v: Boolean) = setSettings { it.copy(followFinger = v) }
    fun setAnimate(v: Boolean) = setSettings { it.copy(animate = v) }
    fun setStateMotion(v: Boolean) = setSettings { it.copy(stateMotion = v) }
    fun setShowNotification(v: Boolean) = setSettings { it.copy(showNotification = v) }
    fun setNotifSticker(v: Boolean) = setSettings { it.copy(notifSticker = v) }
    fun setNotifColor(v: Long) = setSettings { it.copy(notifColor = v) }

    fun resetAllServerTraffic() {
        put { s -> s.copy(servers = s.servers.map { it.copy(up = 0, down = 0) }) }
        ir.v2rayng.compose.core.XrayBridge.requestCounterReset()
        log("INFO", "All server traffic reset")
    }
    fun setSpeed(v: Float) = setSettings { it.copy(speed = v) }
    fun setBgSpeed(v: Float) = setSettings { it.copy(bgSpeed = v) }
    fun setNavStickers(v: Boolean) = setSettings { it.copy(navStickers = v) }
    fun setAutoFlag(v: Boolean) = setSettings { it.copy(autoFlag = v) }

    fun setNavOrder(order: List<Int>) = setSettings { it.copy(navOrder = packList(order)) }

    fun setNavIcon(slot: Int, idx: Int) = setSettings { s ->
        val cur = parseList(s.navIcons, (0..4).toList()).toMutableList()
        cur[slot.coerceIn(0, cur.size - 1)] = idx
        s.copy(navIcons = packList(cur))
    }

    fun setUpIcon(i: Int) = setSettings { it.copy(upIcon = i) }
    fun setDownIcon(i: Int) = setSettings { it.copy(downIcon = i) }
    fun setUpColor(v: Long) = setSettings { it.copy(upColor = v) }
    fun setDownColor(v: Long) = setSettings { it.copy(downColor = v) }

    fun setNavColor(slot: Int, v: Long) = setSettings { s ->
        val cur = parseLongList(s.navColors, listOf(-1L, -1L, -1L, -1L, -1L)).toMutableList()
        cur[slot.coerceIn(0, cur.size - 1)] = v
        s.copy(navColors = packLongList(cur))
    }

    fun setRouteColor(slot: Int, v: Long) = setSettings { s ->
        val cur = parseLongList(s.routeColors, listOf(-1L, -1L, -1L)).toMutableList()
        cur[slot.coerceIn(0, cur.size - 1)] = v
        s.copy(routeColors = packLongList(cur))
    }

    fun setUpSpeed(v: Float) = setSettings { it.copy(upSpeed = v) }
    fun setDownSpeed(v: Float) = setSettings { it.copy(downSpeed = v) }

    fun setNavSpeed(slot: Int, v: Float) = setSettings { s ->
        val cur = parseFloatList(s.navSpeeds, listOf(1f, 1f, 1f, 1f, 1f)).toMutableList()
        while (cur.size < 5) cur.add(1f)
        cur[slot.coerceIn(0, 4)] = v
        s.copy(navSpeeds = packFloatList(cur))
    }

    fun setRouteSpeed(slot: Int, v: Float) = setSettings { s ->
        val cur = parseFloatList(s.routeSpeeds, listOf(1f, 1f, 1f)).toMutableList()
        while (cur.size < 3) cur.add(1f)
        cur[slot.coerceIn(0, 2)] = v
        s.copy(routeSpeeds = packFloatList(cur))
    }

    fun srvStickerIndex(id: String): Int =
        _state.value.servers.firstOrNull { it.id == id }?.stickerIndex ?: 0

    fun setServerIcon(id: String, idx: Int) {
        put { s -> s.copy(servers = s.servers.map { if (it.id == id) it.copy(stickerIndex = idx) else it }) }
    }

    fun setRouteIcon(slot: Int, idx: Int) = setSettings { s ->
        val cur = parseList(s.routeIcons, listOf(0, 1, 2)).toMutableList()
        cur[slot.coerceIn(0, 2)] = idx
        s.copy(routeIcons = packList(cur))
    }

    fun resetSettings() = setSettings { Settings() }

    fun setAntiFilter(v: Boolean) {
        setSettings { it.copy(antiFilter = v) }
        log("INFO", if (v) S.LOG_ANTI_ON else S.LOG_ANTI_OFF)
    }

    fun loadApps(context: android.content.Context) {
        if (_state.value.apps.isNotEmpty()) return
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolve = pm.queryIntentActivities(intent, 0)
        val apps = resolve.mapNotNull {
            val pkg = it.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
            AppInfo(
                label = it.loadLabel(pm).toString(),
                pkg = pkg,
            )
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        put { s -> s.copy(apps = apps) }
    }

    fun toggleAntiFilterApp(pkg: String) = setSettings { s ->
        val cur = s.antiFilterApps.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (cur.contains(pkg)) cur.remove(pkg) else cur.add(pkg)
        s.copy(antiFilterApps = cur.joinToString(","))
    }

    fun setHomeOrder(order: List<Int>) = setSettings { it.copy(homeOrder = packList(order)) }

    private fun log(level: String, msg: String) {
        put { s -> s.copy(logs = (s.logs + LogLine(TimeKit.now(), level, msg)).takeLast(240)) }
    }

    fun toggleVpn() {
        when (_state.value.vpnState) {
            VpnState.IDLE, VpnState.BROKEN -> connect()
            else -> disconnect()
        }
    }

    private fun connect() {
        val target = _state.value.selected
        if (target.link != null) {
            val parsed = ir.v2rayng.compose.core.parseProxyLink(target.link)
            if (parsed == null) {
                put { s -> s.copy(vpnState = VpnState.BROKEN) }
                log("ERROR", S.LOG_FAILED)
                return
            }
            log("INFO", S.LOG_CONNECTING.format(target.name))
            ir.v2rayng.compose.core.XrayVpnService.start(appCtx(), parsed, target.link)
            return
        }
        put { s -> s.copy(vpnState = VpnState.CONNECTING) }
        log("INFO", S.LOG_CONNECTING.format(target.name))
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            delay(800L + Random.nextLong(900))
            if (_state.value.vpnState == VpnState.CONNECTING) {
                if (Random.nextInt(7) == 0) {
                    put { s -> s.copy(vpnState = VpnState.BROKEN) }
                    log("ERROR", S.LOG_FAILED)
                } else {
                    put { s -> s.copy(vpnState = VpnState.CONNECTED) }
                    log("INFO", S.LOG_CONNECTED)
                    startTraffic()
                    if (_settings.value.autoFlag && target.country.isNotBlank()) {
                        applyFlag(target.country)
                    }
                }
            }
        }
    }

    private fun appCtx(): android.content.Context = AppCtx.app

    fun disconnectReal() {
        ir.v2rayng.compose.core.XrayVpnService.stop(appCtx())
    }

    private fun startTraffic() {
        trafficJob?.cancel()
        trafficJob = viewModelScope.launch {
            while (true) {
                delay(700)
                val up = Random.nextInt(2, 81) * 1024.0
                val down = up * (1.2 + Random.nextDouble(0.0, 4.8))
                put { s ->
                    s.copy(
                        traffic = s.traffic.copy(
                            up = s.traffic.up + (up * 0.7).toLong(),
                            down = s.traffic.down + (down * 0.7).toLong(),
                            upSpeed = up,
                            downSpeed = down,
                        )
                    )
                }
            }
        }
    }

    private fun disconnect() {
        val target = _state.value.selected
        connectJob?.cancel()
        trafficJob?.cancel()
        if (target.link != null) {
            ir.v2rayng.compose.core.XrayVpnService.stop(appCtx())
        }
        put { s ->
            s.copy(
                vpnState = VpnState.IDLE,
                traffic = s.traffic.copy(upSpeed = 0.0, downSpeed = 0.0),
            )
        }
        log("INFO", S.LOG_DISCONNECTED)
        if (_settings.value.autoFlag) {
            fetchIpFlag()
        }
    }

    fun fetchIpFlag() {
        viewModelScope.launch {
            val code = withContext(Dispatchers.IO) {
                try {
                    val conn = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
                    conn.connectTimeout = 4500
                    conn.readTimeout = 4500
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    Regex("\"country_code\"\\s*:\\s*\"([A-Z]{2})\"").find(text)?.groupValues?.get(1)
                } catch (e: Exception) {
                    null
                }
            }
            if (code != null) {
                val applied = applyFlag(code)
                put { s -> s.copy(ipCountry = code) }
                log("INFO", S.LOG_IP_OK.format(code))
            } else {
                log("WARN", S.LOG_IP_FAIL)
            }
        }
    }

    private fun applyFlag(code: String): Boolean {
        val idx = CountryFlags.byCode(code)
        if (idx < 0) return false
        setSettings { it.copy(wallpaperStyle = 2, wallpaperIndex = idx) }
        return true
    }

    fun pingAll() {
        viewModelScope.launch {
            log("INFO", S.LOG_PING_START)
            val servers = _state.value.servers
            for (srv in servers) {
            val ms: Int? = if (srv.link != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val l = ir.v2rayng.compose.core.parseProxyLink(srv.link!!)
                        if (l == null) {
                            null
                        } else {
                            val config = ir.v2rayng.compose.core.buildXrayConfig(l)
                            val d = libv2ray.Libv2ray.measureOutboundDelay(config, "https://www.google.com/generate_204")
                            if (d >= 0) d.toInt() else null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            } else {
                    delay(350)
                    40 + Random.nextInt(400)
                }
                put { s ->
                    s.copy(servers = s.servers.map { if (it.id == srv.id) it.copy(ping = ms) else it })
                }
            }
            log("INFO", S.LOG_PING_DONE)
        }
    }

    fun needsVpnConsent(): android.content.Intent? = android.net.VpnService.prepare(AppCtx.app)

    private val countryCache = mutableMapOf<String, String>()

    private suspend fun serverCountry(host: String): String? {
        return withContext(Dispatchers.IO) {
            countryCache[host]?.let { return@withContext it }
            try {
                val ip = java.net.InetAddress.getByName(host).hostAddress ?: return@withContext null
                val urls = listOf("https://ipwho.is/$ip", "https://ipapi.co/$ip/json/", "https://ipinfo.io/$ip/json")
                for (u in urls) {
                    try {
                        val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 3500
                        conn.readTimeout = 3500
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val code = Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"")
                            .find(text)?.groupValues?.get(1)?.uppercase()
                        if (code != null) {
                            countryCache[host] = code
                            return@withContext code
                        }
                    } catch (_: Exception) {
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun addServerFromLink(raw: String) {
        val link = ir.v2rayng.compose.core.parseProxyLink(raw)
        if (link == null) {
            log("ERROR", "Link not recognized (vless / vmess / trojan / ss)")
            return
        }
        val shownName = link.name.ifBlank { link.address }
        val newId = "rv-${_state.value.servers.size}-${Random.nextInt(999)}"
        put { s ->
            s.copy(
                servers = s.servers + ServerProfile(
                    id = newId,
                    name = shownName,
                    host = "${link.address}:${link.port}",
                    stickerIndex = s.servers.size,
                    link = raw.trim(),
                ),
            )
        }
        log("INFO", S.LOG_ADDED.format(shownName))
        store.saveServers(_state.value.servers, _state.value.selectedId)
        viewModelScope.launch {
            val code = serverCountry(link.address)
            if (code != null) {
                put { s ->
                    s.copy(
                        servers = s.servers.map { if (it.id == newId) it.copy(country = code) else it },
                    )
                }
                store.saveServers(_state.value.servers, _state.value.selectedId)
            }
        }
    }

    fun resetTraffic() {
        put { s ->
            s.copy(
                traffic = s.traffic.copy(up = 0, down = 0, upSpeed = 0.0, downSpeed = 0.0),
                servers = s.servers.map { it.copy(up = 0, down = 0) },
            )
        }
        ir.v2rayng.compose.core.XrayBridge.requestCounterReset()
        log("INFO", "Traffic counters reset")
    }

    private var appUp = 0L
    private var appDown = 0L

    fun resetTotalTraffic() {
        appUp = 0
        appDown = 0
        put { s ->
            s.copy(traffic = s.traffic.copy(up = 0, down = 0, upSpeed = 0.0, downSpeed = 0.0))
        }
        log("INFO", "Total traffic reset")
    }

    fun resetServerTraffic(id: String) {
        put { s ->
            s.copy(servers = s.servers.map { if (it.id == id) it.copy(up = 0, down = 0) else it })
        }
        if (_state.value.selectedId == id) {
            ir.v2rayng.compose.core.XrayBridge.requestCounterReset()
        }
        log("INFO", "Server traffic reset")
    }

    fun select(id: String) {
        val name = _state.value.servers.firstOrNull { it.id == id }?.name ?: return
        put { s -> s.copy(selectedId = id) }
        store.saveSelectedId(id)
        log("INFO", S.LOG_SELECTED.format(name))
    }

    fun addServer(name: String, host: String) {
        val idx = _state.value.servers.size
        put { s ->
            s.copy(
                servers = s.servers + ServerProfile(
                    id = "srv-${idx}-${Random.nextInt(999)}",
                    name = name,
                    host = host,
                    stickerIndex = idx,
                )
            )
        }
        log("INFO", S.LOG_ADDED.format(name))
    }

    fun setRoute(key: String, value: Boolean) {
        put { s -> s.copy(routes = s.routes + (key to value)) }
        log("INFO", if (value) S.LOG_ROUTE_ON.format(key) else S.LOG_ROUTE_OFF.format(key))
    }

    fun clearLogs() {
        put { s -> s.copy(logs = emptyList()) }
        log("INFO", S.LOG_CLEARED)
    }
}
