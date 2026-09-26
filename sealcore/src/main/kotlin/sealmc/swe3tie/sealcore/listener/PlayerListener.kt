package sealmc.swe3tie.sealcore.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import sealmc.swe3tie.sealcore.SealCore
import sealmc.swe3tie.sealcore.storage.PlayerRepository
import java.util.UUID
import java.util.logging.Level

/**
 * Keeps per-player state in sync: the profile row, the cached placeholders and
 * any HUD state that must be torn down.
 */
class PlayerListener(private val plugin: SealCore) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.placeholders.invalidate(player.uniqueId)
        storeProfile(player.uniqueId, player.name)
        if (plugin.hasGui) plugin.registerExpansion()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (plugin.hasGui) plugin.gui.forget(player.uniqueId)
        if (plugin.hasHud) plugin.hud.clear(player)
        plugin.placeholders.invalidate(player.uniqueId)
    }

    /**
     * Profile writes are storage work, so they never run on the join thread;
     * the async scheduler is the only correct place for them.
     */
    private fun storeProfile(uuid: UUID, name: String) {
        if (!plugin.isStorageReady) return
        val repository: PlayerRepository = plugin.players
        val now = System.currentTimeMillis()
        plugin.scheduler.async {
            runCatching { repository.upsert(uuid, name, now) }
                .onFailure { plugin.logger.log(Level.WARNING, "Failed to store the profile of $name", it) }
        }
    }
}
