package ir.v2rayng.compose.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VpnState { IDLE, CONNECTING, CONNECTED, BROKEN }

data class AppInfo(
    val label: String,
    val pkg: String,
)

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val stickerIndex: Int,
    val country: String = "",
    val ping: Int? = null,
    val pingErr: String? = null,
    val link: String? = null,
    val up: Long = 0L,
    val down: Long = 0L,
    val pinned: Boolean = false,
    val pack: Int = -1,
    val category: String = "",
    val note: String = "",
    /** per-server bubble color override; -1 = follow the global bubble color */
    val bubbleColor: Long = -1L,
)

data class TrafficStat(
    val up: Long = 0L,
    val down: Long = 0L,
    val upSpeed: Double = 0.0,
    val downSpeed: Double = 0.0,
)

data class LogLine(
    val time: String,
    val level: String,
    val msg: String,
)

data class AppState(
    val servers: List<ServerProfile> = emptyList(),
    val selectedId: String = "",
    val vpnState: VpnState = VpnState.IDLE,
    /** when the tunnel came up (0 = not connected) - the connect clock in the status pill */
    val connSince: Long = 0L,
    val traffic: TrafficStat = TrafficStat(),
    val logs: List<LogLine> = emptyList(),
    val ipCountry: String = "",
    val myIp: String = "",
    val altIp: String = "",
    val altCountry: String = "",
    val apps: List<AppInfo> = emptyList(),
    val appDisabled: Boolean = false,
    val remotePopup: ir.v2rayng.compose.core.Remote.PopupCfg? = null,
    val remoteUpdate: ir.v2rayng.compose.core.Remote.UpdateCfg? = null,
    val remoteNotif: ir.v2rayng.compose.core.Remote.NotifLine? = null,
    val routing: ir.v2rayng.compose.core.Remote.RoutingCfg? = null,
) {
    val selected: ServerProfile
        get() = servers.firstOrNull { it.id == selectedId }
            ?: servers.firstOrNull()
            ?: ServerProfile("none", "-", "-", 0)
}

object TimeKit {
    fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    /** elapsed wall time as h:mm:ss (mm:ss under an hour) - the connect clock */
    fun clock(ms: Long): String {
        val t = if (ms > 0L) ms / 1000L else 0L
        val h = t / 3600L
        val m = (t % 3600L) / 60L
        val s = t % 60L
        return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun human(b: Long): String = when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> String.format(Locale.US, "%.1fK", b / 1024.0)
        b < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2fM", b / 1048576.0)
        else -> String.format(Locale.US, "%.2fG", b / 1073741824.0)
    }

    fun speed(v: Double): String = String.format(Locale.US, "%.1f KB/s", v / 1024.0)
}
