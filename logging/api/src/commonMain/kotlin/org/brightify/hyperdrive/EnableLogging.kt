package org.brightify.hyperdrive

import kotlinx.coroutines.Dispatchers
import kotlin.native.concurrent.ThreadLocal
import kotlin.reflect.KClass

// TODO: Unused for now.
annotation class EnableLogging()

enum class LoggingLevel(val levelValue: Int) {
    Error(1),
    Warn(2),
    Info(3),
    Debug(4),
    Trace(5),
}

private var logLevelOrBelow: LoggingLevel? = LoggingLevel.Warn

class Logger private constructor(private val ownerDescription: String) {
    fun isLoggingEnabled(level: LoggingLevel): Boolean {
        val logLevelOrBelow = logLevelOrBelow ?: return false
        return level.levelValue <= logLevelOrBelow.levelValue
    }

    inline fun trace(crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Trace, null, entryBuilder)
    }

    inline fun debug(crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Debug, null, entryBuilder)
    }

    inline fun info(crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Info, null, entryBuilder)
    }

    inline fun warning(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Warn, null, entryBuilder)
    }

    inline fun error(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(LoggingLevel.Error, throwable, entryBuilder)
    }

    inline fun logIfEnabled(level: LoggingLevel, throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        if (isLoggingEnabled(level)) {
            println(level.format(entryBuilder()))
            throwable?.printStackTrace()
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

    companion object {
        private val mainLogger = Logger("o.b.h.l.Logger")

        fun setLevel(level: LoggingLevel) {
            logLevelOrBelow = level
        }

        fun disable() {
            logLevelOrBelow = null
        }

        inline operator fun <reified T: Any> invoke(): Logger {
            return Logger(T::class)
        }

        operator fun <T: Any> invoke(kclass: KClass<T>): Logger {
            val name = kclass.simpleName ?: run {
                mainLogger.error { "Couldn't get `simpleName` of class <$kclass> for a new logger. Using `toString` as name." }
                kclass.toString()
            }
            return Logger(name)
        }
    }
}


