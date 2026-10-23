package ir.v2rayng.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.AppInfo
import ir.v2rayng.compose.data.LogLine
import ir.v2rayng.compose.data.ServerProfile
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.data.StickerFx
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.packList
import ir.v2rayng.compose.data.packFloatList
import org.json.JSONObject
import ir.v2rayng.compose.data.packLongList
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.data.parseLongList
import ir.v2rayng.compose.data.parseFloatList
import ir.v2rayng.compose.data.packFx
import ir.v2rayng.compose.data.packStickerFx
import ir.v2rayng.compose.data.parseFxPack
import ir.v2rayng.compose.core.Remote
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

    /** admin-driven behavior flags - MUST be before init (Kotlin field order) */
    @Volatile
    private var lastSrvToast: Long = 0L
    @Volatile
    private var adminFlags: Map<String, String> = HashMap()
    @Volatile
    private var mergeStamp: Long = 0L
    @Volatile
    private var lastNetToast: Long = 0L
    @Volatile
    private var popupDismissed: Long = 0L
    @Volatile
    private var updateDismiss: Long = 0L
    @Volatile
    private var notifSeen: Long = 0L
    private var appUp: Long = 0L
    private var appDown: Long = 0L

    /** "1"/"0" => forced by admin, "auto"/missing => follow the user's local toggle. */
    private fun effFlag(adminKey: String, userValue: Boolean): Boolean {
        val f = adminFlags
        return when (f[adminKey]) {
            "1" -> true
            "0" -> false
            else -> userValue
        }
    }

    private fun effAutoPingNew(): Boolean = effFlag("auto_ping_new", _settings.value.autoPingNew)
    private fun effAutoFlagNew(): Boolean = effFlag("auto_flag_new", _settings.value.autoFlagNew)
    private fun effPingStart(): Boolean = effFlag("ping_start", _settings.value.pingOnStart)
    private fun effFlagStart(): Boolean = effFlag("flag_start", _settings.value.flagOnStart)

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
        if (_state.value.servers.isNotEmpty() && effPingStart()) {
            pingServers(null, effFlagStart())
        }
        // remote admin backend (cloudflare worker - see cloudflare/worker.js)
        Remote.BASE = store.loadRemoteBase()
        loadRemoteState()
        syncNow()
        startRemoteLoop()
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
                            ir.v2rayng.compose.core.RealState.CONNECTED -> {
                                log("INFO", S.LOG_CONNECTED)
                                Remote.event(Remote.deviceId(ir.v2rayng.compose.AppCtx.app), "connect")
                                // tunnel watchdog: only trust CONNECTED when real internet passes through it
                                viewModelScope.launch {
                                    delay(2500L)
                                    var ok = false
                                    var attempt = 0
                                    while (!ok && attempt < 8) {
                                        attempt++
                                        ok = probeViaTunnel()
                                        if (!ok) delay(2000L)
                                    }
                                    if (!ok) {
                                        // second, independent anchor avoids false alarms on a slow handshake
                                        val ok2 = probeViaTunnel("https://cp.cloudflare.com/generate_204")
                                        if (!ok2) {
                                            ir.v2rayng.compose.core.XrayBridge.setState(ir.v2rayng.compose.core.RealState.BROKEN, "Tunnel probe failed")
                                            log("ERROR", "Failed to connect to server")
                                        } else {
                                            log("WARN", "Tunnel slow but OK")
                                        }
                                    }
                                }
                            }
                            ir.v2rayng.compose.core.RealState.BROKEN -> {
                                Remote.event(Remote.deviceId(ir.v2rayng.compose.AppCtx.app), "disconnect")
                                log("ERROR", "${S.LOG_FAILED}${if (st.message.isNotBlank()) " - ${st.message}" else ""}")
                            }
                            ir.v2rayng.compose.core.RealState.IDLE -> {
                                if (lastRealState == ir.v2rayng.compose.core.RealState.CONNECTED) {
                                    Remote.event(Remote.deviceId(ir.v2rayng.compose.AppCtx.app), "disconnect")
                                }
                                log("INFO", S.LOG_DISCONNECTED)
                            }
                        }
                        if (st.state == ir.v2rayng.compose.core.RealState.CONNECTED && _settings.value.autoFlag) {
                            val sel = _state.value.servers.firstOrNull { it.id == _state.value.selectedId }
                            if (sel?.link != null) {
                                viewModelScope.launch {
                                    var res: IpProbe? = null
                                    var attempt = 0
                                    while (res == null && attempt < 3) {
                                        attempt++
                                        delay(800L * attempt)
                                        res = ipCountryNow()
                                    }
                                    if (res != null) {
                                        applyFlag(res.code)
                                        put { s ->
                                            s.copy(
                                                servers = s.servers.map { if (it.id == sel.id) it.copy(country = res.code) else it },
                                                ipCountry = res.code,
                                                myIp = res.ip,
                                                altIp = res.altIp,
                                                altCountry = res.altCountry,
                                            )
                                        }
                                        store.saveServers(_state.value.servers, _state.value.selectedId)
                                        log("INFO", "Exit IP: ${res.ip} (${res.code}) ${res.detail}")
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
    fun setMainIndex(v: Int, pack: Int = -1) = setSettings { it.copy(mainIndex = v, mainPack = pack) }
    fun setFollowFinger(v: Boolean) = setSettings { it.copy(followFinger = v) }
    fun setAnimate(v: Boolean) = setSettings { it.copy(animate = v) }
    fun setStateMotion(v: Boolean) = setSettings { it.copy(stateMotion = v) }
    fun setShowNotification(v: Boolean) = setSettings { it.copy(showNotification = v) }
    fun setNotifSticker(v: Boolean) = setSettings { it.copy(notifSticker = v) }
    fun setNotifColor(v: Long) = setSettings { it.copy(notifColor = v) }
    fun setFlagAvatars(v: Boolean) = setSettings { it.copy(flagAvatars = v) }
    fun setConnectFlagDancer(v: Boolean) = setSettings { it.copy(connectFlagDancer = v) }

    fun checkMyIp() {
        viewModelScope.launch {
            val res = ipCountryNow()
            if (res != null) {
                put { s -> s.copy(myIp = res.ip, ipCountry = res.code, altIp = res.altIp, altCountry = res.altCountry) }
                log("INFO", "IP ${res.ip} (${res.code}) ${res.detail}")
            } else {
                log("WARN", S.LOG_IP_FAIL)
            }
        }
    }

    /* ------------ remote admin backend (cloudflare worker + d1) ------------ */

    private fun loadRemoteState() {
        try {
            val o = JSONObject(store.loadRemote())
            popupDismissed = o.optLong("pd", 0L)
            updateDismiss = o.optLong("ud", 0L)
            notifSeen = o.optLong("ns", 0L)
        } catch (_: Exception) {
        }
    }

    private fun saveRemoteState() {
        store.saveRemote(
            JSONObject()
                .put("pd", popupDismissed)
                .put("ud", updateDismiss)
                .put("ns", notifSeen)
                .toString(),
        )
    }

    fun setRemoteBase(url: String) {
        Remote.BASE = url.trim()
        store.saveRemoteBase(Remote.BASE)
    }

    fun remoteBase(): String = Remote.BASE

    fun syncNow() {
        val dev = Remote.deviceId(ir.v2rayng.compose.AppCtx.app)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Remote.sync(dev, "sync", _state.value.vpnState == VpnState.CONNECTED)
                if (res != null) {
                    applyRemote(res)
                } else {
                    handleSyncFail()
                }
            } catch (e: Exception) {
                handleSyncFail()
            }
        }
    }

    private suspend fun handleSyncFail() {
        val online = withContext(Dispatchers.IO) { netOnline() }
        if (online) {
            srvFailToast()
            log("WARN", "Failed to connect to server")
        } else {
            netToast()
            log("WARN", "No internet connection")
        }
    }

    /** English user-facing network alert (must run on main thread to not crash). */
    fun netToast() {
        val now = System.currentTimeMillis()
        if (now - lastNetToast < 4000L) return
        lastNetToast = now
        try {
            val ctx = try { ir.v2rayng.compose.AppCtx.app } catch (e: Exception) { return }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    android.widget.Toast.makeText(ctx, "No internet connection", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun srvFailToast() {
        val now = System.currentTimeMillis()
        if (now - lastSrvToast < 4000L) return
        lastSrvToast = now
        try {
            val ctx = try { ir.v2rayng.compose.AppCtx.app } catch (e: Exception) { return }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    android.widget.Toast.makeText(ctx, "Failed to connect to server", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun isOnline(): Boolean {
        return try {
            val cm = AppCtx.app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            for (net in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                val hasRealTransport = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                if (hasRealTransport) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /** True connectivity: caps across all networks, else a raw TCP/IP socket (no DNS, tunnel-proof). */
    suspend fun netOnline(): Boolean = withContext(Dispatchers.IO) {
        if (isOnline()) return@withContext true
        for (host in listOf("1.1.1.1", "8.8.8.8")) {
            try {
                val s = java.net.Socket()
                try {
                    s.connect(java.net.InetSocketAddress(host, 53), 1500)
                } finally {
                    try { s.close() } catch (_: Exception) {}
                }
                return@withContext true
            } catch (_: Exception) {
            }
        }
        false
    }

    private fun startRemoteLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(4000L)
            var first = true
            while (true) {
                try {
                    val connected = _state.value.vpnState == VpnState.CONNECTED
                    val dev = Remote.deviceId(ir.v2rayng.compose.AppCtx.app)
                    val res = Remote.sync(dev, if (first) "sync" else "heartbeat", connected)
                    if (res != null) applyRemote(res)
                    Remote.beat(dev, connected)
                } catch (_: Exception) {
                }
                first = false
                delay(120_000L)
            }
        }
    }

    /** Merge the admin-managed server list; per-remote-id user data (stats, pin, sticker) is preserved. */
    private fun mergeRemoteServers(list: List<Remote.ServerEntry>) {
        val knownBefore = _state.value.servers.filter { it.id.startsWith("rem-") }.map { it.id }.toSet()
        put { s ->
            val kept = s.servers.filter { !it.id.startsWith("rem-") }
            val rem = list.map { e ->
                val ex = s.servers.firstOrNull { it.id == e.id }
                val fromLink = e.link?.let { try { ir.v2rayng.compose.core.parseProxyLink(it) } catch (_: Exception) { null } }
                val addr = if (!e.address.isNullOrBlank() && e.address != "0.0.0.0") e.address
                    else fromLink?.address?.takeIf { it.isNotBlank() } ?: e.address
                val prt = fromLink?.port?.takeIf { it > 0 } ?: e.port
                val base = ServerProfile(
                    id = e.id,
                    name = e.name, // admin-provided - never the config (#NAME) tag
                    host = "$addr:$prt",
                    stickerIndex = ex?.stickerIndex ?: ((e.id.hashCode() and 0x3FF) % 997),
                    country = ex?.country ?: "",
                    ping = ex?.ping,
                    link = e.link,
                    pinned = ex?.pinned ?: false,
                    pack = ex?.pack ?: -1,
                    category = e.category,
                    note = e.msg,
                )
                base.copy(up = ex?.up ?: 0L, down = ex?.down ?: 0L)
            }
            val still = rem.map { it.id }.toSet()
            val selectId = if (s.selectedId.startsWith("rem-") && !still.contains(s.selectedId)) {
                rem.firstOrNull()?.id ?: kept.firstOrNull()?.id ?: s.selectedId
            } else {
                s.selectedId
            }
            s.copy(servers = kept + rem, selectedId = selectId)
        }
        store.saveServers(_state.value.servers, _state.value.selectedId)
        mergeStamp = System.currentTimeMillis()
        // auto-ping freshly delivered servers (admin added new ones)
        val newIds = list.map { it.id }.filter { !knownBefore.contains(it) }.toSet()
        if (newIds.isNotEmpty()) {
            if (effAutoPingNew()) {
                log("INFO", "Auto ping ${newIds.size} new server(s)")
                pingServers(newIds, effAutoFlagNew())
            } else {
                log("INFO", "${newIds.size} new server(s) arrived")
            }
        }
    }

    fun lastMergeStamp(): Long = mergeStamp

    /* small runtime state is declared at the top of this class (field order safety) */

    private fun applyRemote(r: Remote.Result) {
        adminFlags = r.flags
        put { s ->
            val upd = r.update?.let {
                if (!it.forced && updateDismiss + 24 * 3600_000L > System.currentTimeMillis() && updateDismiss > 0) null else it
            }
            s.copy(
                appDisabled = !r.enabled,
                remotePopup = r.popup?.takeIf { it.id != popupDismissed },
                remoteUpdate = upd,
                remoteNotif = r.notifications.firstOrNull()?.takeIf { it.id > notifSeen },
            )
        }
        mergeRemoteServers(r.servers)
    }

    fun ackPopup(id: Long) {
        popupDismissed = id
        put { s -> s.copy(remotePopup = null) }
        Remote.ack(Remote.deviceId(ir.v2rayng.compose.AppCtx.app), "popup", id)
        saveRemoteState()
    }

    fun ackNotif(id: Long) {
        notifSeen = id
        put { s -> s.copy(remoteNotif = null) }
        Remote.ack(Remote.deviceId(ir.v2rayng.compose.AppCtx.app), "notif", id)
        saveRemoteState()
    }

    fun dismissUpdateDialog() {
        updateDismiss = System.currentTimeMillis()
        put { s -> s.copy(remoteUpdate = null) }
        saveRemoteState()
    }

    fun resetAllServerTraffic() {
        put { s -> s.copy(servers = s.servers.map { it.copy(up = 0, down = 0) }) }
        ir.v2rayng.compose.core.XrayBridge.requestCounterReset()
        log("INFO", "All server traffic reset")
    }
    fun setSpeed(v: Float) = setSettings { it.copy(speed = v) }
    fun setBgSpeed(v: Float) = setSettings { it.copy(bgSpeed = v) }
    fun setNavStickers(v: Boolean) = setSettings { it.copy(navStickers = v) }
    fun setAutoFlag(v: Boolean) = setSettings { it.copy(autoFlag = v) }
    fun setPingOnStart(v: Boolean) = setSettings { it.copy(pingOnStart = v) }
    fun setAutoPingNew(v: Boolean) = setSettings { it.copy(autoPingNew = v) }
    fun setFlagOnStart(v: Boolean) = setSettings { it.copy(flagOnStart = v) }
    fun setAutoFlagNew(v: Boolean) = setSettings { it.copy(autoFlagNew = v) }

    fun setNavOrder(order: List<Int>) = setSettings { it.copy(navOrder = packList(order)) }

    fun setNavIcon(slot: Int, idx: Int, pack: Int = -1) = setSettings { s ->
        val cur = parseList(s.navIcons, (0..4).toList()).toMutableList()
        while (cur.size < 5) cur.add(cur.size)
        cur[slot.coerceIn(0, cur.size - 1)] = idx
        val packs = parseList(s.navPacks, listOf(-1, -1, -1, -1, -1)).toMutableList()
        while (packs.size < 5) packs.add(-1)
        packs[slot.coerceIn(0, packs.size - 1)] = pack
        s.copy(navIcons = packList(cur), navPacks = packList(packs))
    }

    fun setUpIcon(i: Int, pack: Int = -1) = setSettings { it.copy(upIcon = i, upPack = pack) }
    fun setDownIcon(i: Int, pack: Int = -1) = setSettings { it.copy(downIcon = i, downPack = pack) }
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

    fun setMainFx(alpha: Float, blur: Float, scale: Float) = setSettings { it.copy(mainFx = packStickerFx(alpha, blur, scale)) }
    fun setWallFx(alpha: Float, blur: Float, scale: Float) = setSettings { it.copy(wallFx = packStickerFx(alpha, blur, scale)) }
    fun setUpFx(alpha: Float, blur: Float, scale: Float) = setSettings { it.copy(upFx = packStickerFx(alpha, blur, scale)) }
    fun setDownFx(alpha: Float, blur: Float, scale: Float) = setSettings { it.copy(downFx = packStickerFx(alpha, blur, scale)) }

    fun setNavFx(slot: Int, alpha: Float, blur: Float, scale: Float) = setSettings { s ->
        val segs = s.navFx.split(";").map { parseFxPack(it) }.toMutableList()
        while (segs.size < 5) segs.add(StickerFx())
        segs[slot.coerceIn(0, 4)] = StickerFx(alpha, blur, scale)
        s.copy(navFx = segs.joinToString(";") { packFx(it) })
    }

    fun setRouteFx(slot: Int, alpha: Float, blur: Float, scale: Float) = setSettings { s ->
        val segs = s.routeFx.split(";").map { parseFxPack(it) }.toMutableList()
        while (segs.size < 3) segs.add(StickerFx())
        segs[slot.coerceIn(0, 2)] = StickerFx(alpha, blur, scale)
        s.copy(routeFx = segs.joinToString(";") { packFx(it) })
    }

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

    fun setServerIcon(id: String, idx: Int, pack: Int = -1) {
        put { s -> s.copy(servers = s.servers.map { if (it.id == id) it.copy(stickerIndex = idx, pack = pack) else it }) }
        store.saveServers(_state.value.servers, _state.value.selectedId)
    }

    fun setRouteIcon(slot: Int, idx: Int, pack: Int = -1) = setSettings { s ->
        val cur = parseList(s.routeIcons, listOf(0, 1, 2)).toMutableList()
        while (cur.size < 3) cur.add(cur.size)
        cur[slot.coerceIn(0, 2)] = idx
        val packs = parseList(s.routePacks, listOf(-1, -1, -1)).toMutableList()
        while (packs.size < 3) packs.add(-1)
        packs[slot.coerceIn(0, 2)] = pack
        s.copy(routeIcons = packList(cur), routePacks = packList(packs))
    }

    fun srvPack(id: String): Int =
        _state.value.servers.firstOrNull { it.id == id }?.pack ?: -1

    /** Def on a server: back to the default sticker AND re-detect its country flag. */
    fun resetServerIcon(id: String) {
        put { s -> s.copy(servers = s.servers.map { if (it.id == id) it.copy(pack = -1) else it }) }
        store.saveServers(_state.value.servers, _state.value.selectedId)
        log("INFO", "Server sticker reset - detecting country...")
        viewModelScope.launch {
            val connectedSel = _state.value.vpnState == VpnState.CONNECTED && _state.value.selectedId == id
            val host = _state.value.servers.firstOrNull { it.id == id }?.host?.substringBefore(":")
            val code = if (connectedSel) {
                ipCountryNow()?.code
            } else {
                if (host.isNullOrBlank()) null else dnsCountry(host)
            }
            if (code != null) {
                put { s -> s.copy(servers = s.servers.map { if (it.id == id) it.copy(country = code) else it }) }
                if (connectedSel) {
                    applyFlag(code)
                }
                log("INFO", "Server country: $code")
            } else {
                log("WARN", S.LOG_IP_FAIL)
            }
        }
    }

    private suspend fun dnsCountry(host: String): String? = withContext(Dispatchers.IO) {
        try {
            val ip = java.net.InetAddress.getByName(host).hostAddress ?: return@withContext null
            val conn = java.net.URL("https://ipwho.is/$ip").openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(text)?.groupValues?.get(1)?.uppercase()
        } catch (_: Exception) {
            null
        }
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
        if (!isOnline()) {
            // judge offline only after a real probe, then start
            viewModelScope.launch {
                if (netOnline()) {
                    doConnect(target)
                } else {
                    netToast()
                    log("WARN", "No internet connection")
                }
            }
            return
        }
        doConnect(target)
    }

    private fun doConnect(target: ServerProfile) {
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
            val res = withContext(Dispatchers.IO) {
                try {
                    val conn = java.net.URL("https://ipwho.is/").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val ip = Regex("\"ip\"\\s*:\\s*\"([0-9a-fA-F.:]+)\"").find(text)?.groupValues?.get(1)
                    val code = Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(text)?.groupValues?.get(1)
                    if (code != null) (ip ?: "") to code else null
                } catch (e: Exception) {
                    try {
                        val conn = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
                        conn.connectTimeout = 4500
                        conn.readTimeout = 4500
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val ip = Regex("\"ip\"\\s*:\\s*\"([0-9a-fA-F.:]+)\"").find(text)?.groupValues?.get(1)
                        val code = Regex("\"country_code\"\\s*:\\s*\"([A-Z]{2})\"").find(text)?.groupValues?.get(1)
                        if (code != null) (ip ?: "") to code else null
                    } catch (e2: Exception) {
                        null
                    }
                }
            }
            if (res != null) {
                applyFlag(res.second)
                put { s -> s.copy(ipCountry = res.second.uppercase(), myIp = res.first) }
                log("INFO", S.LOG_IP_OK.format(res.second.uppercase()))
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

    fun pingAll() = pingServers(null, _settings.value.flagOnStart)

    /** Real ping via core. ids null = all servers; wantFlag = also fetch country flag after ping. */
    fun pingServers(ids: Collection<String>?, wantFlag: Boolean) {
        viewModelScope.launch {
            if (!netOnline()) {
                netToast()
                log("WARN", "No internet connection")
                return@launch
            }
            // xray runtime must be initialized before measuring delays
            try {
                val d = java.io.File(ir.v2rayng.compose.AppCtx.app.filesDir, "core")
                if (!d.exists()) d.mkdirs()
                libv2ray.Libv2ray.initCoreEnv(d.absolutePath, "")
            } catch (_: Exception) {
            }
            log("INFO", S.LOG_PING_START)
            val targets = if (ids == null) _state.value.servers else _state.value.servers.filter { ids.contains(it.id) }
            for (srv in targets) {
                val ms: Int? = if (srv.link != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val l = ir.v2rayng.compose.core.parseProxyLink(srv.link!!)
                            if (l == null) {
                                log("WARN", "Ping failed to parse: ${srv.name}")
                                null
                            } else {
                                val config = ir.v2rayng.compose.core.buildXrayConfig(l)
                                val d = libv2ray.Libv2ray.measureOutboundDelay(config, "https://www.google.com/generate_204")
                                if (d >= 0) d.toInt() else null
                            }
                        } catch (e: Exception) {
                            log("WARN", "Ping error ${srv.name}: ${e.message}")
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

    /** Probe result: cloudflare trace row plus the ipwho.is row when they disagree. */
    data class IpProbe(
        val ip: String,
        val code: String,
        val altIp: String,
        val altCountry: String,
        val detail: String,
    )

    private suspend fun ipCountryNow(): IpProbe? {
        val throughTunnel = _state.value.vpnState == VpnState.CONNECTED &&
            _state.value.servers.firstOrNull { it.id == _state.value.selectedId }?.link != null
        return withContext(Dispatchers.IO) {
            var cfIp: String? = null
            var cfCode: String? = null
            var whoIp: String? = null
            var whoCode: String? = null
            var apiCode: String? = null

            // 1) cloudflare trace - fastest truth for exit ip + country
            try {
                val url = java.net.URL("https://www.cloudflare.com/cdn-cgi/trace")
                val conn = if (throughTunnel) {
                    val socks = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 10890))
                    url.openConnection(socks)
                } else {
                    url.openConnection()
                } as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                cfIp = Regex("^ip=([0-9a-fA-F.:]+)$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
                cfCode = Regex("^loc=([A-Za-z]{2})$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
            } catch (_: Exception) {
            }

            // 2) ipwho.is - has its own ip + country view
            try {
                val url = java.net.URL("https://ipwho.is/")
                val conn = if (throughTunnel) {
                    val socks = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 10890))
                    url.openConnection(socks)
                } else {
                    url.openConnection()
                } as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                whoIp = Regex("\"ip\"\\s*:\\s*\"([0-9a-fA-F.:]+)\"").find(text)?.groupValues?.get(1)
                whoCode = Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(text)?.groupValues?.get(1)
            } catch (_: Exception) {
            }

            // 3) ipapi.co with the resolved ip
            val ipForApi = cfIp
            if (ipForApi != null) {
                try {
                    val conn = java.net.URL("https://ipapi.co/$ipForApi/json/").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    apiCode = Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(text)?.groupValues?.get(1)
                } catch (_: Exception) {
                }
            }

            val votes = listOfNotNull(cfCode?.uppercase(), whoCode?.uppercase(), apiCode?.uppercase())
            val code = votes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (code == null) {
                null
            } else {
                // main row = cloudflare (fall back to ipwho when cloudflare failed)
                val mainIp = cfIp ?: whoIp ?: ""
                val mainCode = cfCode?.uppercase() ?: whoCode?.uppercase() ?: code
                // alt row only when ipwho sees a different country
                val al = if (whoIp != null && whoCode != null &&
                    whoCode.uppercase() != mainCode && mainIp != "" && mainIp != whoIp
                ) {
                    IpProbe(mainIp, mainCode, whoIp ?: "", whoCode?.uppercase() ?: "", "loc=$cfCode who=$whoCode api=$apiCode")
                } else {
                    IpProbe(mainIp, mainCode, "", "", "loc=$cfCode who=$whoCode api=$apiCode")
                }
                al
            }
        }
    }

    fun addServerFromLink(raw: String) {
        val link = ir.v2rayng.compose.core.parseProxyLink(raw)
        if (link == null) {
            log("ERROR", "Link not recognized (vless / vmess / trojan / ss)")
            return
        }
        // server names are admin-generated - the name inside the config (#NAME) is never used
        val shownName = "srv-${_state.value.servers.size + 1}"
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

    fun togglePin(id: String) {
        put { s -> s.copy(servers = s.servers.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }) }
        store.saveServers(_state.value.servers, _state.value.selectedId)
    }

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
        val tunnelUp = _state.value.vpnState == VpnState.CONNECTED || _state.value.vpnState == VpnState.CONNECTING
        put { s -> s.copy(selectedId = id) }
        store.saveSelectedId(id)
        log("INFO", S.LOG_SELECTED.format(name))
        if (tunnelUp) {
            // hot swap: drop the old tunnel and bring the newly selected one up
            val sel = _state.value.servers.firstOrNull { it.id == id }
            if (sel?.link != null) {
                val parsed = ir.v2rayng.compose.core.parseProxyLink(sel.link)
                if (parsed != null) {
                    log("INFO", S.LOG_CONNECTING.format(sel.name))
                    // the running service's connect intent swaps the core internally (700ms settle)
                    ir.v2rayng.compose.core.XrayVpnService.start(appCtx(), parsed, sel.link)
                } else {
                    put { s -> s.copy(vpnState = VpnState.BROKEN) }
                }
            } else {
                connectJob?.cancel()
                trafficJob?.cancel()
                XrayVpnServiceStop()
                put { s -> s.copy(vpnState = VpnState.IDLE) }
                if (_settings.value.autoFlag) {
                    fetchIpFlag()
                }
            }
        }
    }

    private fun XrayVpnServiceStop() {
        ir.v2rayng.compose.core.XrayVpnService.stop(appCtx())
    }

    /** HTTP 204 test forced through the local socks inbound - proves real end-to-end connectivity. */
    private suspend fun probeViaTunnel(url: String = "https://www.google.com/generate_204"): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection(
                java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 10890)),
            ) as HttpURLConnection
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.useCaches = false
            val ok = conn.responseCode in 200..299
            try { conn.inputStream.close() } catch (_: Exception) {}
            ok
        } catch (_: Exception) {
            false
        }
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
