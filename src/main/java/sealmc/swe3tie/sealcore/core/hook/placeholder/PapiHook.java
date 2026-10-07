package sealmc.swe3tie.sealcore.core.hook.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI glue. Only this class references PlaceholderAPI types; load it only when the
 * plugin is present.
 */
public final class PapiHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PlaceholderRegistry registry;

    private PapiHook(@NotNull JavaPlugin plugin, @NotNull PlaceholderRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Registers the {@code sealcore} expansion and wires message parsing into the registry. */
    @NotNull
    public static PapiHook install(@NotNull JavaPlugin plugin, @NotNull PlaceholderRegistry registry) {
        PapiHook hook = new PapiHook(plugin, registry);
        hook.register();
        registry.setParser(PlaceholderAPI::setPlaceholders);
        registry.setHooked(true);
        return hook;
    }

    public void uninstall() {
        this.registry.setParser((player, text) -> text);
        this.registry.setHooked(false);
        this.unregister();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sealcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", this.plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        return this.registry.resolve(player, params);
    }
}
