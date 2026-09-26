package sealmc.swe3tie.sealcore.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sealmc.swe3tie.sealcore.module.ModuleMigration
import sealmc.swe3tie.sealcore.module.ModuleRegistry
import sealmc.swe3tie.sealcore.module.ModuleReport
import sealmc.swe3tie.sealcore.module.ModuleState
import sealmc.swe3tie.sealcore.module.SealModule
import java.io.File

/**
 * Owner of everything an operator can edit: `config.yml`, the per module files
 * under `modules/` and the language files under `languages/`.
 *
 * The three live at different granularities on purpose. `config.yml` holds only
 * what two or more modules share plus the engine switches, a module file holds
 * that module's own settings, and a language file holds every string keyed by
 * module id. Translating therefore never touches a settings file.
 */
class ConfigManager(private val plugin: Plugin) {

    /** Engine and shared settings from `config.yml`. */
    lateinit var config: SealCoreConfig
        private set

    /** Strings for the active language, with English as fallback. */
    lateinit var messages: Messages
        private set

    /** Every registered feature module. */
    val modules = ModuleRegistry(plugin, plugin.logger)

    private var restartSnapshot: Map<String, String?> = emptyMap()

    /**
     * `config.yml` keys that need a restart. A reload still reads them, so the
     * typed config always matches the file, but the change is reported instead
     * of quietly taking effect.
     */
    private val restartKeys = listOf(
        "storage.type",
        "storage.pool-size",
        "storage.file-name",
        "gui.enabled",
        "gui.rows",
        "hud.enabled",
        "general.refresh-interval-ticks",
    )

    /** Reads `config.yml` and the active language, creating both on first run. */
    fun loadEngine() {
        saveDefault("config.yml")
        val raw = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))
        config = SealCoreConfig.from(raw)
        messages = Messages.load(plugin, config.lang)
        restartSnapshot = snapshot(raw)
    }

    /** Registers modules. Call once, before [loadModules], during enable. */
    fun registerModules(list: List<SealModule<*>>) {
        list.forEach { modules.register(it) }
    }

    fun addMigration(migration: ModuleMigration): ModuleMigration = modules.addMigration(migration)

    /** First pass: read, validate and apply every module. */
    fun loadModules(): List<ModuleReport> = modules.load()

    /**
     * Reloads config, language and modules without a restart.
     *
     * Module settings are applied per module, so a file that fails to load
     * leaves that module on its last good spec while everything else reloads.
     */
    fun reload(): ReloadReport {
        loadEngine()
        val reports = modules.load()
        return ReloadReport(reports, restartPending())
    }

    /**
     * `config.yml` keys whose file value no longer matches what is running.
     * `/sealcore reload` reports these, so nothing looks like it applied when
     * it did not.
     */
    fun restartPending(): List<String> {
        val raw = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))
        val current = snapshot(raw)
        return current.keys.filter { restartSnapshot[it] != current[it] }.sorted()
    }

    private fun snapshot(raw: YamlConfiguration): Map<String, String?> =
        LinkedHashMap<String, String?>().also { map ->
            for (key in restartKeys) map[key] = raw.get(key)?.toString()
        }

    private fun saveDefault(name: String) {
        val file = File(plugin.dataFolder, name)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        if (plugin.getResource(name) != null) plugin.saveResource(name, false)
    }

    /** What one reload pass found, for the log and the command output. */
    data class ReloadReport(
        val modules: List<ModuleReport>,
        val restartRequired: List<String>,
    ) {
        val broken: List<ModuleReport> get() = modules.filter { it.state == ModuleState.FAILED || it.state == ModuleState.SKIPPED }
        val clean: Boolean get() = broken.isEmpty() && restartRequired.isEmpty()
    }
}
