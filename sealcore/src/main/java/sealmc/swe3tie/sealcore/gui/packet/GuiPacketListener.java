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

import java.util.UUID;

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
        // Matched before anything else, so the common case of a packet this
        // listener has no interest in costs one instanceof and nothing else.
        Object packet = event.getLastUsedWrapper();
        if (packet instanceof WrapperPlayClientClickWindow click) {
            Player player = playerOf(event);
            if (player != null) {
                handleClick(event, player, click);
            }
        } else if (packet instanceof WrapperPlayClientCloseWindow) {
            Player player = playerOf(event);
            if (player != null) {
                manager.handleClientClose(player);
            }
        }
    }

    /**
     * The player behind a client packet, or null when there is not one yet.
     *
     * <p>ProtocolPacketEvent exposes the platform player through a generic
     * getter, so it is resolved by uuid instead: same player, no unchecked cast
     * and no dependency on the server implementation. That uuid is null on a
     * connection that has not logged in yet, such as a legacy server list ping,
     * and Bukkit.getPlayer rejects a null uuid outright rather than answering
     * null, so it has to be checked here.
     */
    private static Player playerOf(PacketReceiveEvent event) {
        UUID id = event.getUser().getUUID();
        if (id == null) {
            return null;
        }
        return Bukkit.getPlayer(id);
    }

    private void handleClick(PacketReceiveEvent event, Player player, WrapperPlayClientClickWindow packet) {
        // Clicks are always cancelled while a screen is open, otherwise the
        // client would happily move items around in a container the server
        // knows nothing about and the two sides would drift apart.
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
