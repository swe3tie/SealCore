package sealmc.swe3tie.sealcore.hud

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge
import sealmc.swe3tie.sealcore.platform.CancellableTask
import sealmc.swe3tie.sealcore.platform.TaskScheduler
import sealmc.swe3tie.sealcore.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Packet driven HUD: sidebar, boss bar, action bar and titles.
 *
 * Widgets own their own state so a line only goes out when it actually
 * changed, which keeps a per-second refresh cheap even with many viewers.
 */
class HudManager(
    private val bridge: PacketBridge,
    private val scheduler: TaskScheduler,
    private val logger: Logger,
) {

    private val sidebars = ConcurrentHashMap<UUID, SidebarState>()
    private val bossBars = ConcurrentHashMap<UUID, BossBarState>()
    private var tickTask: CancellableTask? = null
    private val tickables = mutableListOf<() -> Unit>()

    fun start(intervalTicks: Long) {
        stop()
        tickTask = scheduler.repeating(intervalTicks, intervalTicks) { tick() }
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
    }

    /** Pushes a line to a player's sidebar, creating the objective on first use. */
    fun setSidebarLine(player: Player, objective: String, title: Component, key: String, value: Component) {
        val state = sidebars.computeIfAbsent(player.uniqueId) { SidebarState(objective, title, mutableMapOf()) }
        if (state.objective != objective || state.title != title) {
            removeSidebar(player)
            sidebars[player.uniqueId] = SidebarState(objective, title, mutableMapOf())
            bridge.createSidebar(player, objective, title)
        }
        val target = sidebars[player.uniqueId] ?: return
        if (target.lines[key] == value) return
        target.lines[key] = value
        bridge.setSidebarLine(player, objective, key, value)
    }

    fun removeSidebarLine(player: Player, objective: String, key: String) {
        val state = sidebars[player.uniqueId] ?: return
        if (state.lines.remove(key) == null) return
        bridge.removeSidebarLine(player, objective, key)
    }

    fun removeSidebar(player: Player) {
        val state = sidebars.remove(player.uniqueId) ?: return
        bridge.removeSidebar(player, state.objective)
    }

    fun showBossBar(player: Player, id: UUID, title: Component, color: BossBar.Color, progress: Float) {
        if (bossBars.putIfAbsent(player.uniqueId, BossBarState(id, title, progress)) == null) {
            bridge.addBossBar(player, id, title, color, progress)
        } else {
            updateBossBar(player, title, progress)
        }
    }

    fun updateBossBar(player: Player, title: Component, progress: Float) {
        val state = bossBars[player.uniqueId] ?: return
        if (state.title != title) {
            state.title = title
            bridge.updateBossBarTitle(player, state.id, title)
        }
        if (state.progress != progress) {
            state.progress = progress
            bridge.updateBossBarProgress(player, state.id, progress)
        }
    }

    fun hideBossBar(player: Player) {
        val state = bossBars.remove(player.uniqueId) ?: return
        bridge.removeBossBar(player, state.id)
    }

    fun sendActionBar(player: Player, text: Component) = bridge.sendActionBar(player, text)

    fun sendTitle(player: Player, title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) =
        bridge.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut)

    fun showTitle(player: Player, title: String, subtitle: String = "") {
        bridge.sendTitle(
            player,
            Text.parse(title),
            Text.parse(subtitle),
            10,
            60,
            10,
        )
    }

    /** Clears everything for a player; called on quit and on disable. */
    fun clear(player: Player) {
        removeSidebar(player)
        hideBossBar(player)
    }

    fun clearAll() {
        stop()
        for (player in Bukkit.getOnlinePlayers()) {
            clear(player)
        }
    }

    /**
     * Registers a per-tick callback. Feature modules use this to repopulate
     * their own sidebar lines without the framework knowing about them.
     */
    fun onTick(callback: () -> Unit) {
        tickables += callback
    }

    fun tick() {
        for (callback in tickables.toList()) {
            runCatching(callback).onFailure { logger.log(Level.WARNING, "HUD tick callback failed", it) }
        }
    }

    private class SidebarState(
        val objective: String,
        val title: Component,
        val lines: MutableMap<String, Component>,
    )

    private class BossBarState(
        val id: UUID,
        var title: Component,
        var progress: Float,
    )
}
