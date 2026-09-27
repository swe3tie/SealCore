package sealmc.swe3tie.sealcore.gui.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.gui.ClickInfo;
import sealmc.swe3tie.sealcore.gui.GuiManager;
import sealmc.swe3tie.sealcore.gui.GuiSession;

/**
 * Turns client container packets into screen actions.
 *
 * <p>Clicks are always cancelled while a screen is open, otherwise the client
 * would happily move items around in a container the server knows nothing about
 * and the two sides would drift apart.
 */
public final class GuiPacketListener implements PacketListener {

    private final GuiManager manager;

    public GuiPacketListener(GuiManager manager) {
        this.manager = manager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // ProtocolPacketEvent exposes the platform player through a generic
        // getter, so it is resolved by uuid instead: same player, no unchecked
        // cast and no dependency on the server implementation.
        Player player = Bukkit.getPlayer(event.getUser().getUUID());
        if (player == null) {
            return;
        }
        Object packet = event.getLastUsedWrapper();
        if (packet instanceof WrapperPlayClientClickWindow click) {
            handleClick(event, player, click);
        } else if (packet instanceof WrapperPlayClientCloseWindow) {
            manager.handleClientClose(player);
        }
    }

    private void handleClick(PacketReceiveEvent event, Player player, WrapperPlayClientClickWindow packet) {
        GuiSession session = manager.session(player);
        if (session == null || packet.getWindowId() != session.windowId()) {
            return;
        }

        event.setCancelled(true);

        if (session.screen() == null) {
            return;
        }
        int size = session.screen().layout().size();
        int slot = packet.getSlot();
        if (slot < 0 || slot >= size) {
            return;
        }

        manager.handleClick(player, slot, packet.getButton(), modeOf(packet));
    }

    private static ClickInfo.ClickMode modeOf(WrapperPlayClientClickWindow packet) {
        WrapperPlayClientClickWindow.WindowClickType type = packet.getWindowClickType();
        if (type == null) {
            return ClickInfo.ClickMode.NORMAL;
        }
        return switch (type) {
            case QUICK_MOVE -> ClickInfo.ClickMode.SHIFT;
            case SWAP -> ClickInfo.ClickMode.SWAP;
            case CLONE -> ClickInfo.ClickMode.MIDDLE;
            case PICKUP_ALL -> ClickInfo.ClickMode.DOUBLE;
            case THROW -> ClickInfo.ClickMode.DROP;
            default -> ClickInfo.ClickMode.NORMAL;
        };
    }
}
