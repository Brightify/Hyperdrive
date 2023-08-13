package org.brightify.hyperdrive

import org.brightify.hyperdrive.utils.AtomicReference
import kotlin.reflect.KClass

public enum class LoggingLevel(public val levelValue: Int) {
    Error(1),
    Warn(2),
    Info(3),
    Debug(4),
    Trace(5),
}

public class Logger private constructor(
    private val tag: String,
) {
    public fun isLoggingEnabled(level: LoggingLevel, throwable: Throwable? = null): Boolean {
        val logLevelOrBelow = configuration.minLogLevel ?: return false
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
            log(level, throwable, entryBuilder())
        }
    }

    @PublishedApi
    internal fun log(level: LoggingLevel, throwable: Throwable?, entry: String) {
        for (destination in configuration.destinations) {
            destination.log(level, throwable, tag, entry)
        }
    }

    public class Configuration(
        public val minLogLevel: LoggingLevel?,
        public val destinations: List<Destination>,
    ) {

        public class Builder {
            private var minLogLevel: LoggingLevel? = LoggingLevel.Warn
            private val destinations = mutableListOf<Destination>()

            public fun setMinLevel(level: LoggingLevel) {
                minLogLevel = level
            }

            public fun disable() {
                minLogLevel = null
            }

            public fun destination(destination: Destination) {
                destinations.add(destination)
            }

            public fun clearDestinations() {
                destinations.clear()
            }

            public fun build(): Configuration = Configuration(
                minLogLevel = minLogLevel,
                destinations = destinations,
            )
        }
    }

    public companion object {
        private val configurationReference = AtomicReference(Configuration.Builder().apply { destination(PrintlnDestination()) }.build())
        private val mainLogger = Logger("o.b.h.l.Logger")

        public val configuration: Configuration
            get() = configurationReference.value

        public fun configure(block: Configuration.Builder.() -> Unit) {
            val builder = Configuration.Builder()
            block(builder)
            configurationReference.value = builder.build()
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

        public operator fun invoke(tag: String): Logger {
            return Logger(tag)
        }
    }

    public interface Destination {
        public fun log(level: LoggingLevel, throwable: Throwable? = null, tag: String, message: String)
    }
}

public class PrintlnDestination: Logger.Destination {
    override fun log(level: LoggingLevel, throwable: Throwable?, tag: String, message: String) {
        println(level.format(tag, message) + (throwable?.let { "\n" + it.stackTraceToString() } ?: ""))
    }

    private fun LoggingLevel.format(tag: String, message: String): String {
        // TODO: Add current thread info.
        val levelPrefix = when (this) {
            LoggingLevel.Error -> "ERROR"
            LoggingLevel.Warn -> "WARN"
            LoggingLevel.Info -> "INFO"
            LoggingLevel.Debug -> "DEBUG"
            LoggingLevel.Trace -> "TRACE"
        }

        return "$levelPrefix\t@\t$tag\t: $message"
    }
}
