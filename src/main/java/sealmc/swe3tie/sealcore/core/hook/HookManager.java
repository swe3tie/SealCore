package sealmc.swe3tie.sealcore.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.config.CoreSettings;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.hook.economy.ExcellentCurrencyService;
import sealmc.swe3tie.sealcore.core.hook.placeholder.PapiHook;
import sealmc.swe3tie.sealcore.core.hook.placeholder.PlaceholderRegistry;

import java.util.logging.Level;

/**
 * Detects and wires external plugins. Every integration is optional and is loaded only after its
 * plugin is confirmed enabled, so a missing plugin can never cause a NoClassDefFoundError.
 */
public final class HookManager {

    private final SealCore core;
    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private volatile CurrencyService currency;
    private PapiHook papi;
    private boolean packetEvents;

    public HookManager(@NotNull SealCore core) {
        this.core = core;
    }

    /** (Re)attaches hooks according to the current settings. Safe to call on reload. */
    public void enable(@NotNull CoreSettings settings) {
        this.currency = null;
        if (this.isPluginEnabled(Hook.ECONOMY)) {
            try {
                this.currency = ExcellentCurrencyService.create(settings.economy());
                if (this.currency == null) this.core.getLogger().warning("ExcellentEconomy is enabled but exposes no API.");
            } catch (LinkageError | RuntimeException e) {
                this.core.getLogger().log(Level.WARNING, "Could not hook ExcellentEconomy", e);
            }
        }

        if (this.papi == null && this.isPluginEnabled(Hook.PLACEHOLDERAPI)) {
            try {
                this.papi = PapiHook.install(this.core, this.placeholders);
            } catch (LinkageError | RuntimeException e) {
                this.core.getLogger().log(Level.WARNING, "Could not hook PlaceholderAPI", e);
            }
        }

        this.packetEvents = settings.packetsEnabled() && this.isPluginEnabled(Hook.PACKETEVENTS);
    }

    public void disable() {
        if (this.papi != null) {
            try {
                this.papi.uninstall();
            } catch (LinkageError | RuntimeException ignored) {
                // PlaceholderAPI may already be shutting down.
            }
            this.papi = null;
        }
        this.currency = null;
        this.packetEvents = false;
    }

    public boolean isAvailable(@NotNull Hook hook) {
        return switch (hook) {
            case ECONOMY -> this.currency != null;
            case PLACEHOLDERAPI -> this.papi != null;
            case PACKETEVENTS -> this.packetEvents;
        };
    }

    /** The currency backend, or {@code null} when ExcellentEconomy is missing. */
    @Nullable
    public CurrencyService currency() {
        return this.currency;
    }

    @NotNull
    public PlaceholderRegistry placeholders() {
        return this.placeholders;
    }

    private boolean isPluginEnabled(@NotNull Hook hook) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(hook.pluginName());
        return plugin != null && plugin.isEnabled();
    }
}
