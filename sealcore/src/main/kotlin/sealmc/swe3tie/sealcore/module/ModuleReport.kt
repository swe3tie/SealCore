package sealmc.swe3tie.sealcore.module

/** Outcome of one module in a load pass. */
enum class ModuleState {
    /** Parsed, validated and applied. */
    ACTIVE,

    /** Switched off by `enabled: false`. */
    DISABLED,

    /** Config problem or an exception while parsing. The last good spec is kept. */
    FAILED,

    /** A dependency is not active, so this module was not applied. */
    SKIPPED,

    /** Not loaded yet. */
    PENDING,
}

/**
 * What happened to one module, for the log and `/sealcore debug modules`.
 *
 * [problems] names the file and key, so an operator can fix it without reading a
 * stack trace. [restartRequired] lists changed keys that only apply on a
 * restart, so a reload never quietly appears to do nothing.
 */
data class ModuleReport(
    val id: String,
    val file: String,
    val state: ModuleState,
    val schemaVersion: Int = 0,
    val problems: List<String> = emptyList(),
    val restartRequired: List<String> = emptyList(),
) {
    val isActive: Boolean get() = state == ModuleState.ACTIVE
}
