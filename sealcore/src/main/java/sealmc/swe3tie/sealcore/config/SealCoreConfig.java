package sealmc.swe3tie.sealcore.config;

import java.time.Duration;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view over {@code config.yml}.
 *
 * <p>Every value has a default, so a partial or missing file still yields a
 * usable configuration. Unknown keys are left alone on save so hand-written
 * additions survive a reload.
 */
public final class SealCoreConfig {

    private final String lang;
    private final General general;
    private final Economy economy;
    private final Storage storage;
    private final Gui gui;
    private final Hud hud;
    private final boolean debug;

    public SealCoreConfig(String lang, General general, Economy economy, Storage storage, Gui gui, Hud hud, boolean debug) {
        this.lang = lang;
        this.general = general;
        this.economy = economy;
        this.storage = storage;
        this.gui = gui;
        this.hud = hud;
        this.debug = debug;
    }

    public SealCoreConfig() {
        this("en.yml", new General(), new Economy(), new Storage(), new Gui(), new Hud(), false);
    }

    /** Language file name under {@code languages/}, for example {@code en.yml}. */
    public String lang() {
        return lang;
    }

    public General general() {
        return general;
    }

    public Economy economy() {
        return economy;
    }

    public Storage storage() {
        return storage;
    }

    public Gui gui() {
        return gui;
    }

    public Hud hud() {
        return hud;
    }

    public boolean debug() {
        return debug;
    }

    public Duration refreshInterval() {
        return Duration.ofMillis(general.refreshIntervalTicks() * 50L);
    }

    public record General(long refreshIntervalTicks, String accentColor) {

        public General() {
            this(20L, "#9CC0D9");
        }
    }

    /**
     * Maps SealCore currency keys onto ExcellentEconomy currency ids, so the
     * rest of the plugin never hardcodes a provider specific id.
     */
    public record Economy(String money, String shards, String coins, String defaultCurrency, boolean required) {

        public Economy() {
            this("money", "shards", "coins", "money", false);
        }

        public String currencyId(String key) {
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "money" -> money;
                case "shards" -> shards;
                case "coins" -> coins;
                default -> key;
            };
        }
    }

    public record Storage(
        Type type,
        String fileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean useSsl,
        int poolSize,
        long connectionTimeoutMillis
    ) {

        public enum Type {
            SQLITE,
            MYSQL
        }

        public Storage() {
            this(Type.SQLITE, "sealcore.db", "localhost", 3306, "sealcore", "root", "", false, 10, 10_000L);
        }

        /** The two settings that identify a store; the rest keep their defaults. */
        public Storage(Type type, String fileName) {
            this(type, fileName, "localhost", 3306, "sealcore", "root", "", false, 10, 10_000L);
        }
    }

    public record Gui(boolean enabled, int rows, long animationTicks, boolean asyncClickHandling) {

        public Gui() {
            this(true, 6, 0L, true);
        }
    }

    public record Hud(boolean enabled, long updateIntervalTicks, Sidebar sidebar, BossBar bossBar) {

        public Hud() {
            this(true, 20L, new Sidebar(), new BossBar());
        }

        public record Sidebar(boolean enabled, String title) {

            public Sidebar() {
                this(false, "<gradient:#00c6ff:#0072ff><bold>SealCore</bold></gradient>");
            }
        }

        public record BossBar(boolean enabled, String title) {

            public BossBar() {
                this(false, "<white>Welcome back!");
            }
        }
    }

    public static SealCoreConfig from(FileConfiguration raw) {
        ConfigurationSection generalRaw = raw.getConfigurationSection("general");
        ConfigurationSection economyRaw = raw.getConfigurationSection("economy");
        ConfigurationSection storageRaw = raw.getConfigurationSection("storage");
        ConfigurationSection guiRaw = raw.getConfigurationSection("gui");
        ConfigurationSection hudRaw = raw.getConfigurationSection("hud");

        return new SealCoreConfig(
            text(raw.getString("lang"), "en.yml", true),
            new General(
                generalRaw == null ? 20L : generalRaw.getLong("refresh-interval-ticks", 20L),
                text(generalRaw == null ? null : generalRaw.getString("accent-color"), "#9CC0D9", true)),
            new Economy(
                text(economyRaw == null ? null : economyRaw.getString("currencies.money"), "money", false),
                text(economyRaw == null ? null : economyRaw.getString("currencies.shards"), "shards", false),
                text(economyRaw == null ? null : economyRaw.getString("currencies.coins"), "coins", false),
                text(economyRaw == null ? null : economyRaw.getString("default-currency"), "money", false),
                economyRaw != null && economyRaw.getBoolean("required", false)),
            new Storage(
                storageType(storageRaw),
                text(storageRaw == null ? null : storageRaw.getString("file-name"), "sealcore.db", false),
                text(storageRaw == null ? null : storageRaw.getString("host"), "localhost", false),
                storageRaw == null ? 3306 : storageRaw.getInt("port", 3306),
                text(storageRaw == null ? null : storageRaw.getString("database"), "sealcore", false),
                text(storageRaw == null ? null : storageRaw.getString("username"), "root", false),
                text(storageRaw == null ? null : storageRaw.getString("password"), "", false),
                storageRaw != null && storageRaw.getBoolean("use-ssl", false),
                storageRaw == null ? 10 : storageRaw.getInt("pool-size", 10),
                storageRaw == null ? 10_000L : storageRaw.getLong("connection-timeout-ms", 10_000L)),
            new Gui(
                guiRaw == null || guiRaw.getBoolean("enabled", true),
                Math.max(1, Math.min(6, guiRaw == null ? 6 : guiRaw.getInt("rows", 6))),
                guiRaw == null ? 0L : guiRaw.getLong("animation-ticks", 0L),
                guiRaw == null || guiRaw.getBoolean("async-click-handling", true)),
            new Hud(
                hudRaw == null || hudRaw.getBoolean("enabled", true),
                hudRaw == null ? 20L : hudRaw.getLong("update-interval-ticks", 20L),
                new Hud.Sidebar(
                    hudRaw == null ? false : hudRaw.getBoolean("sidebar.enabled", false),
                    text(hudRaw == null ? null : hudRaw.getString("sidebar.title"), new Hud.Sidebar().title(), false)),
                new Hud.BossBar(
                    hudRaw == null ? false : hudRaw.getBoolean("bossbar.enabled", false),
                    text(hudRaw == null ? null : hudRaw.getString("bossbar.title"), new Hud.BossBar().title(), false))),
            raw.getBoolean("debug", false));
    }

    private static String text(String value, String fallback, boolean trim) {
        if (value == null) {
            return fallback;
        }
        String result = trim ? value.trim() : value;
        return result.isEmpty() ? fallback : result;
    }

    private static Storage.Type storageType(ConfigurationSection section) {
        String raw = text(section == null ? null : section.getString("type"), "sqlite", true).toUpperCase(Locale.ROOT);
        try {
            return Storage.Type.valueOf(raw);
        } catch (IllegalArgumentException unknown) {
            return Storage.Type.SQLITE;
        }
    }
}
