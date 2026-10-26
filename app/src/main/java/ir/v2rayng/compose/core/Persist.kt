package ir.v2rayng.compose.core

import java.util.concurrent.Executors

/**
 * SettingsStore/SecurePrefs writes go through a StrongBox-backed keystore key and cost ~200ms
 * each on the phone seen in the MIUI APP_SCOUT traces, so no preference write may happen on the
 * UI thread. One thread keeps the writes ordered without the caller waiting.
 */
object Persist {

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "blob-persist").apply { isDaemon = true }
    }

    fun write(block: () -> Unit) {
        try {
            io.execute { try { block() } catch (_: Exception) {} }
        } catch (_: Exception) {
        }
    }

    /** Runs on the calling thread. Used when the process is on its way out: the worker thread is
     *  a daemon, so a write still sitting in its queue would be lost with it. */
    fun writeNow(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}
