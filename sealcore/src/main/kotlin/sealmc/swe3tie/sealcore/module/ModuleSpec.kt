package sealmc.swe3tie.sealcore.module

/**
 * A module's parsed configuration.
 *
 * Specs are immutable value objects. The registry stages one per module, keeps
 * the last good one, and only hands it to the module once every module has
 * loaded and validated. That is what makes a bad config in one module leave the
 * running server on its previous settings instead of half applied.
 */
abstract class ModuleSpec {

    /** One line summary for `/sealcore debug modules`. */
    abstract fun describe(): String
}

/**
 * A step that upgrades one module file to a newer `schema-version`.
 *
 * Migrations run in ascending `fromVersion` order and only when the file is
 * behind the module, so they are safe to run on every boot. Unlike a plain
 * version stamp there is no wholesale overwrite, so keys an operator added by
 * hand survive an upgrade.
 */
class ModuleMigration(
    val moduleId: String,
    val fromVersion: Int,
    val apply: (org.bukkit.configuration.ConfigurationSection) -> Unit,
)
