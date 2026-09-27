package sealmc.swe3tie.sealcore.module;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Owns every {@link SealModule}: its file, its staged spec and its last good
 * spec.
 *
 * <p>A load pass is two phase on purpose. Phase one reads, migrates, parses and
 * validates every module without touching live state. Phase two applies the
 * specs that passed, in dependency order. A typo in one file therefore leaves
 * the running server on its previous settings for that module while everything
 * else reloads, instead of a half applied reload.
 */
public final class ModuleRegistry {

    private static final class Entry {
        private final SealModule<?> module;
        private ModuleReport report;
        private ModuleSpec staged;
        private ModuleSection stagedSection;
        private ModuleSpec applied;
        private Map<String, String> restartSnapshot = Map.of();

        private Entry(SealModule<?> module) {
            this.module = module;
            this.report = new ModuleReport(module.id(), "modules/" + module.fileName(), ModuleState.PENDING);
        }
    }

    private final Plugin plugin;
    private final Logger logger;

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<ModuleMigration>> migrations = new HashMap<>();

    public ModuleRegistry(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /** Migrations available for one module, applied when its file is behind. */
    public ModuleMigration addMigration(ModuleMigration migration) {
        migrations.computeIfAbsent(migration.moduleId(), key -> new ArrayList<>()).add(migration);
        return migration;
    }

    public SealModule<?> register(SealModule<?> module) {
        if (entries.containsKey(module.id())) {
            throw new IllegalArgumentException("module '" + module.id() + "' is already registered");
        }
        entries.put(module.id(), new Entry(module));
        return module;
    }

    public List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    /** Every registered module, in registration order. */
    public List<SealModule<?>> registered() {
        List<SealModule<?>> modules = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            modules.add(entry.module);
        }
        return modules;
    }

    public ModuleReport report(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.report;
    }

