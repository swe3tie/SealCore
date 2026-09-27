package sealmc.swe3tie.sealcore.module;

import java.util.List;
import java.util.Set;

/**
 * A feature module inside the SealCore jar.
 *
 * <p>Every module owns exactly one file, {@code modules/<fileName()>}, and the
 * id is derived from that name: {@code jobs.miner} reads
 * {@code modules/jobs-miner.yml}. The same id namespaces the module's messages
 * and placeholders, so there is one name to remember and nothing to keep in
 * sync by hand.
 *
 * <p>A module is written in three steps, and the registry drives them in order:
 * <ol>
 *   <li>{@link #parse} turns the file into an immutable {@link ModuleSpec}. It
 *       must not touch live state, because the result may be thrown away.</li>
 *   <li>{@link #validate} reports anything the typed reader could not judge,
 *       such as two mutually exclusive settings both being on.</li>
 *   <li>{@link #enable} applies a spec that passed. It runs on the first load
 *       and again after every successful reload, so it has to be idempotent.</li>
 * </ol>
 *
 * <p>Registration order does not matter; the registry enables in dependency
 * order.
 */
public interface SealModule<C extends ModuleSpec> {

    /** Dotted id, for example {@code jobs.miner}. Also the message and placeholder namespace. */
    String id();

    /** Bumped when the file layout changes; drives {@link ModuleMigration} lookup. */
    default int schemaVersion() {
        return 1;
    }

    /** Ids that must be loaded first. A module whose dependency is off is skipped. */
    default Set<String> dependsOn() {
        return Set.of();
    }

    /**
     * Dotted paths inside this module's own file that only take effect on a
     * restart. A change to any of them is reported instead of silently ignored.
     */
    default Set<String> restartKeys() {
        return Set.of();
    }

    /** {@code modules/<fileName()>}, for example {@code modules/jobs-miner.yml}. */
    default String fileName() {
        return id().replace('.', '-') + ".yml";
    }

    /** The key that switches the module off, honoured before anything is applied. */
    default String enabledKey() {
        return "enabled";
    }

    C parse(ModuleSection section);

    /** Anything the reader cannot judge: cross field rules, required values. */
    default List<String> validate(C spec, ModuleSection section) {
        return List.of();
    }

    /** Applies a spec that loaded and validated. Called on boot and on every reload. */
    void enable(C spec);

    /** Tears down whatever {@link #enable} started. Only called on plugin disable. */
    default void disable() {
        // Nothing to undo by default.
    }

    /**
     * Commands this module owns. The framework binds them on boot and again on
     * every reload, and a command whose module is not active answers with
     * {@code core.module-disabled} instead of disappearing from the help.
     */
    default List<SealCommand> commands(ModuleContext context) {
        return List.of();
    }
}
