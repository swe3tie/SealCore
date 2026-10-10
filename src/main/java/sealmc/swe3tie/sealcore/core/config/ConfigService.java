package sealmc.swe3tie.sealcore.core.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
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

    /**
     * File handle for a module config. Directory-based configs are preferred, while the legacy
     * {@code modules/<id>.yml} layout remains supported for modules that have not migrated yet.
     */
    @NotNull
    public YamlFile moduleFile(@NotNull String moduleId, @NotNull Collection<String> freeSections) {
        String directoryName = "modules/" + moduleId + "/config.yml";
        Path directoryPath = this.plugin.getDataFolder().toPath().resolve(directoryName);
        if (this.plugin.getResource(directoryName) != null || Files.exists(directoryPath)) {
            return new YamlFile(directoryPath, resource(this.plugin, directoryName), freeSections);
        }

        String legacyName = "modules/" + moduleId + ".yml";
        return new YamlFile(
            this.plugin.getDataFolder().toPath().resolve(legacyName),
            resource(this.plugin, legacyName),
            freeSections
        );
    }

    /** File handle for a module-owned resource such as {@code prices.yml} or {@code gui/sell.yml}. */
    @NotNull
    public YamlFile moduleCustomFile(@NotNull String moduleId, @NotNull String relativePath) {
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("\\")) {
            throw new IllegalArgumentException("Module resource path must be relative: " + relativePath);
        }
        Path path = Path.of(relativePath).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("Module resource path escapes module directory: " + relativePath);
        }

        String name = "modules/" + moduleId + "/" + path.toString().replace(java.io.File.separatorChar, '/');
        return new YamlFile(this.plugin.getDataFolder().toPath().resolve(name), resource(this.plugin, name));
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
