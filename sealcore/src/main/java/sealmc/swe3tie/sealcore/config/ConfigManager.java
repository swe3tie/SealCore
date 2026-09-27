package sealmc.swe3tie.sealcore.config;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import sealmc.swe3tie.sealcore.module.ModuleMigration;
import sealmc.swe3tie.sealcore.module.ModuleRegistry;
import sealmc.swe3tie.sealcore.module.ModuleReport;
import sealmc.swe3tie.sealcore.module.ModuleState;
import sealmc.swe3tie.sealcore.module.SealModule;

/**
 * Owner of everything an operator can edit: {@code config.yml}, the per module
 * files under {@code modules/} and the language files under
 * {@code languages/}.
 *
 * <p>The three live at different granularities on purpose. {@code config.yml}
 * holds only what two or more modules share plus the engine switches, a module
 * file holds that module's own settings, and a language file holds every string
 * keyed by module id. Translating therefore never touches a settings file.
 */
public final class ConfigManager {

    /**
     * {@code config.yml} keys that need a restart. A reload still reads them, so
     * the typed config always matches the file, but the change is reported
     * instead of quietly taking effect.
     */
    private static final List<String> RESTART_KEYS = List.of(
        "storage.type",
        "storage.pool-size",
        "storage.file-name",
        "gui.enabled",
        "gui.rows",
        "hud.enabled",
        "general.refresh-interval-ticks"
    );

    private final Plugin plugin;

    /** Engine and shared settings from {@code config.yml}. */
    private SealCoreConfig config;

    /** Strings for the active language, with English as fallback. */
    private Messages messages;

    /** Every registered feature module. */
    private final ModuleRegistry modules;

    private Map<String, String> restartSnapshot = new HashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.modules = new ModuleRegistry(plugin, plugin.getLogger());
    }

    public SealCoreConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ModuleRegistry modules() {
        return modules;
    }

    /** Reads {@code config.yml} and the active language, creating both on first run. */
    public void loadEngine() {
        saveDefault("config.yml");
        YamlConfiguration raw = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        config = SealCoreConfig.from(raw);
        messages = Messages.load(plugin, config.lang());
        restartSnapshot = snapshot(raw);
    }

    /** Registers modules. Call once, before {@link #loadModules()}, during enable. */
    public void registerModules(List<SealModule<?>> list) {
        for (SealModule<?> module : list) {
            modules.register(module);
        }
    }

    public ModuleMigration addMigration(ModuleMigration migration) {
        return modules.addMigration(migration);
    }

    /** First pass: read, validate and apply every module. */
    public List<ModuleReport> loadModules() {
        return modules.load();
    }

    /**
     * Reloads config, language and modules without a restart.
     *
     * <p>Module settings are applied per module, so a file that fails to load
     * leaves that module on its last good spec while everything else reloads.
     */
    public ReloadReport reload() {
        loadEngine();
        return new ReloadReport(modules.load(), restartPending());
    }

    /**
     * {@code config.yml} keys whose file value no longer matches what is
     * running. {@code /sealcore reload} reports these, so nothing looks like it
     * applied when it did not.
     */
    public List<String> restartPending() {
        YamlConfiguration raw = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        Map<String, String> current = snapshot(raw);
        return current.keySet().stream()
            .filter(key -> !Objects.equals(restartSnapshot.get(key), current.get(key)))
            .sorted()
            .toList();
    }

    private Map<String, String> snapshot(YamlConfiguration raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : RESTART_KEYS) {
            Object value = raw.get(key);
            map.put(key, value == null ? null : value.toString());
        }
        return map;
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (file.exists()) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
    }

    /** What one reload pass found, for the log and the command output. */
    public record ReloadReport(List<ModuleReport> modules, List<String> restartRequired) {

        public List<ModuleReport> broken() {
            return modules.stream()
                .filter(report -> report.state() == ModuleState.FAILED || report.state() == ModuleState.SKIPPED)
                .toList();
        }

        public boolean clean() {
            return broken().isEmpty() && restartRequired.isEmpty();
        }
    }
}
