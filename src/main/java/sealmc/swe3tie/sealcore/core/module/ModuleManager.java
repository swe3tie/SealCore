package sealmc.swe3tie.sealcore.core.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.hook.Hook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers modules and drives their lifecycle in dependency order. A module that throws is marked
 * {@link ModuleState#FAILED} and released; every other module keeps running.
 */
public final class ModuleManager {

    private final SealCore core;
    private final Map<String, SealModule> modules = new LinkedHashMap<>();
    private final Map<String, String> lastError = new LinkedHashMap<>();
    private List<String> order = List.of();

    public ModuleManager(@NotNull SealCore core) {
        this.core = core;
    }

    public void register(@NotNull SealModule module) {
        if (this.modules.putIfAbsent(module.id().toLowerCase(Locale.ROOT), module) != null) {
            throw new IllegalStateException("Duplicate module id: " + module.id());
        }
    }

    /** Computes the enable order. Call once after registering every module. */
    public void freeze() {
        Map<String, java.util.Set<String>> graph = new LinkedHashMap<>();
        this.modules.forEach((id, module) -> graph.put(id, module.dependsOn()));
        this.order = ModuleGraph.order(graph);
    }

    /** Applies config.yml toggles to every module, in dependency order. Also used on reload. */
    public void sync() {
        for (String id : this.order) this.apply(this.modules.get(id), false);
    }

    public void disableAll() {
        for (int i = this.order.size() - 1; i >= 0; i--) {
            SealModule module = this.modules.get(this.order.get(i));
            if (module.isActive()) module.disable();
            module.setState(ModuleState.DISABLED);
        }
    }

    @NotNull
    public Collection<SealModule> all() {
        return this.order.stream().map(this.modules::get).toList();
    }

    @Nullable
    public SealModule find(@NotNull String id) {
        return this.modules.get(id.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public String lastError(@NotNull String id) {
        return this.lastError.get(id.toLowerCase(Locale.ROOT));
    }

    /** Force-enables one module (ignoring its config.yml toggle). @return error text or null */
    @Nullable
    public String enable(@NotNull SealModule module) {
        if (module.isActive()) return null;
        this.apply(module, true);
        return module.isActive() ? null : this.describeFailure(module);
    }

    /** Disables one module and any module that depends on it. */
    public void disable(@NotNull SealModule module) {
        List<SealModule> affected = new ArrayList<>();
        for (int i = this.order.size() - 1; i >= 0; i--) {
            SealModule candidate = this.modules.get(this.order.get(i));
            if (candidate == module || this.dependsTransitively(candidate, module)) affected.add(candidate);
        }
        for (SealModule m : affected) {
            if (m.isActive()) m.disable();
            m.setState(ModuleState.DISABLED);
        }
    }

    /** Reloads one active module's config. @return error text or null */
    @Nullable
    public String reload(@NotNull SealModule module) {
        if (!module.isActive()) return this.enable(module);
        this.apply(module, false);
        return module.isActive() ? null : this.describeFailure(module);
    }

    // -------------------------------------------------------------------------------------------

    private void apply(SealModule module, boolean ignoreToggle) {
        String id = module.id();
        boolean wanted = ignoreToggle || this.core.settings().isModuleEnabled(id);

        if (!wanted) {
            if (module.isActive()) module.disable();
            module.setState(ModuleState.DISABLED);
            return;
        }

        for (Hook hook : module.requiredHooks()) {
            if (!this.core.hooks().isAvailable(hook)) {
                if (module.isActive()) module.disable();
                module.setState(ModuleState.MISSING_HOOK);
                this.lastError.put(id, hook.pluginName());
                this.core.getLogger().warning("Module '" + id + "' skipped: requires " + hook.pluginName());
                return;
            }
        }

        for (String dep : module.dependsOn()) {
            SealModule dependency = this.modules.get(dep.toLowerCase(Locale.ROOT));
            if (dependency == null || !dependency.isActive()) {
                if (module.isActive()) module.disable();
                module.setState(ModuleState.MISSING_DEPENDENCY);
                this.lastError.put(id, dep);
                this.core.getLogger().warning("Module '" + id + "' skipped: requires module '" + dep + "'");
                return;
            }
        }

        try {
            if (module.isActive()) {
                module.reload();
            } else {
                module.enable();
                this.core.getLogger().info("Enabled module '" + id + "'");
            }
            this.lastError.remove(id);
        } catch (Exception | LinkageError e) {
            module.release();
            module.setState(ModuleState.FAILED);
            this.lastError.put(id, String.valueOf(e.getMessage()));
            this.core.getLogger().log(Level.SEVERE, "Module '" + id + "' failed: " + e.getMessage(), e);
        }
    }

    private boolean dependsTransitively(SealModule module, SealModule target) {
        for (String dep : module.dependsOn()) {
            SealModule d = this.modules.get(dep.toLowerCase(Locale.ROOT));
            if (d == null) continue;
            if (d == target || this.dependsTransitively(d, target)) return true;
        }
        return false;
    }

    private String describeFailure(SealModule module) {
        String error = this.lastError.get(module.id().toLowerCase(Locale.ROOT));
        return error == null ? module.state().name() : error;
    }
}
