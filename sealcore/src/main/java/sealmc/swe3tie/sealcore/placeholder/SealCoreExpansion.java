package sealmc.swe3tie.sealcore.placeholder;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Exposes the same {@code %prefix_key%} tokens through PlaceholderAPI.
 *
 * <p>Registered only when PlaceholderAPI is installed, and unregistered on
 * disable so a reload does not leave a stale expansion behind.
 */
public final class SealCoreExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final PlaceholderEngine engine;

    public SealCoreExpansion(Plugin plugin, PlaceholderEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public String getIdentifier() {
        return "sealcore";
    }

    @Override
    public String getAuthor() {
        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());
        return authors.isEmpty() ? "sealmc.swe3tie" : authors;
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        String resolved = engine.resolve(offlinePlayer, "%" + params.toLowerCase(Locale.ROOT) + "%");
        return resolved.startsWith("%") ? null : resolved;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return onRequest(player, params);
    }
}
