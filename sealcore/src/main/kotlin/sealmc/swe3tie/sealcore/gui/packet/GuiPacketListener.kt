package sealmc.swe3tie.sealcore.gui.packet

import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import sealmc.swe3tie.sealcore.gui.ClickInfo
import sealmc.swe3tie.sealcore.gui.GuiManager

/**
 * Turns client container packets into screen actions.
 *
 * Clicks are always cancelled while a screen is open, otherwise the client
 * would happily move items around in a container the server knows nothing
 * about and the two sides would drift apart.
 */
class GuiPacketListener(private val manager: GuiManager) : PacketListener {

    override fun onPacketReceive(event: PacketReceiveEvent) {
        // ProtocolPacketEvent exposes the platform player through a generic
        // getter, so it is resolved by uuid instead: same player, no unchecked
        // cast and no dependency on the server implementation.
        val player: Player = Bukkit.getPlayer(event.user.uuid) ?: return
        when (val packet = event.lastUsedWrapper) {
            is WrapperPlayClientClickWindow -> handleClick(event, player, packet)
            is WrapperPlayClientCloseWindow -> manager.handleClientClose(player)
            else -> Unit
        }
    }

    private fun handleClick(event: PacketReceiveEvent, player: Player, packet: WrapperPlayClientClickWindow) {
        val session = manager.session(player) ?: return
        if (packet.windowId != session.windowId) return

        event.isCancelled = true

        val size = session.screen?.layout?.size ?: return
        val slot = packet.slot
        if (slot < 0 || slot >= size) return

        manager.handleClick(player, slot, packet.button, modeOf(packet))
    }

    private fun modeOf(packet: WrapperPlayClientClickWindow): ClickInfo.ClickMode =
        when (packet.windowClickType) {
            WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE -> ClickInfo.ClickMode.SHIFT
            WrapperPlayClientClickWindow.WindowClickType.SWAP -> ClickInfo.ClickMode.SWAP
            WrapperPlayClientClickWindow.WindowClickType.CLONE -> ClickInfo.ClickMode.MIDDLE
            WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL -> ClickInfo.ClickMode.DOUBLE
            WrapperPlayClientClickWindow.WindowClickType.THROW -> ClickInfo.ClickMode.DROP
            WrapperPlayClientClickWindow.WindowClickType.PICKUP -> ClickInfo.ClickMode.NORMAL
            else -> ClickInfo.ClickMode.NORMAL
        }
}
