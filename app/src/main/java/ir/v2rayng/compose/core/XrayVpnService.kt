package ir.v2rayng.compose.core

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class XrayVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ir.blob.vpn.CONNECT"
        const val ACTION_STOP = "ir.blob.vpn.STOP"
        var pendingLink: VlessLink? = null

        fun start(context: android.content.Context, link: VlessLink) {
            pendingLink = link
            context.startService(Intent(context, XrayVpnService::class.java).setAction(ACTION_CONNECT))
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, XrayVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private var controller: CoreController? = null
    private var tunFd = -1
    private var lastUp = 0L
    private var lastDown = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null) {
                try {
                    val raw = c.queryAllOutboundTrafficStats()
                    val t = parseTrafficStats(raw, lastUp, lastDown)
                    val prevPair = lastStatsAt
                    val now = System.nanoTime()
                    var us = 0.0
                    var ds = 0.0
                    if (prevPair > 0) {
                        val dt = (now - prevPair) / 1e9
                        if (dt > 0.2 && t.up >= lastUp && t.down >= lastDown) {
                            us = (t.up - lastUp).toDouble() / dt
                            ds = (t.down - lastDown).toDouble() / dt
                        }
                    }
                    lastStatsAt = now
                    lastUp = t.up
                    lastDown = t.down
                    XrayBridge.setTraffic(t.up, t.down, us, ds)
                } catch (_: Exception) {
                }
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }
    private var lastStatsAt = 0L
    private val stateSetter = Runnable { XrayBridge.setState(RealState.CONNECTED) }

    private val callback = object : CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            XrayBridge.addLog("CORE", p1 ?: "")
            mainHandler.removeCallbacks(stateSetter)
            mainHandler.postDelayed(stateSetter, 900L)
            return 0L
        }

        override fun shutdown(): Long {
            XrayBridge.addLog("CORE", "Core shutdown")
            XrayBridge.setState(RealState.IDLE)
            return 0L
        }

        override fun startup(): Long {
            XrayBridge.addLog("CORE", "Core started")
            XrayBridge.setState(RealState.CONNECTED)
            return 0L
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCore()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val link = pendingLink
                if (link == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat()
                startCore(link)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel("blob_vpn", "Blob VPN", android.app.NotificationManager.IMPORTANCE_LOW)
            manager?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "blob_vpn")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Blob")
            .setContentText(XrayBridge.status.value.message.ifBlank { "Tunnel running" })
            .setOngoing(true)
            .build()
        androidx.core.app.ServiceCompat.startForeground(this, 1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun startCore(link: VlessLink) {
        try {
            Libv2ray.initCoreEnv(File(filesDir, "core").apply { mkdirs() }.absolutePath, "")
            XrayBridge.addLog("INFO", "Starting core: ${link.address}:${link.port} (${link.type}/${link.security})")
            XrayBridge.setState(RealState.CONNECTING)
            val tun = Builder()
                .setSession("Blob")
                .setMtu(1500)
                .addAddress("10.9.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .establish()
            if (tun == null) {
                XrayBridge.addLog("ERROR", "TUN create failed (another VPN active or duplicate)")
                XrayBridge.setState(RealState.BROKEN, "TUN create failed")
                return
            }
            tunFd = tun.detachFd()
            val fd = protect(tunFd)
            if (!fd) {
                XrayBridge.addLog("ERROR", "protect(fd) failed")
                XrayBridge.setState(RealState.BROKEN, "protect failed")
                return
            }
            lastUp = 0
            lastDown = 0
            controller = Libv2ray.newCoreController(callback)
            controller?.startLoop(buildXrayConfig(link), tunFd)
            XrayBridge.addLog("INFO", "Core loop entered")
            mainHandler.removeCallbacks(statsRunnable)
            mainHandler.postDelayed(statsRunnable, 1200L)
        } catch (e: Exception) {
            XrayBridge.addLog("ERROR", "Core error: ${e.message}")
            XrayBridge.setState(RealState.BROKEN, e.message ?: "core error")
        }
    }

    private fun stopCore() {
        mainHandler.removeCallbacks(statsRunnable)
        mainHandler.removeCallbacks(stateSetter)
        try {
            controller?.stopLoop()
        } catch (_: Exception) {
        }
        controller = null
        if (tunFd > 0) {
            tunFd = -1
        }
        XrayBridge.reset()
    }

    override fun onRevoke() {
        stopCore()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopCore()
        super.onDestroy()
    }
}
