package sealmc.swe3tie.sealcore.gui

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Per-player GUI state: which screen is open, the window id the client knows it
 * by, the navigation stack and the last content that was sent.
 *
 * The [lastSent] snapshot is what makes refreshing cheap; without it every
 * refresh would resend all slots even when nothing changed.
 */
class GuiSession(
    val player: Player,
    val playerId: UUID,
    val windowId: Int,
) {

    var screen: GuiScreen? = null
        private set

    /** Client side content state id; a fresh one is required for every update batch. */
    var stateId: Int = 0

    private val navigation = ArrayDeque<GuiScreen>()

    var lastSent: MutableMap<Int, GuiItem> = mutableMapOf()
        internal set

    val isOpen: Boolean get() = screen != null

    val canGoBack: Boolean get() = navigation.isNotEmpty()

    fun push(next: GuiScreen) {
        screen?.let { navigation.addLast(it) }
        screen = next
    }

    /** @return the screen that was open, or null if nothing was. */
    fun close(): GuiScreen? {
        val previous = screen
        screen = null
        navigation.clear()
        lastSent = mutableMapOf()
        return previous
    }

    fun back(): GuiScreen? {
        val previous = navigation.removeLastOrNull() ?: return null
        screen = previous
        lastSent = mutableMapOf()
        return previous
    }
}
