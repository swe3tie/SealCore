package sealmc.swe3tie.sealcore.core.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns {@code config.yml} and creates the per-module files under {@code modules/}.
 */
public final class ConfigService {

    private final JavaPlugin plugin;
    private final YamlFile mainFile;
    private volatile CoreSettings settings;

    public ConfigService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        // The currency map is user owned: removing an entry must not bring it back.
        this.mainFile = new YamlFile(
            plugin.getDataFolder().toPath().resolve("config.yml"),
            resource(plugin, "config.yml"),
            List.of("hooks.excellent-economy.currencies")
        );
    }

    /** Loads config.yml. On failure the previous settings stay active. */
    public void load() throws ConfigException {
        this.settings = CoreSettings.from(this.mainFile.load());
    }

    @NotNull
    public CoreSettings settings() {
        CoreSettings current = this.settings;
        if (current == null) throw new IllegalStateException("config.yml not loaded yet");
        return current;
    }

    /** File handle for {@code modules/<id>.yml}, merged against the bundled default if one exists. */
    @NotNull
    public YamlFile moduleFile(@NotNull String moduleId, @NotNull Collection<String> freeSections) {
        String name = "modules/" + moduleId + ".yml";
        return new YamlFile(this.plugin.getDataFolder().toPath().resolve(name), resource(this.plugin, name), freeSections);
    }

    @NotNull
    public Path dataFolder() {
        return this.plugin.getDataFolder().toPath();
    }

    @NotNull
    public static Supplier<InputStream> resource(@NotNull JavaPlugin plugin, @NotNull String name) {
        return () -> plugin.getResource(name);
    }
}
