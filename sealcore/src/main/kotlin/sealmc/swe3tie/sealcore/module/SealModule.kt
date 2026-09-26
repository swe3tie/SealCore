package sealmc.swe3tie.sealcore.module

/**
 * A feature module inside the SealCore jar.
 *
 * Every module owns exactly one file, `modules/<fileName()>`, and the id is
 * derived from that name: `jobs.miner` reads `modules/jobs-miner.yml`. The same
 * id namespaces the module's messages and placeholders, so there is one name to
 * remember and nothing to keep in sync by hand.
 *
 * A module is written in three steps, and the registry drives them in order:
 *
 * 1. [parse] turns the file into an immutable [ModuleSpec]. It must not touch
 *    live state, because the result may be thrown away.
 * 2. [validate] reports anything the typed reader could not judge, such as two
 *    mutually exclusive settings both being on.
 * 3. [enable] applies a spec that passed. It runs on the first load and again
 *    after every successful reload, so it has to be idempotent.
 *
 * Registration order does not matter; the registry enables in dependency order.
 */
interface SealModule<C : ModuleSpec> {

    /** Dotted id, for example `jobs.miner`. Also the message and placeholder namespace. */
    val id: String

    /** Bumped when the file layout changes; drives [ModuleMigration] lookup. */
    val schemaVersion: Int get() = 1

    /** Ids that must be loaded first. A module whose dependency is off is skipped. */
    val dependsOn: Set<String> get() = emptySet()

    /**
     * Dotted paths inside this module's own file that only take effect on a
     * restart. A change to any of them is reported instead of silently ignored.
     */
    val restartKeys: Set<String> get() = emptySet()

    /** `modules/<fileName()>`, for example `modules/jobs-miner.yml`. */
    fun fileName(): String = id.replace('.', '-') + ".yml"

    /** The key that switches the module off, honoured before anything is applied. */
    fun enabledKey(): String = "enabled"

    fun parse(section: ModuleSection): C

    /** Anything the reader cannot judge: cross field rules, required values. */
    fun validate(spec: C, section: ModuleSection): List<String> = emptyList()

    /** Applies a spec that loaded and validated. Called on boot and on every reload. */
    fun enable(spec: C)

    /** Tears down whatever [enable] started. Only called on plugin disable. */
    fun disable() = Unit
}
