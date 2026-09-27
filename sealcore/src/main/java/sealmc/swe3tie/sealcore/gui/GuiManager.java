package sealmc.swe3tie.sealcore.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge;
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine;
import sealmc.swe3tie.sealcore.platform.TaskScheduler;
import sealmc.swe3tie.sealcore.text.Text;

/**
 * Owns every open packet screen.
 *
 * <p>Handles window ids, the navigation stack and slot diffing: a refresh only
 * sends the slots whose content actually changed, which is what keeps a menu
 * refreshing every second from flooding the client.
 */
public final class GuiManager {

    private static final int MAX_WINDOW_ID = 0x7FFF;
    private static final int MAX_STATE_ID = 0x7FFFFFFF;

    private final Plugin plugin;
    private final PacketBridge bridge;
    private final TaskScheduler scheduler;
    private final PlaceholderEngine placeholders;
    private final Logger logger;

    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger windowIds = new AtomicInteger(1);

    public GuiManager(
        Plugin plugin,
        PacketBridge bridge,
        TaskScheduler scheduler,
        PlaceholderEngine placeholders,
        Logger logger
    ) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.scheduler = scheduler;
        this.placeholders = placeholders;
        this.logger = logger;
    }

    public int openSessions() {
        return sessions.size();
    }

    public GuiSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean isOpen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Opens a screen as a new root and drops any navigation history. */
    public void open(Player player, GuiScreen screen) {
        close(player);
        GuiSession session = new GuiSession(player, player.getUniqueId(), nextWindowId());
        sessions.put(player.getUniqueId(), session);
        session.push(screen);
        fireOpen(session);
        render(session, true);
    }

    /** Replaces the current screen, leaving a back entry for {@link #back}. */
    public void push(Player player, GuiScreen screen) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            open(player, screen);
            return;
        }
        session.push(screen);
        fireOpen(session);
        render(session, true);
    }

    /** Restores the previous screen. @return false when there is nothing to go back to. */
    public boolean back(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.back() == null) {
            return false;
        }
        fireOpen(session);
        render(session, true);
        return true;
    }

    public void close(Player player) {
        GuiSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        GuiScreen screen = session.close();
        bridge.sendClose(player, session.windowId());
        if (screen != null) {
            fireClose(session, screen);
        }
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                close(player);
            } else {
                sessions.remove(uuid);
            }
        }
        sessions.clear();
    }

    public void refreshAll() {
        for (GuiSession session : new ArrayList<>(sessions.values())) {
            if (!session.player().isOnline()) {
                sessions.remove(session.playerId());
                continue;
            }
            render(session, false);
        }
    }

    public void refresh(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            render(session, false);
        }
    }

    /** @return true when a screen consumed the click. */
    public boolean handleClick(Player player, int slot, int button, ClickInfo.ClickMode mode) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.screen() == null) {
            return false;
        }
        ClickInfo click = new ClickInfo(slot, button, mode);
        boolean consumed;
        try {
            GuiElement element = session.screen().elementAt(slot);
            if (element != null) {
                element.onClick(contextOf(session), click);
            }
            consumed = true;
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "GUI click handler failed for " + player.getName(), failure);
            consumed = false;
        }

        if (consumed) {
            render(session, false);
        }
        return consumed;
    }

    public void handleClientClose(Player player) {
        GuiSession session = sessions.remove(player.getUniqueId());
        if (session == null || session.close() == null) {
            return;
        }
    }

    public void forget(UUID playerId) {
        sessions.remove(playerId);
    }

    // --- internals ------------------------------------------------------------

    private void render(GuiSession session, boolean force) {
        GuiScreen screen = session.screen();
        if (screen == null) {
            return;
        }
        Player player = session.player();
        if (!player.isOnline()) {
            sessions.remove(session.playerId());
            return;
        }

        GuiContext context = contextOf(session);
        Map<Integer, GuiItem> buffer = new LinkedHashMap<>();
        try {
            screen.render(context, buffer);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "GUI render failed for " + player.getName(), failure);
        }

        GuiLayout layout = screen.layout();
        if (layout.filler() != null) {
            for (int slot = 0; slot < layout.size(); slot++) {
                buffer.putIfAbsent(slot, layout.filler());
            }
        }

        if (force) {
            String raw = placeholders == null ? layout.title() : placeholders.resolve(player, layout.title());
            bridge.sendOpen(player, session.windowId(), layout.rows(), Text.parse(raw));
        }

        Map<Integer, GuiItem> previous = session.lastSent();
        session.stateId((session.stateId() + 1) % MAX_STATE_ID);
        int stateId = session.stateId();

        List<Integer> slots = new ArrayList<>(buffer.keySet());
        slots.sort(Integer::compareTo);
        for (int slot : slots) {
            GuiItem next = buffer.get(slot);
            if (!force && next.equals(previous.get(slot))) {
                continue;
            }
            bridge.sendSlot(player, session.windowId(), stateId, slot, next);
        }
        // A slot whose element disappeared has to be cleared explicitly.
        for (int slot : previous.keySet()) {
            if (!buffer.containsKey(slot)) {
                bridge.sendEmptySlot(player, session.windowId(), stateId, slot);
            }
        }
        session.lastSent(new LinkedHashMap<>(buffer));
    }

    private void fireOpen(GuiSession session) {
        GuiScreen screen = session.screen();
        if (screen == null) {
            return;
        }
        try {
            screen.onOpen(contextOf(session));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "GUI open hook failed for " + session.player().getName(), failure);
        }
    }

    private void fireClose(GuiSession session, GuiScreen screen) {
        try {
            screen.onClose(contextOf(session));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "GUI close hook failed for " + session.player().getName(), failure);
        }
    }

    private int nextWindowId() {
        int id = windowIds.getAndIncrement();
        if (id <= 0 || id > MAX_WINDOW_ID) {
            windowIds.set(1);
            return 1;
        }
        return id;
    }

    private GuiContext contextOf(GuiSession session) {
        return new GuiContext(
            session.player(),
            session.playerId(),
            session,
            placeholders,
            task -> scheduler.player(session.player(), task));
    }
}
