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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    fun setStickerStyle(i: Int) = setSettings { it.copy(stickerStyle = i) }
    fun setWallpaper(i: Int) = setSettings { it.copy(wallpaperIndex = i) }
    fun setFollowFinger(v: Boolean) = setSettings { it.copy(followFinger = v) }
    fun setAnimate(v: Boolean) = setSettings { it.copy(animate = v) }
    fun setSpeed(v: Float) = setSettings { it.copy(speed = v) }

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
        log("INFO", "در حال اتصال به «${target.name}» …")
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            delay(800L + Random.nextLong(900))
            if (_state.value.vpnState == VpnState.CONNECTING) {
                if (Random.nextInt(7) == 0) {
                    put { s -> s.copy(vpnState = VpnState.BROKEN) }
                    log("ERROR", "اتصال برقرار نشد – دوباره امتحان کن")
                } else {
                    put { s -> s.copy(vpnState = VpnState.CONNECTED) }
                    log("INFO", "اتصال برقرار شد، تونل فعال است")
                    startTraffic()
                }
            }
        }
    }

    private fun startTraffic() {
        trafficJob?.cancel()
        trafficJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val up = Random.nextInt(2, 81) * 1024.0
                val down = up * (1.2 + Random.nextDouble(0.0, 4.8))
                put { s ->
                    s.copy(
                        traffic = s.traffic.copy(
                            up = s.traffic.up + (up * 0.5).toLong(),
                            down = s.traffic.down + (down * 0.5).toLong(),
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
        log("INFO", "اتصال قطع شد")
    }

    fun pingAll() {
        viewModelScope.launch {
            log("INFO", "آزمون تاخیر شروع شد")
            for (srv in _state.value.servers) {
                delay(350)
                val ms = 40 + Random.nextInt(400)
                put { s ->
                    s.copy(servers = s.servers.map { if (it.id == srv.id) it.copy(ping = ms) else it })
                }
            }
            log("INFO", "آزمون تاخیر کامل شد")
        }
    }

    fun select(id: String) {
        val name = _state.value.servers.firstOrNull { it.id == id }?.name ?: return
        put { s -> s.copy(selectedId = id) }
        log("INFO", "پروفایل انتخاب شد: «$name»")
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
        log("INFO", "سرور جدید اضافه شد: «$name»")
    }

    fun setRoute(key: String, value: Boolean) {
        put { s -> s.copy(routes = s.routes + (key to value)) }
        log("INFO", "قانون مسیریابی «${if (value) key else "!$key"}» فعال شد")
    }

    fun clearLogs() {
        put { s -> s.copy(logs = emptyList()) }
        log("INFO", "گزارش پاک شد")
    }
}
