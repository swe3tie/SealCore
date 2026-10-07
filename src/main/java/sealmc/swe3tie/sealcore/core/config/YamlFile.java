package sealmc.swe3tie.sealcore.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A YAML file on disk backed by an optional default resource from the jar.
 * <p>
 * On every {@link #load()} the file is created from the default if missing, and any key present in
 * the default but missing on disk is copied over with its comments. User values are never
 * overwritten. Paths listed as <em>free sections</em> (for example a user editable map of
 * currencies) are only seeded when the section itself is absent, so deleted entries stay deleted.
 */
public final class YamlFile {

    public static final String VERSION_KEY = "config-version";

    private final Path path;
    private final Supplier<InputStream> defaults;
    private final Set<String> freeSections;

    private volatile YamlConfiguration config = new YamlConfiguration();

    public YamlFile(@NotNull Path path, @Nullable Supplier<InputStream> defaults, @NotNull Collection<String> freeSections) {
        this.path = path;
        this.defaults = defaults;
        this.freeSections = Set.copyOf(freeSections);
    }

    public YamlFile(@NotNull Path path, @Nullable Supplier<InputStream> defaults) {
        this(path, defaults, List.of());
    }

    /**
     * Loads (and creates/merges) the file.
     *
     * @throws ConfigException when the file on disk is not valid YAML or cannot be written
     */
    @NotNull
    public YamlConfiguration load() throws ConfigException {
        YamlConfiguration defaultConfig = this.loadDefaults();
        YamlConfiguration loaded = new YamlConfiguration();

        try {
            if (Files.exists(this.path)) {
                loaded.loadFromString(Files.readString(this.path, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            throw new ConfigException("Invalid YAML in " + this.path.getFileName() + ": " + e.getMessage(), e);
        }

        boolean changed = !Files.exists(this.path) && defaultConfig != null;
        if (defaultConfig != null) {
            changed |= mergeDefaults(loaded, defaultConfig, this.freeSections);
        }

        if (changed) {
            try {
                Files.createDirectories(this.path.getParent());
                Files.writeString(this.path, loaded.saveToString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ConfigException("Could not write " + this.path.getFileName() + ": " + e.getMessage(), e);
            }
        }

        this.config = loaded;
        return loaded;
    }

    @NotNull
    public YamlConfiguration config() {
        return this.config;
    }

    @NotNull
    public Path path() {
        return this.path;
    }

    @Nullable
    private YamlConfiguration loadDefaults() throws ConfigException {
        if (this.defaults == null) return null;
        try (InputStream in = this.defaults.get()) {
            if (in == null) return null;
            YamlConfiguration def = new YamlConfiguration();
            def.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return def;
        } catch (IOException | InvalidConfigurationException e) {
            throw new ConfigException("Broken default resource for " + this.path.getFileName(), e);
        }
    }

    /**
     * Copies every key from {@code defaults} that {@code target} lacks, including comments.
     *
     * @return {@code true} if {@code target} was modified
     */
    public static boolean mergeDefaults(@NotNull YamlConfiguration target, @NotNull YamlConfiguration defaults,
                                        @NotNull Set<String> freeSections) {
        boolean changed = false;

        List<String> header = target.options().getHeader();
        if (header.isEmpty() && !defaults.options().getHeader().isEmpty()) {
            target.options().setHeader(defaults.options().getHeader());
            changed = true;
        }

        // Decide up front which free sections the user already has. Checking inside the loop would
        // see the empty section we just created for the first child and skip the rest.
        Set<String> userOwned = new java.util.HashSet<>();
        for (String free : freeSections) {
            if (target.contains(free, true)) userOwned.add(free);
        }

        for (String key : defaults.getKeys(true)) {
            if (isInsideFreeSection(key, userOwned)) continue;
            if (target.contains(key, true)) continue;
            // Parent replaced by a scalar on purpose by the user: leave it alone.
            if (hasScalarParent(target, key)) continue;

            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection) {
                target.createSection(key);
            } else {
                target.set(key, value);
            }
            target.setComments(key, defaults.getComments(key));
            target.setInlineComments(key, defaults.getInlineComments(key));
            changed = true;
        }
        return changed;
    }

    private static boolean isInsideFreeSection(String key, Set<String> userOwned) {
        for (String free : userOwned) {
            if (key.startsWith(free + ".")) return true;
        }
        return false;
    }

    private static boolean hasScalarParent(YamlConfiguration target, String key) {
        int idx = key.lastIndexOf('.');
        while (idx > 0) {
            String parent = key.substring(0, idx);
            if (target.contains(parent, true) && !target.isConfigurationSection(parent)) return true;
            idx = parent.lastIndexOf('.');
        }
        return false;
    }
}
