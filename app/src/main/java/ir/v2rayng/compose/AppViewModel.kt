package ir.v2rayng.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.LogLine
import ir.v2rayng.compose.data.ServerProfile
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.packList
import ir.v2rayng.compose.data.parseList
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

class AppViewModel(private val store: SettingsStore) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var connectJob: Job? = null
    private var trafficJob: Job? = null

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
        put { s -> s.copy(vpnState = VpnState.CONNECTING) }
        log("INFO", "Ø¯Ø± Ø­Ø§Ù„ Ø§ØªØµØ§Ù„ Ø¨Ù‡ Â«${target.name}Â» â€¦")
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            delay(800L + Random.nextLong(900))
            if (_state.value.vpnState == VpnState.CONNECTING) {
                if (Random.nextInt(7) == 0) {
                    put { s -> s.copy(vpnState = VpnState.BROKEN) }
                    log("ERROR", "Ø§ØªØµØ§Ù„ Ø¨Ø±Ù‚Ø±Ø§Ø± Ù†Ø´Ø¯ â€“ Ø¯ÙˆØ¨Ø§Ø±Ù‡ Ø§Ù…ØªØ­Ø§Ù† Ú©Ù†")
                } else {
                    put { s -> s.copy(vpnState = VpnState.CONNECTED) }
                    log("INFO", "Ø§ØªØµØ§Ù„ Ø¨Ø±Ù‚Ø±Ø§Ø± Ø´Ø¯ØŒ ØªÙˆÙ†Ù„ ÙØ¹Ø§Ù„ Ø§Ø³Øª")
                    startTraffic()
                    if (_settings.value.autoFlag && target.country.isNotBlank()) {
                        applyFlag(target.country)
                    }
                }
            }
        }
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
        connectJob?.cancel()
        trafficJob?.cancel()
        put { s ->
            s.copy(
                vpnState = VpnState.IDLE,
                traffic = s.traffic.copy(upSpeed = 0.0, downSpeed = 0.0),
            )
        }
        log("INFO", "Ø§ØªØµØ§Ù„ Ù‚Ø·Ø¹ Ø´Ø¯")
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
                log("INFO", "Ú©Ø´ÙˆØ± Ø¢ÛŒâ€ŒÙ¾ÛŒ: $code" + if (applied) " (Ù¾Ø³â€ŒØ²Ù…ÛŒÙ†Ù‡ Ø³Øª Ø´Ø¯)" else "")
            } else {
                log("WARN", "ØªØ´Ø®ÛŒØµ Ú©Ø´ÙˆØ± Ø¢ÛŒâ€ŒÙ¾ÛŒ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯")
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
            log("INFO", "Ø¢Ø²Ù…ÙˆÙ† ØªØ§Ø®ÛŒØ± Ø´Ø±ÙˆØ¹ Ø´Ø¯")
            for (srv in _state.value.servers) {
                delay(350)
                val ms = 40 + Random.nextInt(400)
                put { s ->
                    s.copy(servers = s.servers.map { if (it.id == srv.id) it.copy(ping = ms) else it })
                }
            }
            log("INFO", "Ø¢Ø²Ù…ÙˆÙ† ØªØ§Ø®ÛŒØ± Ú©Ø§Ù…Ù„ Ø´Ø¯")
        }
    }

    fun select(id: String) {
        val name = _state.value.servers.firstOrNull { it.id == id }?.name ?: return
        put { s -> s.copy(selectedId = id) }
        log("INFO", "Ù¾Ø±ÙˆÙØ§ÛŒÙ„ Ø§Ù†ØªØ®Ø§Ø¨ Ø´Ø¯: Â«$nameÂ»")
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
        log("INFO", "Ø³Ø±ÙˆØ± Ø¬Ø¯ÛŒØ¯ Ø§Ø¶Ø§ÙÙ‡ Ø´Ø¯: Â«$nameÂ»")
    }

    fun setRoute(key: String, value: Boolean) {
        put { s -> s.copy(routes = s.routes + (key to value)) }
        log("INFO", "Ù‚Ø§Ù†ÙˆÙ† Ù…Ø³ÛŒØ±ÛŒØ§Ø¨ÛŒ Â«${if (value) key else "!$key"}Â» ÙØ¹Ø§Ù„ Ø´Ø¯")
    }

    fun clearLogs() {
        put { s -> s.copy(logs = emptyList()) }
        log("INFO", "Ú¯Ø²Ø§Ø±Ø´ Ù¾Ø§Ú© Ø´Ø¯")
    }
}
