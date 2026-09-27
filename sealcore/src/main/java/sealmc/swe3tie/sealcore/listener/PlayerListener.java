package sealmc.swe3tie.sealcore.listener;

import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.storage.PlayerRepository;

/**
 * Keeps per-player state in sync: the profile row, the cached placeholders and
 * any HUD state that must be torn down.
 */
public final class PlayerListener implements Listener {

    private final SealCore plugin;

    public PlayerListener(SealCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.placeholders().invalidate(player.getUniqueId());
        storeProfile(player.getUniqueId(), player.getName());
        if (plugin.hasGui()) {
            plugin.registerExpansion();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (plugin.hasGui()) {
            plugin.gui().forget(player.getUniqueId());
        }
        if (plugin.hasHud()) {
            plugin.hud().clear(player);
        }
        plugin.placeholders().invalidate(player.getUniqueId());
    }

    /**
     * Profile writes are storage work, so they never run on the join thread; the
     * async scheduler is the only correct place for them.
     */
    private void storeProfile(UUID uuid, String name) {
        if (!plugin.isStorageReady()) {
            return;
        }
        PlayerRepository repository = plugin.players();
        long now = System.currentTimeMillis();
        plugin.scheduler().async(() -> {
            try {
                repository.upsert(uuid, name, now);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Failed to store the profile of " + name, failure);
            }
        });
    }
}
