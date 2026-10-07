package sealmc.swe3tie.sealcore.core.module;

/** Lifecycle state of a module, shown by {@code /sealcore module list}. */
public enum ModuleState {
    /** Never started (toggled off in config.yml, or not yet enabled). */
    DISABLED("<gray>"),
    ENABLED("<green>"),
    /** A required external plugin is missing. */
    MISSING_HOOK("<yellow>"),
    /** A required module is disabled or failed. */
    MISSING_DEPENDENCY("<yellow>"),
    /** Threw while enabling or reloading; the rest of SealCore keeps running. */
    FAILED("<red>");

    private final String tag;

    ModuleState(String tag) {
        this.tag = tag;
    }

    /** MiniMessage colour tag for this state. */
    public String tag() {
        return this.tag;
    }
}
