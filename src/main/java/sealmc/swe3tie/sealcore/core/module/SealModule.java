package sealmc.swe3tie.sealcore.core.module;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.config.YamlFile;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.scheduler.SealTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Base class for a SealCore module (packaged in the plugin, not standalone).
 * <p>
 * A module owns {@code modules/<id>/config.yml} (with legacy flat-file fallback), its listeners,
 * tasks, placeholders and submodules. Anything
 * registered through {@link #listen}, {@link #track} or {@link #registerPlaceholders} is released
 * automatically on disable, so modules can be toggled at runtime.
 */
public abstract class SealModule {

    protected final SealCore core;
    private final String id;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<SealTask> tasks = new ArrayList<>();
    private final List<SealSubmodule<?>> submodules = new ArrayList<>();
    private YamlFile file;
    private volatile YamlConfiguration config = new YamlConfiguration();
    private volatile ModuleState state = ModuleState.DISABLED;
    private volatile boolean placeholdersRegistered;

    protected SealModule(@NotNull SealCore core, @NotNull String id) {
        this.core = core;
        this.id = id;
    }

    @NotNull
    public final String id() {
        return this.id;
    }

    @NotNull
    public final SealCore core() {
        return this.core;
    }

    /** External plugins that must be present. The module is skipped when one is missing. */
    @NotNull
    public Set<Hook> requiredHooks() {
        return Set.of();
    }

    /** Modules that must be enabled first. */
    @NotNull
    public Set<String> dependsOn() {
        return Set.of();
    }

    /** Config sections whose entries are user owned and never re-added after deletion. */
    @NotNull
    protected Collection<String> freeSections() {
        return List.of();
    }

    /** Called with the freshly loaded module config; register commands, listeners and tasks here. */
    protected abstract void onEnable() throws Exception;

    /** Called before listeners and tasks are released. */
    protected void onDisable() {
    }

    /** Called after the module config was re-read while the module stays enabled. */
    protected void onReload() throws Exception {
    }

    // Lifecycle (driven by ModuleManager) -------------------------------------------------------

    final void enable() throws Exception {
        this.loadConfig();
        this.onEnable();
        for (SealSubmodule<?> submodule : this.submodules) submodule.enableIfConfigured();
        this.state = ModuleState.ENABLED;
    }

    final void reload() throws Exception {
        this.loadConfig();
        this.onReload();
        for (SealSubmodule<?> submodule : this.submodules) submodule.reload();
    }

    final void disable() {
        for (SealSubmodule<?> submodule : this.submodules) submodule.disable();
        try {
            this.onDisable();
        } catch (RuntimeException e) {
            this.core.getLogger().log(java.util.logging.Level.WARNING, "Module " + this.id + " threw while disabling", e);
        }
        this.release();
    }

    /** Frees resources after a failed enable too. */
    final void release() {
        for (Listener listener : this.listeners) HandlerList.unregisterAll(listener);
        this.listeners.clear();
        this.tasks.forEach(SealTask::cancel);
        this.tasks.clear();
        if (this.placeholdersRegistered) {
            this.core.hooks().placeholders().unregister(this.id);
            this.placeholdersRegistered = false;
        }
    }

    final void setState(@NotNull ModuleState state) {
        this.state = state;
    }

    @NotNull
    public final ModuleState state() {
        return this.state;
    }

    public final boolean isActive() {
        return this.state == ModuleState.ENABLED;
    }

    private void loadConfig() throws ConfigException {
        if (this.file == null) this.file = this.core.config().moduleFile(this.id, this.freeSections());
        this.config = this.file.load();
    }

    /** Resolves a module-owned YAML file under {@code modules/<id>/}. */
    @NotNull
    protected final YamlFile resolveFile(@NotNull String relativePath) {
        return this.core.config().moduleCustomFile(this.id, relativePath);
    }

    // Helpers for subclasses --------------------------------------------------------------------

    @NotNull
    public final YamlConfiguration config() {
        return this.config;
    }

    protected final void listen(@NotNull Listener listener) {
        this.core.getServer().getPluginManager().registerEvents(listener, this.core);
        this.listeners.add(listener);
    }

    protected final void track(@NotNull SealTask task) {
        this.tasks.add(task);
    }

    /** Exposes {@code %sealcore_<moduleId>_<params>%}. */
    protected final void registerPlaceholders(@NotNull sealmc.swe3tie.sealcore.core.hook.placeholder.PlaceholderRegistry.Resolver resolver) {
        this.core.hooks().placeholders().register(this.id, resolver);
        this.placeholdersRegistered = true;
    }

    protected final <S extends SealSubmodule<?>> S addSubmodule(@NotNull S submodule) {
        this.submodules.add(submodule);
        return submodule;
    }

    @NotNull
    public final List<SealSubmodule<?>> submodules() {
        return List.copyOf(this.submodules);
    }
}
