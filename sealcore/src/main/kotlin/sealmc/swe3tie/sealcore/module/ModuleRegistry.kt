package sealmc.swe3tie.sealcore.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns every [SealModule]: its file, its staged spec and its last good spec.
 *
 * A load pass is two phase on purpose. Phase one reads, migrates, parses and
 * validates every module without touching live state. Phase two applies the
 * specs that passed, in dependency order. A typo in one file therefore leaves
 * the running server on its previous settings for that module while everything
 * else reloads, instead of a half applied reload.
 */
class ModuleRegistry(
    private val plugin: Plugin,
    private val logger: Logger,
) {

    private class Entry(val module: SealModule<*>) {
        var report: ModuleReport = ModuleReport(
            id = module.id,
            file = "modules/${module.fileName()}",
            state = ModuleState.PENDING,
        )
        var staged: ModuleSpec? = null
        var stagedSection: ModuleSection? = null
        var applied: ModuleSpec? = null
        var restartSnapshot: Map<String, String?> = emptyMap()
    }

    private val entries = LinkedHashMap<String, Entry>()
    private val migrations = LinkedHashMap<String, MutableList<ModuleMigration>>()

    /** Migrations available for one module, applied when its file is behind. */
    fun addMigration(migration: ModuleMigration): ModuleMigration {
        migrations.getOrPut(migration.moduleId) { mutableListOf() }.add(migration)
        return migration
    }

    fun register(module: SealModule<*>): SealModule<*> {
        require(entries[module.id] == null) { "module '${module.id}' is already registered" }
        entries[module.id] = Entry(module)
        return module
    }

    fun ids(): List<String> = entries.keys.toList()

    fun report(id: String): ModuleReport? = entries[id]?.report

    fun reports(): List<ModuleReport> = entries.values.map { it.report }

    fun activeCount(): Int = entries.values.count { it.report.isActive }

    fun isRegistered(id: String): Boolean = entries.containsKey(id)

    /** The spec currently applied for [id], or null when the module is not active. */
    @Suppress("UNCHECKED_CAST")
    fun <C : ModuleSpec> specOf(id: String): C? = entries[id]?.applied as C?

    /**
     * Reads, migrates, parses and validates everything, then applies what
     * passed. Safe to call again after a reload: a module that fails keeps the
     * spec it already had.
     */
    fun load(): List<ModuleReport> {
        val order = dependencyOrder()
        for (id in order) stage(entries.getValue(id))

        // Phase one, validation: collect every complaint before touching state.
        for (id in order) {
            val entry = entries.getValue(id)
            if (entry.report.state != ModuleState.PENDING) continue
            val spec = entry.staged ?: continue
            val section = entry.stagedSection ?: continue
            val problems = section.problems() +
                runCatching { validateOf(entry, spec, section) }
                    .getOrElse { listOf("validate() threw ${it::class.simpleName}: ${it.message}") }
            if (problems.isNotEmpty()) {
                entry.report = entry.report.copy(state = ModuleState.FAILED, problems = problems)
                entry.staged = null
                entry.stagedSection = null
            }
        }

        // Phase one, dependency gate. Nothing loads before what it needs, and
        // walking in dependency order means a dependency is already settled by
        // the time its dependents are reached, so one pass is enough.
        for (id in order) {
            val entry = entries.getValue(id)
            if (entry.report.state != ModuleState.PENDING) continue
            val blocking = entry.module.dependsOn.firstOrNull { entries[it]?.report?.state != ModuleState.PENDING }
            if (blocking != null) {
                // A dependency that is off and a dependency that failed to
                // reload are different problems, and the operator needs to tell
                // them apart: the second one is still running old settings.
                val reason = if (entries[blocking]?.applied != null) {
                    "dependency '$blocking' could not be reloaded, so this keeps its previous settings"
                } else {
                    "dependency '$blocking' is not active"
                }
                entry.report = entry.report.copy(state = ModuleState.SKIPPED, problems = listOf(reason))
            }
        }

        // Phase two: apply what passed, in dependency order.
        for (id in order) {
            val entry = entries.getValue(id)
            if (entry.report.state != ModuleState.PENDING) continue
            val spec = entry.staged ?: continue
            val section = entry.stagedSection ?: continue
            val changed = changedRestartKeys(entry, section)
            val error = runCatching { apply(entry, spec) }.exceptionOrNull()
            if (error != null) {
                logger.log(Level.SEVERE, "Module $id threw while applying its config", error)
                entry.report = entry.report.copy(
                    state = ModuleState.FAILED,
                    problems = listOf("enable() threw ${error::class.simpleName}: ${error.message}"),
                )
                continue
            }
            entry.applied = spec
            entry.restartSnapshot = snapshotRestartKeys(entry, section)
            entry.report = entry.report.copy(state = ModuleState.ACTIVE, restartRequired = changed)
        }

        logReports()
        return reports()
    }

    /** Disables every active module, in reverse dependency order. */
    fun disableAll() {
        dependencyOrder().asReversed().forEach { id ->
            val entry = entries.getValue(id)
            if (!entry.report.isActive) return@forEach
            runCatching { entry.module.disable() }
                .onFailure { logger.log(Level.SEVERE, "Module $id threw while disabling", it) }
            entry.applied = null
            entry.report = entry.report.copy(state = ModuleState.DISABLED)
        }
    }

    private fun stage(entry: Entry) {
        val module = entry.module
        val relative = "modules/${module.fileName()}"
        entry.staged = null
        entry.stagedSection = null
        entry.report = entry.report.copy(problems = emptyList(), restartRequired = emptyList())

        val file = ensureFile(relative)
        val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }.getOrElse { error ->
            entry.report = entry.report.copy(
                state = ModuleState.FAILED,
                problems = listOf("could not read $relative: ${error.message}"),
            )
            return
        }

        val version = runCatching { migrate(entry, file, yaml) }.getOrElse { error ->
            entry.report = entry.report.copy(
                state = ModuleState.FAILED,
                problems = listOf(error.message ?: "migration failed"),
            )
            return
        }
        entry.report = entry.report.copy(schemaVersion = version)

        val section = ModuleSection(module.id, relative, yaml)
        if (!section.bool(module.enabledKey(), true)) {
            entry.report = entry.report.copy(state = ModuleState.DISABLED)
            return
        }

        val spec = runCatching { module.parse(section) }.getOrElse { error ->
            entry.report = entry.report.copy(
                state = ModuleState.FAILED,
                problems = listOf("parse() threw ${error::class.simpleName}: ${error.message}"),
            )
            return
        }
        entry.staged = spec
        entry.stagedSection = section
        entry.report = entry.report.copy(state = ModuleState.PENDING)
    }

    /**
     * Copies the shipped default out on first run and returns the file.
     *
     * A module that ships no default resource still gets a minimal file, so a
     * partially written module is usable with its getter defaults rather than
     * dead on arrival.
     */
    private fun ensureFile(relative: String): File {
        val directory = File(plugin.dataFolder, "modules")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, relative.removePrefix("modules/"))
        if (file.exists()) return file
        if (plugin.getResource(relative) != null) {
            plugin.saveResource(relative, false)
        } else {
            logger.warning("Module file $relative has no shipped default; writing a stub.")
            file.writeText("schema-version: 1\nenabled: true\n")
        }
        return file
    }

    /**
     * Brings one file up to the module's [SealModule.schemaVersion] and writes
     * it back. A file from a newer plugin is refused rather than guessed at, so
     * downgrading cannot silently drop settings.
     */
    private fun migrate(entry: Entry, file: File, yaml: YamlConfiguration): Int {
        val module = entry.module
        val target = module.schemaVersion
        val from = yaml.getInt("schema-version", 1)
        if (from > target) {
            error("${module.fileName()} is at schema-version $from but this build only understands $target. Update SealCore.")
        }
        if (from == target) return from

        val steps = migrations[module.id]
            .orEmpty()
            .filter { it.fromVersion >= from && it.fromVersion < target }
            .sortedBy { it.fromVersion }
        val missing = (from until target).filter { version -> steps.none { it.fromVersion == version } }
        if (missing.isNotEmpty()) {
            error("${module.fileName()} has no migration from schema-version ${missing.joinToString()}.")
        }
        for (step in steps) step.apply(yaml)
        yaml.set("schema-version", target)
        yaml.save(file)
        logger.info("Migrated ${module.fileName()} to schema-version $target.")
        return target
    }

    /**
     * Registration order, but a module always follows what it depends on.
     * A dependency cycle is fatal because there is no sensible order to pick.
     */
    private fun dependencyOrder(): List<String> {
        val ordered = ArrayList<String>(entries.size)
        val done = LinkedHashSet<String>()
        val visiting = LinkedHashSet<String>()

        fun visit(id: String) {
            if (id in done) return
            check(id !in visiting) { "module dependency cycle: ${(visiting.toList() + id).joinToString(" -> ")}" }
            visiting += id
            entries[id]?.module?.dependsOn?.sorted()?.forEach { dependency ->
                check(entries.containsKey(dependency)) { "module '$id' depends on unknown module '$dependency'" }
                visit(dependency)
            }
            visiting -= id
            done += id
            ordered += id
        }

        entries.keys.forEach { visit(it) }
        return ordered
    }

    private fun snapshotRestartKeys(entry: Entry, section: ModuleSection): Map<String, String?> =
        LinkedHashMap<String, String?>().also { map ->
            for (key in entry.module.restartKeys) map[key] = section.rawValue(key)?.toString()
        }

    /**
     * Restart only keys that differ from the values that are currently applied.
     * The first load has nothing to compare against, so it reports nothing.
     */
    private fun changedRestartKeys(entry: Entry, section: ModuleSection): List<String> {
        if (entry.restartSnapshot.isEmpty()) return emptyList()
        val current = LinkedHashMap<String, String?>()
        for (key in entry.module.restartKeys) current[key] = section.rawValue(key)?.toString()
        return current.keys.filter { entry.restartSnapshot[it] != current[it] }.sorted()
    }

    @Suppress("UNCHECKED_CAST")
    private fun apply(entry: Entry, spec: ModuleSpec) {
        (entry.module as SealModule<ModuleSpec>).enable(spec)
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateOf(entry: Entry, spec: ModuleSpec, section: ModuleSection): List<String> =
        (entry.module as SealModule<ModuleSpec>).validate(spec, section)

    private fun logReports() {
        for (report in reports()) {
            when (report.state) {
                ModuleState.ACTIVE -> logger.info("Module ${report.id} active (${report.file}).")
                ModuleState.DISABLED -> logger.info("Module ${report.id} disabled by config.")
                ModuleState.SKIPPED, ModuleState.FAILED ->
                    logger.warning("Module ${report.id} ${report.state.name.lowercase()}: ${report.problems.joinToString("; ")}")
                ModuleState.PENDING -> logger.warning("Module ${report.id} was never applied.")
            }
            for (key in report.restartRequired) {
                logger.warning("Module ${report.id}: '$key' changed and applies on the next restart.")
            }
        }
    }
}
