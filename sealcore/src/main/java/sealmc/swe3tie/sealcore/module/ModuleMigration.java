package sealmc.swe3tie.sealcore.module;

import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A step that upgrades one module file to a newer {@code schema-version}.
 *
 * <p>Migrations run in ascending {@code fromVersion} order and only when the
 * file is behind the module, so they are safe to run on every boot. Unlike a
 * plain version stamp there is no wholesale overwrite, so keys an operator added
 * by hand survive an upgrade.
 */
public record ModuleMigration(String moduleId, int fromVersion, Consumer<ConfigurationSection> apply) {
}
