package org.brightify.hyperdrive

import kotlinx.coroutines.Dispatchers
import kotlin.native.concurrent.ThreadLocal
import kotlin.reflect.KClass

// TODO: Unused for now.
internal annotation class EnableLogging()

public enum class LoggingLevel(public val levelValue: Int) {
    Error(1),
    Warn(2),
    Info(3),
    Debug(4),
    Trace(5),
}

private var logLevelOrBelow: LoggingLevel? = LoggingLevel.Warn

public class Logger private constructor(private val ownerDescription: String) {
    public fun isLoggingEnabled(level: LoggingLevel, throwable: Throwable? = null): Boolean {
        val logLevelOrBelow = logLevelOrBelow ?: return false
        return level.levelValue <= logLevelOrBelow.levelValue
    }

    public inline fun trace(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Trace, throwable, entryBuilder)
    }

    public inline fun debug(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Debug, throwable, entryBuilder)
    }

    public inline fun info(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Info, throwable, entryBuilder)
    }

    public inline fun warning(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Warn, throwable, entryBuilder)
    }

    public inline fun error(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Error, throwable, entryBuilder)
    }

    public inline fun logIfEnabled(level: LoggingLevel, throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        if (isLoggingEnabled(level, throwable)) {
            println(level.format(entryBuilder()) + (throwable?.let { "\n" + it.stackTraceToString() } ?: ""))
        }
    }

    @PublishedApi
    internal fun LoggingLevel.format(message: String): String {
        // TODO: Add current thread info.
        val levelPrefix = when (this) {
            LoggingLevel.Error -> "ERROR"
            LoggingLevel.Warn -> "WARN"
            LoggingLevel.Info -> "INFO"
            LoggingLevel.Debug -> "DEBUG"
            LoggingLevel.Trace -> "TRACE"
        }

        return "$levelPrefix\t@\t$ownerDescription\t: $message"
    }

    public companion object {
        private val mainLogger = Logger("o.b.h.l.Logger")

        public fun setLevel(level: LoggingLevel) {
            logLevelOrBelow = level
        }

        public fun disable() {
            logLevelOrBelow = null
        }

        public inline operator fun <reified T: Any> invoke(): Logger {
            return Logger(T::class)
        }

        public operator fun <T: Any> invoke(kclass: KClass<T>): Logger {
            val name = kclass.simpleName ?: run {
                mainLogger.error { "Couldn't get `simpleName` of class <$kclass> for a new logger. Using `toString` as name." }
                kclass.toString()
            }
            return Logger(name)
        }
    }
}