    public List<ModuleReport> reports() {
        List<ModuleReport> reports = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            reports.add(entry.report);
        }
        return reports;
    }

    public int activeCount() {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.report.isActive()) {
                count++;
            }
        }
        return count;
    }

    public boolean isRegistered(String id) {
        return entries.containsKey(id);
    }

    /** The spec currently applied for the id, or null when the module is not active. */
    @SuppressWarnings("unchecked")
    public <C extends ModuleSpec> C specOf(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : (C) entry.applied;
    }

    /**
     * Reads, migrates, parses and validates everything, then applies what
     * passed. Safe to call again after a reload: a module that fails keeps the
     * spec it already had.
     */
    public List<ModuleReport> load() {
        List<String> order = dependencyOrder();
        for (String id : order) {
            stage(entries.get(id));
        }

        // Phase one, validation: collect every complaint before touching state.
        for (String id : order) {
            Entry entry = entries.get(id);
            if (entry.report.state() != ModuleState.PENDING) {
                continue;
            }
            if (entry.staged == null || entry.stagedSection == null) {
                continue;
            }
            List<String> problems = new ArrayList<>(entry.stagedSection.problems());
            problems.addAll(validateOf(entry, entry.staged, entry.stagedSection));
            if (!problems.isEmpty()) {
                entry.report = entry.report.withState(ModuleState.FAILED).withProblems(problems);
                entry.staged = null;
                entry.stagedSection = null;
            }
        }

        // Phase one, dependency gate. Nothing loads before what it needs, and
        // walking in dependency order means a dependency is already settled by
        // the time its dependents are reached, so one pass is enough.
        for (String id : order) {
            Entry entry = entries.get(id);
            if (entry.report.state() != ModuleState.PENDING) {
                continue;
            }
            String blocking = null;
            for (String dependency : sorted(entry.module.dependsOn())) {
                Entry other = entries.get(dependency);
                if (other == null || other.report.state() != ModuleState.PENDING) {
                    blocking = dependency;
                    break;
                }
            }
            if (blocking != null) {
                // A dependency that is off and a dependency that failed to
                // reload are different problems, and the operator needs to tell
                // them apart: the second one is still running old settings.
                Entry other = entries.get(blocking);
                String reason = other.applied != null
                    ? "dependency '" + blocking + "' could not be reloaded, so this keeps its previous settings"
                    : "dependency '" + blocking + "' is not active";
                entry.report = entry.report.withState(ModuleState.SKIPPED).withProblems(List.of(reason));
            }
        }

        // Phase two: apply what passed, in dependency order.
        for (String id : order) {
            Entry entry = entries.get(id);
            if (entry.report.state() != ModuleState.PENDING) {
                continue;
            }
            if (entry.staged == null || entry.stagedSection == null) {
                continue;
            }
            List<String> changed = changedRestartKeys(entry, entry.stagedSection);
            try {
                apply(entry, entry.staged);
            } catch (RuntimeException | Error error) {
                logger.log(Level.SEVERE, "Module " + id + " threw while applying its config", error);
                entry.report = entry.report.withState(ModuleState.FAILED).withProblems(
                    List.of("enable() threw " + error.getClass().getSimpleName() + ": " + error.getMessage()));
                continue;
            }
            entry.applied = entry.staged;
            entry.restartSnapshot = snapshotRestartKeys(entry, entry.stagedSection);
            entry.report = entry.report.withState(ModuleState.ACTIVE).withRestartRequired(changed);
        }

        logReports();
        return reports();
    }

    /** Disables every active module, in reverse dependency order. */
    public void disableAll() {
        List<String> order = dependencyOrder();
        for (int index = order.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(order.get(index));
            if (!entry.report.isActive()) {
                continue;
            }
            try {
                entry.module.disable();
            } catch (RuntimeException | Error error) {
                logger.log(Level.SEVERE, "Module " + entry.module.id() + " threw while disabling", error);
            }
            entry.applied = null;
            entry.report = entry.report.withState(ModuleState.DISABLED);
        }
    }

    private void stage(Entry entry) {
        SealModule<?> module = entry.module;
        String relative = "modules/" + module.fileName();
        entry.staged = null;
        entry.stagedSection = null;
        entry.report = entry.report.withProblems(List.of()).withRestartRequired(List.of());

        File file = ensureFile(relative);
        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException error) {
            entry.report = entry.report.withState(ModuleState.FAILED).withProblems(
                List.of("could not read " + relative + ": " + error.getMessage()));
            return;
        }

        int version;
        try {
            version = migrate(entry, file, yaml);
        } catch (RuntimeException error) {
            entry.report = entry.report.withState(ModuleState.FAILED).withProblems(
                List.of(error.getMessage() == null ? "migration failed" : error.getMessage()));
            return;
        }
        entry.report = entry.report.withSchemaVersion(version);

        ModuleSection section = new ModuleSection(module.id(), relative, yaml);
        if (!section.bool(module.enabledKey(), true)) {
            entry.report = entry.report.withState(ModuleState.DISABLED);
            return;
        }

        ModuleSpec spec;
        try {
            spec = parseOf(entry, section);
        } catch (RuntimeException error) {
            entry.report = entry.report.withState(ModuleState.FAILED).withProblems(
                List.of("parse() threw " + error.getClass().getSimpleName() + ": " + error.getMessage()));
            return;
        }
        entry.staged = spec;
        entry.stagedSection = section;
        entry.report = entry.report.withState(ModuleState.PENDING);
    }

    /**
     * Copies the shipped default out on first run and returns the file.
     *
     * <p>A module that ships no default resource still gets a minimal file, so a
     * partially written module is usable with its getter defaults rather than
     * dead on arrival.
     */
    private File ensureFile(String relative) {
        File directory = new File(plugin.getDataFolder(), "modules");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory, relative.substring("modules/".length()));
        if (file.exists()) {
            return file;
        }
        if (plugin.getResource(relative) != null) {
            plugin.saveResource(relative, false);
        } else {
            logger.warning("Module file " + relative + " has no shipped default; writing a stub.");
            writeStub(file);
        }
        return file;
    }

    private void writeStub(File file) {
        try {
            Files.writeString(file.toPath(), "schema-version: 1\nenabled: true\n");
        } catch (IOException failure) {
            logger.log(Level.WARNING, "Could not write the module stub " + file, failure);
        }
    }

    /**
     * Brings one file up to the module's {@link SealModule#schemaVersion()} and
     * writes it back. A file from a newer plugin is refused rather than guessed
     * at, so downgrading cannot silently drop settings.
     */
    private int migrate(Entry entry, File file, YamlConfiguration yaml) {
        SealModule<?> module = entry.module;
        int target = module.schemaVersion();
        int from = yaml.getInt("schema-version", 1);
        if (from > target) {
            throw new IllegalStateException(module.fileName() + " is at schema-version " + from
                + " but this build only understands " + target + ". Update SealCore.");
        }
        if (from == target) {
            return from;
        }

        List<ModuleMigration> steps = new ArrayList<>();
        for (ModuleMigration migration : migrations.getOrDefault(module.id(), List.of())) {
            if (migration.fromVersion() >= from && migration.fromVersion() < target) {
                steps.add(migration);
            }
        }
        steps.sort(java.util.Comparator.comparingInt(ModuleMigration::fromVersion));

        List<Integer> missing = new ArrayList<>();
        for (int version = from; version < target; version++) {
            boolean found = false;
            for (ModuleMigration step : steps) {
                if (step.fromVersion() == version) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(version);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(module.fileName() + " has no migration from schema-version " + missing + ".");
        }

        for (ModuleMigration step : steps) {
            step.apply().accept(yaml);
        }
        yaml.set("schema-version", target);
        try {
            yaml.save(file);
        } catch (java.io.IOException unwritable) {
            throw new IllegalStateException(module.fileName() + " could not be written back: " + unwritable.getMessage(), unwritable);
        }
        logger.info("Migrated " + module.fileName() + " to schema-version " + target + ".");
        return target;
    }

    /**
     * Registration order, but a module always follows what it depends on. A
     * dependency cycle is fatal because there is no sensible order to pick.
     */
    private List<String> dependencyOrder() {
        List<String> ordered = new ArrayList<>(entries.size());
        Set<String> done = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();

        for (String id : entries.keySet()) {
            visit(id, ordered, done, visiting);
        }
        return ordered;
    }

    private void visit(String id, List<String> ordered, Set<String> done, Set<String> visiting) {
        if (done.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalStateException("module dependency cycle: " + new ArrayList<>(visiting) + " -> " + id);
        }
        Entry entry = entries.get(id);
        for (String dependency : sorted(entry.module.dependsOn())) {
            if (!entries.containsKey(dependency)) {
                throw new IllegalStateException("module '" + id + "' depends on unknown module '" + dependency + "'");
            }
            visit(dependency, ordered, done, visiting);
        }
        visiting.remove(id);
        done.add(id);
        ordered.add(id);
    }

    private Map<String, String> snapshotRestartKeys(Entry entry, ModuleSection section) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : entry.module.restartKeys()) {
            Object value = section.rawValue(key);
            map.put(key, value == null ? null : value.toString());
        }
        return map;
    }

    /**
     * Restart only keys that differ from the values that are currently applied.
     * The first load has nothing to compare against, so it reports nothing.
     */
    private List<String> changedRestartKeys(Entry entry, ModuleSection section) {
        if (entry.restartSnapshot.isEmpty()) {
            return List.of();
        }
        Map<String, String> current = snapshotRestartKeys(entry, section);
        List<String> changed = new ArrayList<>();
        for (String key : current.keySet()) {
            if (!java.util.Objects.equals(entry.restartSnapshot.get(key), current.get(key))) {
                changed.add(key);
            }
        }
        changed.sort(String::compareTo);
        return changed;
    }

    @SuppressWarnings("unchecked")
    private ModuleSpec parseOf(Entry entry, ModuleSection section) {
        return ((SealModule<ModuleSpec>) entry.module).parse(section);
    }

    @SuppressWarnings("unchecked")
    private void apply(Entry entry, ModuleSpec spec) {
        ((SealModule<ModuleSpec>) entry.module).enable(spec);
    }

    @SuppressWarnings("unchecked")
    private List<String> validateOf(Entry entry, ModuleSpec spec, ModuleSection section) {
        try {
            return ((SealModule<ModuleSpec>) entry.module).validate(spec, section);
        } catch (RuntimeException error) {
            return List.of("validate() threw " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void logReports() {
        for (ModuleReport report : reports()) {
            switch (report.state()) {
                case ACTIVE -> logger.info("Module " + report.id() + " active (" + report.file() + ").");
                case DISABLED -> logger.info("Module " + report.id() + " disabled by config.");
                case SKIPPED, FAILED -> logger.warning("Module " + report.id() + " "
                    + report.state().name().toLowerCase(java.util.Locale.ROOT) + ": " + String.join("; ", report.problems()));
                case PENDING -> logger.warning("Module " + report.id() + " was never applied.");
            }
            for (String key : report.restartRequired()) {
                logger.warning("Module " + report.id() + ": '" + key + "' changed and applies on the next restart.");
            }
        }
    }

    private static List<String> sorted(Set<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
