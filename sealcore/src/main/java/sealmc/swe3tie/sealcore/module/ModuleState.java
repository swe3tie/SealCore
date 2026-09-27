package sealmc.swe3tie.sealcore.module;

/** Outcome of one module in a load pass. */
public enum ModuleState {
    /** Parsed, validated and applied. */
    ACTIVE,

    /** Switched off by {@code enabled: false}. */
    DISABLED,

    /** Config problem or an exception while parsing. The last good spec is kept. */
    FAILED,

    /** A dependency is not active, so this module was not applied. */
    SKIPPED,

    /** Not loaded yet. */
    PENDING
}
