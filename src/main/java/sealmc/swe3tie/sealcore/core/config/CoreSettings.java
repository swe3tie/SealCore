package sealmc.swe3tie.sealcore.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable snapshot of {@code config.yml}, rebuilt on every load. */
public record CoreSettings(
    @NotNull String lang,
    boolean debug,
    @NotNull StorageSettings storage,
    @NotNull EconomySettings economy,
    boolean parsePlaceholdersInMessages,
    boolean packetsEnabled,
    @NotNull Map<String, Boolean> moduleToggles
) {

    public record StorageSettings(
        @NotNull String type,
        @NotNull String file,
        @NotNull String host,
        int port,
        @NotNull String database,
        @NotNull String username,
        @NotNull String password,
        boolean useSsl,
        @NotNull String tablePrefix,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long maxLifetimeMs
    ) {
        public boolean isMySql() {
            return "mysql".equals(this.type) || "mariadb".equals(this.type);
        }
    }

    public record EconomySettings(boolean required, @NotNull Map<String, String> currencies, @NotNull String defaultCurrency) {
    }

    public boolean isModuleEnabled(@NotNull String id) {
        return this.moduleToggles.getOrDefault(id, false);
    }

    @NotNull
    public static CoreSettings from(@NotNull YamlConfiguration c) {
        String lang = c.getString("lang", "en.yml");
        if (!lang.endsWith(".yml")) lang += ".yml";

        StorageSettings storage = new StorageSettings(
            c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT),
            c.getString("storage.file", "sealcore.db"),
            c.getString("storage.host", "localhost"),
            c.getInt("storage.port", 3306),
            c.getString("storage.database", "sealcore"),
            c.getString("storage.username", "root"),
            c.getString("storage.password", ""),
            c.getBoolean("storage.use-ssl", false),
            c.getString("storage.table-prefix", "sealcore_"),
            Math.max(1, c.getInt("storage.pool.maximum-size", 10)),
            Math.max(0, c.getInt("storage.pool.minimum-idle", 2)),
            c.getLong("storage.pool.connection-timeout-ms", 10000L),
            c.getLong("storage.pool.max-lifetime-ms", 1800000L)
        );

        Map<String, String> currencies = new LinkedHashMap<>();
        ConfigurationSection currencySection = c.getConfigurationSection("hooks.excellent-economy.currencies");
        if (currencySection != null) {
            for (String key : currencySection.getKeys(false)) {
                String target = currencySection.getString(key);
                if (target != null && !target.isBlank()) currencies.put(key.toLowerCase(Locale.ROOT), target);
            }
        }
        EconomySettings economy = new EconomySettings(
            c.getBoolean("hooks.excellent-economy.required", false),
            Collections.unmodifiableMap(currencies),
            c.getString("hooks.excellent-economy.default-currency", "money").toLowerCase(Locale.ROOT)
        );

        Map<String, Boolean> toggles = new LinkedHashMap<>();
        ConfigurationSection modules = c.getConfigurationSection("modules");
        if (modules != null) {
            for (String id : modules.getKeys(false)) toggles.put(id.toLowerCase(Locale.ROOT), modules.getBoolean(id));
        }

        return new CoreSettings(
            lang,
            c.getBoolean("general.debug", false),
            storage,
            economy,
            c.getBoolean("hooks.placeholderapi.parse-in-messages", true),
            c.getBoolean("hooks.packetevents.enabled", true),
            Collections.unmodifiableMap(toggles)
        );
    }
}
