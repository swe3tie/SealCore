package sealmc.swe3tie.sealcore.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge;
import sealmc.swe3tie.sealcore.platform.CancellableTask;
import sealmc.swe3tie.sealcore.platform.TaskScheduler;
import sealmc.swe3tie.sealcore.text.Text;

/**
 * Packet driven HUD: sidebar, boss bar, action bar and titles.
 *
 * <p>Widgets own their own state so a line only goes out when it actually
 * changed, which keeps a per-second refresh cheap even with many viewers.
 */
public final class HudManager {

    private static final class SidebarState {
        private final String objective;
        private final Component title;
        private final Map<String, Component> lines;

        private SidebarState(String objective, Component title, Map<String, Component> lines) {
            this.objective = objective;
            this.title = title;
            this.lines = lines;
        }
    }

    private static final class BossBarState {
        private final UUID id;
        private Component title;
        private float progress;

        private BossBarState(UUID id, Component title, float progress) {
            this.id = id;
            this.title = title;
            this.progress = progress;
        }
    }

    private final PacketBridge bridge;
    private final TaskScheduler scheduler;
    private final Logger logger;

    private final Map<UUID, SidebarState> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBarState> bossBars = new ConcurrentHashMap<>();
    private final List<Runnable> tickables = new CopyOnWriteArrayList<>();

    private CancellableTask tickTask;

    public HudManager(PacketBridge bridge, TaskScheduler scheduler, Logger logger) {
        this.bridge = bridge;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void start(long intervalTicks) {
        stop();
        tickTask = scheduler.repeating(intervalTicks, intervalTicks, this::tick);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /** Pushes a line to a player's sidebar, creating the objective on first use. */
    public void setSidebarLine(Player player, String objective, Component title, String key, Component value) {
        UUID id = player.getUniqueId();
        SidebarState state = sidebars.computeIfAbsent(id, ignored -> new SidebarState(objective, title, new LinkedHashMap<>()));
        if (!state.objective.equals(objective) || !state.title.equals(title)) {
            removeSidebar(player);
            sidebars.put(id, new SidebarState(objective, title, new LinkedHashMap<>()));
            bridge.createSidebar(player, objective, title);
        }
        SidebarState target = sidebars.get(id);
        if (target == null) {
            return;
        }
        if (value.equals(target.lines.get(key))) {
            return;
        }
        target.lines.put(key, value);
        bridge.setSidebarLine(player, objective, key, value);
    }

    public void removeSidebarLine(Player player, String objective, String key) {
        SidebarState state = sidebars.get(player.getUniqueId());
        if (state == null || state.lines.remove(key) == null) {
            return;
        }
        bridge.removeSidebarLine(player, objective, key);
    }

    public void removeSidebar(Player player) {
        SidebarState state = sidebars.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        bridge.removeSidebar(player, state.objective);
    }

    public void showBossBar(Player player, UUID id, Component title, BossBar.Color color, float progress) {
        if (bossBars.putIfAbsent(player.getUniqueId(), new BossBarState(id, title, progress)) == null) {
            bridge.addBossBar(player, id, title, color, progress);
        } else {
            updateBossBar(player, title, progress);
        }
    }

    public void updateBossBar(Player player, Component title, float progress) {
        BossBarState state = bossBars.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (!state.title.equals(title)) {
            state.title = title;
            bridge.updateBossBarTitle(player, state.id, title);
        }
        if (state.progress != progress) {
            state.progress = progress;
            bridge.updateBossBarProgress(player, state.id, progress);
        }
    }

    public void hideBossBar(Player player) {
        BossBarState state = bossBars.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        bridge.removeBossBar(player, state.id);
    }

    public void sendActionBar(Player player, Component text) {
        bridge.sendActionBar(player, text);
    }

    public void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        bridge.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    public void showTitle(Player player, String title) {
        showTitle(player, title, "");
    }

    public void showTitle(Player player, String title, String subtitle) {
        bridge.sendTitle(player, Text.parse(title), Text.parse(subtitle), 10, 60, 10);
    }

    /** Clears everything for a player; called on quit and on disable. */
    public void clear(Player player) {
        removeSidebar(player);
        hideBossBar(player);
    }

    public void clearAll() {
        stop();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    /**
     * Registers a per-tick callback. Feature modules use this to repopulate their
     * own sidebar lines without the framework knowing about them.
     */
    public void onTick(Runnable callback) {
        tickables.add(callback);
    }

    public void tick() {
        for (Runnable callback : new ArrayList<>(tickables)) {
            try {
                callback.run();
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "HUD tick callback failed", failure);
            }
        }
    }
}
