package ir.v2rayng.compose.core

import android.content.Context

/**
 * Records the last uncaught exception so a crash that kills the process can still be read
 * from the in-app log after the next start (the device is not connected to adb).
 * Written with commit(): apply() would not flush before the process dies.
 */
object CrashNote {

    private const val PREFS = "blob-crash"
    private const val KEY = "last"

    /** what the UI looked like when the crash happened, e.g. "light tab=2 srv=true" */
    @Volatile
    var ui: String = "?"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append(t.name).append(" | ").append(e.javaClass.name).append(": ").append(e.message)
                sb.append(" | ui=").append(ui)
                e.stackTrace.take(12).forEach { f ->
                    sb.append(" | at ").append(f.className).append('.').append(f.methodName).append(':').append(f.lineNumber)
                }
                var cause = e.cause
                var hops = 0
                while (cause != null && hops < 2) {
                    hops++
                    sb.append(" | caused by ").append(cause.javaClass.name).append(':').append(cause.message)
                    cause.stackTrace.firstOrNull()?.let { f ->
                        sb.append(" | at ").append(f.className).append('.').append(f.methodName).append(':').append(f.lineNumber)
                    }
                    cause = cause.cause
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, sb.toString().take(1800))
                    .commit()
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** Read once and clear, so the same crash is not reported again on the next launch. */
    fun take(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = sp.getString(KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        sp.edit().remove(KEY).commit()
        return v
    }
}
