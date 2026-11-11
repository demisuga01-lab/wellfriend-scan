package dev.wellfriend.scan.perception

/**
 * Android wiring supplies a Logcat sink; JVM tests keep this dependency-free module platform-neutral.
 * The scanner never logs image bytes through this bridge.
 */
object NativeRuntimeLogger {
    @Volatile private var sink: ((Level, String, Throwable?) -> Unit)? = null

    enum class Level { DEBUG, INFO, ERROR }

    fun configure(logger: (Level, String, Throwable?) -> Unit) {
        sink = logger
    }

    fun debug(message: String) = sink?.invoke(Level.DEBUG, message, null)
    fun info(message: String) = sink?.invoke(Level.INFO, message, null)
    fun error(message: String, throwable: Throwable? = null) = sink?.invoke(Level.ERROR, message, throwable)
}
