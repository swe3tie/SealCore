package sealmc.swe3tie.sealcore.core.module;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * A feature inside a module, toggled by {@code submodules.<id>.enabled} in the parent's yml.
 * Resources must be registered through the parent module so they are released with it.
 */
public abstract class SealSubmodule<P extends SealModule> {

    protected final P parent;
    private final String id;
    private boolean enabled;

    protected SealSubmodule(@NotNull P parent, @NotNull String id) {
        this.parent = parent;
        this.id = id;
    }

    @NotNull
    public final String id() {
        return this.id;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    /** The {@code submodules.<id>} section of the parent module config (never null). */
    @NotNull
    protected final ConfigurationSection settings() {
        ConfigurationSection section = this.parent.config().getConfigurationSection("submodules." + this.id);
        return section != null ? section : this.parent.config().createSection("submodules." + this.id);
    }

    protected abstract void onEnable() throws Exception;

    protected void onDisable() {
    }

    protected void onReload() throws Exception {
    }

    final void enableIfConfigured() {
        if (!this.settings().getBoolean("enabled", true)) return;
        try {
            this.onEnable();
            this.enabled = true;
        } catch (Exception e) {
            this.parent.core.getLogger().log(Level.SEVERE,
                "Submodule " + this.parent.id() + "/" + this.id + " failed to enable", e);
        }
    }

    final void reload() throws Exception {
        boolean wanted = this.settings().getBoolean("enabled", true);
        if (this.enabled && !wanted) {
            this.disable();
        } else if (!this.enabled && wanted) {
            this.enableIfConfigured();
        } else if (this.enabled) {
            this.onReload();
        }
    }

    final void disable() {
        if (!this.enabled) return;
        this.enabled = false;
        try {
            this.onDisable();
        } catch (RuntimeException e) {
            this.parent.core.getLogger().log(Level.WARNING,
                "Submodule " + this.parent.id() + "/" + this.id + " threw while disabling", e);
        }
    }
}
