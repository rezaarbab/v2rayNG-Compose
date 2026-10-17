package ir.v2rayng.compose.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VpnState { IDLE, CONNECTING, CONNECTED, BROKEN }

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val stickerIndex: Int,
    val country: String = "",
    val ping: Int? = null,
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
    val servers: List<ServerProfile> = listOf(
        ServerProfile("s1", "آلمان – فرانکفورت", "de-01.sticker-vpn.net", 1, "de"),
        ServerProfile("s2", "ترکیه – استانبول", "tr-02.sticker-vpn.net", 0, "tr"),
        ServerProfile("s3", "سوئد – استکهلم", "se-03.sticker-vpn.net", 2, "se"),
        ServerProfile("s4", "هلند – امستردام", "nl-04.sticker-vpn.net", 4, "nl"),
        ServerProfile("s5", "ژاپن – توکیو", "jp-05.sticker-vpn.net", 3, "jp"),
        ServerProfile("s6", "آمریکا – نیویورک", "us-06.sticker-vpn.net", 5, "us"),
    ),
    val selectedId: String = "s1",
    val vpnState: VpnState = VpnState.IDLE,
    val traffic: TrafficStat = TrafficStat(),
    val logs: List<LogLine> = emptyList(),
    val routes: Map<String, Boolean> = mapOf(
        "bypass_ir" to true,
        "block_ads" to true,
        "udp443" to false,
    ),
    val ipCountry: String = "",
) {
    val selected: ServerProfile get() = servers.first { it.id == selectedId }
}

object TimeKit {
    fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    fun human(b: Long): String = when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> String.format(Locale.US, "%.1fK", b / 1024.0)
        b < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2fM", b / 1048576.0)
        else -> String.format(Locale.US, "%.2fG", b / 1073741824.0)
    }

    fun speed(v: Double): String = String.format(Locale.US, "%.1f KB/s", v / 1024.0)
}
