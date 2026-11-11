package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.LogLevel
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import org.slf4j.LoggerFactory

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class LoggerUtilsImpl(private val preferences: Preferences) : LoggerUtils {

    override var suffix = ".log"

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return path
     */
    override val logDirectory: String
        get() = (LoggerFactory.getILoggerFactory() as LoggerContext).getProperty("EXT_FILES_DIR")

    override fun logLevel(): LogLevel = LogLevel.fromStored(preferences.get(IntNonKey.LogLevel))

    override fun initializeLogLevel() = applyLevel(logLevel())

    override fun setLogLevel(level: LogLevel) {
        preferences.put(IntNonKey.LogLevel, level.ordinal)
        applyLevel(level)
    }

    /**
     * Sets the level on the root logger, which is the single gate every appender sits behind. OFF
     * is logback's own OFF level: no event reaches the file appender or logcat, so it really is
     * silent, not merely quiet. The level object is shared, so a change here takes effect for the
     * next log call without a restart.
     */
    private fun applyLevel(level: LogLevel) {
        val root = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)
        root.level = when (level) {
            LogLevel.OFF   -> Level.OFF
            LogLevel.WARN  -> Level.WARN
            LogLevel.INFO  -> Level.INFO
            LogLevel.DEBUG -> Level.DEBUG
        }
    }
}
