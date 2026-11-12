package app.aaps.core.interfaces.logging

interface LoggerUtils {

    var suffix: String
    val logDirectory: String

    /**
     * Applies the stored [LogLevel] to the logging backend.
     *
     * Called once during startup, before the first log line, so a level the user picked earlier is
     * in force from the start - switching to [LogLevel.OFF] has to mean no output after a restart,
     * not only until the next one.
     */
    fun initializeLogLevel()

    fun logLevel(): LogLevel

    /** Persists [level] and applies it immediately. */
    fun setLogLevel(level: LogLevel)
}
