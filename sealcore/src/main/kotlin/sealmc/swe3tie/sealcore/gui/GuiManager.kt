package sealmc.swe3tie.sealcore.gui

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine
import sealmc.swe3tie.sealcore.platform.TaskScheduler
import sealmc.swe3tie.sealcore.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns every open packet screen.
 *
 * Handles window ids, the navigation stack and slot diffing: a refresh only
 * sends the slots whose content actually changed, which is what keeps a menu
 * refreshing every second from flooding the client.
 */
class GuiManager(
    private val plugin: Plugin,
    private val bridge: PacketBridge,
    private val scheduler: TaskScheduler,
    private val placeholders: PlaceholderEngine?,
    private val logger: Logger,
) {

    private val sessions = ConcurrentHashMap<UUID, GuiSession>()
    private val windowIds = AtomicInteger(1)

    val openSessions: Int get() = sessions.size

    fun session(player: Player): GuiSession? = sessions[player.uniqueId]

    fun isOpen(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    /** Opens [screen] as a new root and drops any navigation history. */
    fun open(player: Player, screen: GuiScreen) {
        close(player)
        val session = GuiSession(player, player.uniqueId, nextWindowId())
        sessions[player.uniqueId] = session
        session.push(screen)
        fireOpen(session)
        render(session, force = true)
    }

    /** Replaces the current screen, leaving a back entry for [back]. */
    fun push(player: Player, screen: GuiScreen) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            open(player, screen)
            return
        }
        session.push(screen)
        fireOpen(session)
        render(session, force = true)
    }

    /** Restores the previous screen. @return false when there is nothing to go back to. */
    fun back(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        session.back() ?: return false
        fireOpen(session)
        render(session, force = true)
        return true
    }

    fun close(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        val screen = session.close()
        bridge.sendClose(player, session.windowId)
        if (screen != null) fireClose(session, screen)
    }

    fun closeAll() {
        for (uuid in sessions.keys.toList()) {
            val player = plugin.server.getPlayer(uuid)
            if (player != null) close(player) else sessions.remove(uuid)
        }
        sessions.clear()
    }

    fun refreshAll() {
        for (session in sessions.values) {
            if (!session.player.isOnline) {
                sessions.remove(session.playerId)
                continue
            }
            render(session, force = false)
        }
    }

    fun refresh(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        render(session, force = false)
    }

    /** @return true when a screen consumed the click. */
    fun handleClick(player: Player, slot: Int, button: Int, mode: ClickInfo.ClickMode): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        val screen = session.screen ?: return false
        val click = ClickInfo(slot, button, mode)
        val consumed = runCatching {
            screen.elementAt(slot)?.onClick(contextOf(session), click)
            true
        }.onFailure { error ->
            logger.log(Level.WARNING, "GUI click handler failed for ${player.name}", error)
        }.getOrDefault(false)

        if (consumed) render(session, force = false)
        return consumed
    }

    fun handleClientClose(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        val screen = session.close() ?: return
        fireClose(session, screen)
    }

    fun forget(playerId: UUID) {
        sessions.remove(playerId)
    }

    // --- internals ------------------------------------------------------------

    private fun render(session: GuiSession, force: Boolean) {
        val screen = session.screen ?: return
        val player = session.player
        if (!player.isOnline) {
            sessions.remove(session.playerId)
            return
        }

        val context = contextOf(session)
        val buffer = LinkedHashMap<Int, GuiItem>()
        runCatching { screen.render(context, buffer) }
            .onFailure { error -> logger.log(Level.WARNING, "GUI render failed for ${player.name}", error) }

        val layout = screen.layout
        layout.filler?.let { filler ->
            for (slot in 0 until layout.size) {
                buffer.putIfAbsent(slot, filler)
            }
        }

        if (force) {
            val raw = placeholders?.resolve(player, layout.title) ?: layout.title
            bridge.sendOpen(player, session.windowId, layout.rows, Text.parse(raw))
        }

        val previous = session.lastSent
        session.stateId = (session.stateId + 1) % MAX_STATE_ID
        val stateId = session.stateId

        for (slot in buffer.keys.sorted()) {
            if (!force && previous[slot] == buffer[slot]) continue
            bridge.sendSlot(player, session.windowId, stateId, slot, buffer.getValue(slot))
        }
        // A slot whose element disappeared has to be cleared explicitly.
        for (slot in previous.keys) {
            if (buffer.containsKey(slot)) continue
            bridge.sendEmptySlot(player, session.windowId, stateId, slot)
        }
        session.lastSent = LinkedHashMap(buffer)
    }

    private fun fireOpen(session: GuiSession) {
        val screen = session.screen ?: return
        runCatching { screen.onOpen(contextOf(session)) }
            .onFailure { error -> logger.log(Level.WARNING, "GUI open hook failed for ${session.player.name}", error) }
    }

    private fun fireClose(session: GuiSession, screen: GuiScreen) {
        runCatching { screen.onClose(contextOf(session)) }
            .onFailure { error -> logger.log(Level.WARNING, "GUI close hook failed for ${session.player.name}", error) }
    }

    private fun nextWindowId(): Int {
        val id = windowIds.getAndIncrement()
        if (id <= 0 || id > MAX_WINDOW_ID) {
            windowIds.set(1)
            return 1
        }
        return id
    }

    private fun contextOf(session: GuiSession): GuiContext = GuiContext(
        player = session.player,
        playerId = session.playerId,
        session = session,
        placeholders = placeholders,
        scheduler = { task -> scheduler.player(session.player, task::run) },
    )

    private companion object {
        const val MAX_WINDOW_ID = 0x7FFF
        const val MAX_STATE_ID = 0x7FFFFFFF
    }
}
