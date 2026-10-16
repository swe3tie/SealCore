package sealmc.swe3tie.sealcore.modules.economy.packet;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for inventory interactions to schedule an inventory update on the next tick.
 * This forces the server to resend container slot and cursor packets, ensuring client-side
 * worth lore is recalculated for updated stack sizes after splitting, dragging, or merging.
 */
public final class WorthInventoryListener implements Listener {

    private final EconomyModule module;
    private final Set<UUID> pendingUpdates = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public WorthInventoryListener(@NotNull EconomyModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!this.module.worthLoreConfig().enabled()) return;
        if (event.getWhoClicked() instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE) return;
            this.scheduleUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!this.module.worthLoreConfig().enabled()) return;
        if (event.getWhoClicked() instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE) return;
            this.scheduleUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!this.module.worthLoreConfig().enabled()) return;
        this.scheduleUpdate(event.getPlayer());
    }

    private void scheduleUpdate(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!this.pendingUpdates.add(uuid)) {
            return;
        }

        this.module.core().scheduler().entityLater(player, () -> {
            this.pendingUpdates.remove(uuid);
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, () -> this.pendingUpdates.remove(uuid), 1);
    }
}
