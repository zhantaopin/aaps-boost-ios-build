package app.aaps.core.interfaces.logging

/**
 * How much the app writes to its log.
 *
 * The levels are ordered from quietest to loudest and are stored by ordinal in
 * [app.aaps.core.keys.IntNonKey.LogLevel], so never renumber them.
 */
enum class LogLevel {
    /** Nothing at all: no log file writes and no logcat. */
    OFF,

    /** Warnings and errors only. */
    WARN,

    /** The normal operation log: info, warnings and errors. The default. */
    INFO,

    /** Everything, including per-tag debug lines. */
    DEBUG;

    companion object {
        fun fromStored(ordinal: Int?): LogLevel = entries.getOrNull(ordinal ?: INFO.ordinal) ?: INFO
    }
}
