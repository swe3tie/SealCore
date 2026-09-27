package su.nightexpress.excellenteconomy.migration;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

import java.util.Map;

public abstract class Migrator {

    protected final EconomyPlugin plugin;
    protected final String        name;

    public Migrator(@NotNull EconomyPlugin plugin, @NotNull String name) {
        this.plugin = plugin;
        this.name = name;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @Nullable
    public Plugin getBackend() {
        return this.plugin.getPluginManager().getPlugin(this.name);
    }

    public abstract boolean canMigrate(@NotNull ExcellentCurrency currency);

    @NotNull
    public abstract Map<OfflinePlayer, Double> getBalances(@NotNull ExcellentCurrency currency);
}
