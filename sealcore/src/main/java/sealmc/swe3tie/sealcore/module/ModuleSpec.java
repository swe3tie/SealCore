package sealmc.swe3tie.sealcore.module;


/**
 * A module's parsed configuration.
 *
 * <p>Specs are immutable value objects. The registry stages one per module,
 * keeps the last good one, and only hands it to the module once every module has
 * loaded and validated. That is what makes a bad config in one module leave the
 * running server on its previous settings instead of half applied.
 */
public interface ModuleSpec {

    /** One line summary for {@code /sealcore debug modules}. */
    String describe();
}
