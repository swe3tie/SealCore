package sealmc.swe3tie.sealcore.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Per-player GUI state: which screen is open, the window id the client knows it
 * by, the navigation stack and the last content that was sent.
 *
 * <p>The last-sent snapshot is what makes refreshing cheap; without it every
 * refresh would resend all slots even when nothing changed.
 */
public final class GuiSession {

    private final Player player;
    private final UUID playerId;
    private final int windowId;
    private final Deque<GuiScreen> navigation = new ArrayDeque<>();

    private GuiScreen screen;

    /** Client side content state id; a fresh one is required for every update batch. */
    private int stateId;

    private Map<Integer, GuiItem> lastSent = new LinkedHashMap<>();

    public GuiSession(Player player, UUID playerId, int windowId) {
        this.player = player;
        this.playerId = playerId;
        this.windowId = windowId;
    }

    public Player player() {
        return player;
    }

    public UUID playerId() {
        return playerId;
    }

    public int windowId() {
        return windowId;
    }

    public GuiScreen screen() {
        return screen;
    }

    public int stateId() {
        return stateId;
    }

    public void stateId(int stateId) {
        this.stateId = stateId;
    }

    public Map<Integer, GuiItem> lastSent() {
        return lastSent;
    }

    public void lastSent(Map<Integer, GuiItem> lastSent) {
        this.lastSent = lastSent;
    }

    public boolean isOpen() {
        return screen != null;
    }

    public boolean canGoBack() {
        return !navigation.isEmpty();
    }

    public void push(GuiScreen next) {
        if (screen != null) {
            navigation.addLast(screen);
        }
        screen = next;
    }

    /** @return the screen that was open, or null if nothing was. */
    public GuiScreen close() {
        GuiScreen previous = screen;
        screen = null;
        navigation.clear();
        lastSent = new LinkedHashMap<>();
        return previous;
    }

    public GuiScreen back() {
        GuiScreen previous = navigation.pollLast();
        if (previous == null) {
            return null;
        }
        screen = previous;
        lastSent = new LinkedHashMap<>();
        return previous;
    }
}
