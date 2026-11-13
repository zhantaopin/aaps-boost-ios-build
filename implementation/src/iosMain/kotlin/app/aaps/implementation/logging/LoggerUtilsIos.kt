package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.LogLevel
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * The iOS answer to [LoggerUtils].
 *
 * Android's implementation drives logback; iOS has no logback, and [AAPSLoggerIos] already writes
 * to NSLog plus a rotating file. So this only persists the level the maintenance screen shows and
 * changes - the log directory comes from the same Documents folder the logger writes into.
 */
@OptIn(ExperimentalForeignApi::class)
class LoggerUtilsIos(private val preferences: Preferences) : LoggerUtils {

    override var suffix = ".log"

    override val logDirectory: String
        get() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""

    override fun initializeLogLevel() = Unit

    override fun logLevel(): LogLevel = LogLevel.fromStored(preferences.get(IntNonKey.LogLevel))

    override fun setLogLevel(level: LogLevel) {
        preferences.put(IntNonKey.LogLevel, level.ordinal)
    }
}
