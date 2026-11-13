package app.aaps.core.interfaces.overview

/**
 * Boost V5 meal-hypothesis state machine. Replaces the tier model for the V5 engine.
 * [verb] is the one-word action shown on the chip; [colorHex] the chip colour
 * (grey idle -> amber watching -> orange-red acting -> orange covering -> teal easing).
 * Values copied verbatim from the live BoostOverviewHelper source.
 *
 * [zhLabel] is the short Chinese name shown next to [name] on the overview strip, where the state is
 * read at a glance and the engine identifier alone means nothing to the user. It is a literal rather
 * than a resource because this enum is in commonMain and the string travels with the state, not with
 * a screen. Untranslated on purpose: the pairing is `空闲（IDLE）`, so the English name stays visible
 * for matching against the script log.
 */
enum class BoostV5State(val label: String, val verb: String, val short: String, val colorHex: Long, val zhLabel: String) {
    IDLE("Idle", "idle", "IDLE", 0xFF78909C, "空闲"),                  // blue-grey
    OBSERVING("Observing", "watching", "OBSERVE", 0xFFFFC107, "观察中"), // amber
    CONFIRMED("Confirmed", "acting", "CONFIRM", 0xFFFF6E40, "加量中"),   // orange-red
    COMMITTED("Committed", "covering", "COMMIT", 0xFFFF9800, "覆盖中"),  // orange
    RECOVERING("Recovering", "easing", "RECOVER", 0xFF26C6DA, "收尾中"); // teal

    companion object {

        fun fromName(text: String?): BoostV5State =
            entries.firstOrNull { it.name.equals(text?.trim(), ignoreCase = true) } ?: IDLE
    }
}
