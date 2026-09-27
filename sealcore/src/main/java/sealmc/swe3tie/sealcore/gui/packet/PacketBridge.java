package sealmc.swe3tie.sealcore.gui.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.util.Dummy;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleTimes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sealmc.swe3tie.sealcore.gui.GuiItem;

/**
 * The single place that talks to PacketEvents.
 *
 * <p>Nothing else in the plugin imports a packet class, so the encoding details
 * that differ between Minecraft versions stay contained in one class and a
 * protocol change only ever breaks this one.
 */
public final class PacketBridge {

    private final Plugin plugin;
    private final Logger logger;

    private PacketEventsAPI<?> api;

    /** True when this plugin installed the API, so {@link #terminate()} may shut it down. */
    private boolean owned;

    /** Channels are injected but the managers are not up yet. */
    public boolean isLoaded() {
        return api != null && api.isLoaded();
    }

    public boolean isReady() {
        return api != null && api.isInitialized();
    }

    public String packetEventsVersion() {
        try {
            if (api == null || api.getVersion() == null) {
                return "unavailable";
            }
            return api.getVersion().major() + "." + api.getVersion().minor() + "." + api.getVersion().patch();
        } catch (RuntimeException notReady) {
            return "unavailable";
        }
    }

    public PacketBridge(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * Builds the embedded API and injects the channels. Called from {@code onLoad}
     * so the injector is in place before any player connects.
     *
     * <p>A relocated PacketEvents has its own static state, so the API always has
     * to be constructed here rather than picked up from a standalone install.
     */
    public boolean load() {
        try {
            warnIfStandalonePresent();
            PacketEventsAPI<Plugin> events = SpigotPacketEventsBuilder.build(plugin);
            PacketEvents.setAPI(events);
            events.load();
            api = events;
            owned = true;
            logger.info("PacketEvents " + packetEventsVersion() + " loaded.");
            return true;
        } catch (Throwable failure) {
            logger.log(Level.SEVERE, "PacketEvents failed to load; GUI and HUD features are disabled.", failure);
            return false;
        }
    }

    /**
     * Starts the PacketEvents managers. Called from {@code onEnable}, matching the
     * upstream plugin: the channel injector is safe during plugin load, the
     * managers are not.
     */
    public boolean init() {
        PacketEventsAPI<?> events = api;
        if (events == null) {
            return false;
        }
        if (events.isInitialized()) {
            return true;
        }
        try {
            events.init();
            logger.info("PacketEvents ready (" + packetEventsVersion() + ").");
            return true;
        } catch (Throwable failure) {
            logger.log(Level.SEVERE, "PacketEvents failed to start; GUI and HUD features are disabled.", failure);
            return false;
        }
    }

    private void warnIfStandalonePresent() {
        if (plugin.getServer().getPluginManager().getPlugin("packetevents") != null) {
            logger.warning(
                "A standalone PacketEvents plugin is installed. SealCore embeds its own copy; "
                    + "remove the standalone one to avoid two injectors fighting over the same channels.");
        }
    }

    public void registerListener(PacketListener listener) {
        PacketEventsAPI<?> events = api;
        if (events == null) {
            return;
        }
        try {
            events.getEventManager().registerListener(listener, PacketListenerPriority.NORMAL);
        } catch (Throwable failure) {
            logger.log(Level.SEVERE, "Failed to register the SealCore packet listener.", failure);
        }
    }

    public void terminate() {
        if (owned && api != null) {
            try {
                api.terminate();
            } catch (RuntimeException alreadyGone) {
                // The API is being torn down anyway; nothing left to shut down.
            }
        }
        api = null;
        owned = false;
    }

    // --- items ---------------------------------------------------------------

    /**
     * Encodes a {@link GuiItem} for the wire.
     *
     * <p>Built from data components rather than NBT so the same call produces a
     * correct packet on 1.21.11 and on both 26.x lines, where the legacy tag
     * names no longer exist.
     */
    public ItemStack encode(GuiItem item) {
        var type = ItemTypes.getByName(item.material());
        if (type == null) {
            logger.warning("Unknown material '" + item.material() + "', falling back to stone.");
            type = ItemTypes.getByName("stone");
            if (type == null) {
                return null;
            }
        }

        var builder = ItemStack.builder()
            .type(type)
            .amount(Math.max(1, Math.min(99, item.amount())));

        if (item.name() != null) {
            builder = builder.component(ComponentTypes.CUSTOM_NAME, item.name());
        }
        if (item.hasLore()) {
            builder = builder.component(ComponentTypes.LORE, new ItemLore(item.lore()));
        }
        if (item.glow()) {
            builder = builder.component(ComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (item.hideTooltip()) {
            builder = builder.component(ComponentTypes.HIDE_TOOLTIP, Dummy.DUMMY);
        }
        if (item.profile() != null) {
            var property = new ItemProfile.Property("textures", item.profile(), null);
            builder = builder.component(
                ComponentTypes.PROFILE,
                new ItemProfile("sealcore", null, List.of(property)));
        }
        return builder.build();
    }

    // --- containers ----------------------------------------------------------

    public void sendOpen(Player player, int windowId, int rows, Component title) {
        // Window type ids are 0-based: 0 is a single row, 5 is six rows.
        send(player, new WrapperPlayServerOpenWindow(windowId, Math.max(0, Math.min(5, rows - 1)), title));
    }

    public void sendClose(Player player, int windowId) {
        send(player, new WrapperPlayServerCloseWindow(windowId));
    }

    public void sendSlot(Player player, int windowId, int stateId, int slot, GuiItem item) {
        ItemStack encoded = encode(item);
        if (encoded == null) {
            return;
        }
        send(player, new WrapperPlayServerSetSlot(windowId, stateId, slot, encoded));
    }

    public void sendEmptySlot(Player player, int windowId, int stateId, int slot) {
        ItemStack empty = ItemStack.builder().type(ItemTypes.AIR).amount(0).build();
        send(player, new WrapperPlayServerSetSlot(windowId, stateId, slot, empty));
    }

    // --- text hud ------------------------------------------------------------

    public void sendActionBar(Player player, Component text) {
        send(player, new WrapperPlayServerActionBar(text));
    }

    public void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        send(player, new WrapperPlayServerSetTitleTimes(fadeIn, stay, fadeOut));
        send(player, new WrapperPlayServerSetTitleSubtitle(subtitle));
        send(player, new WrapperPlayServerSetTitleText(title));
    }

    // --- boss bar ------------------------------------------------------------

    public void addBossBar(Player player, UUID id, Component title, BossBar.Color color, float progress) {
        var bar = new WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.ADD);
        bar.setTitle(title);
        bar.setColor(color);
        bar.setHealth(clampProgress(progress));
        send(player, bar);
    }

    public void updateBossBarTitle(Player player, UUID id, Component title) {
        var bar = new WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.UPDATE_TITLE);
        bar.setTitle(title);
        send(player, bar);
    }

    public void updateBossBarProgress(Player player, UUID id, float progress) {
        var bar = new WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.UPDATE_HEALTH);
        bar.setHealth(clampProgress(progress));
        send(player, bar);
    }

    private static float clampProgress(float progress) {
        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    public void removeBossBar(Player player, UUID id) {
        send(player, new WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.REMOVE));
    }

    // --- sidebar -------------------------------------------------------------

    public void createSidebar(Player player, String objective, Component title) {
        send(player, new WrapperPlayServerScoreboardObjective(
            objective,
            WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
            title,
            WrapperPlayServerScoreboardObjective.RenderType.INTEGER));
        // Slot 1 is the sidebar; 0 is the tab list and 2+ are the chat areas.
        send(player, new WrapperPlayServerDisplayScoreboard(1, objective));
    }

    public void updateSidebarTitle(Player player, String objective, Component title) {
        send(player, new WrapperPlayServerScoreboardObjective(
            objective,
            WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
            title,
            WrapperPlayServerScoreboardObjective.RenderType.INTEGER));
    }

    public void setSidebarLine(Player player, String objective, String key, Component value) {
        send(player, new WrapperPlayServerUpdateScore(
            key,
            WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
            objective,
            0,
            value,
            ScoreFormat.blankScore()));
    }

    public void removeSidebarLine(Player player, String objective, String key) {
        send(player, new WrapperPlayServerResetScore(key, objective));
    }

    public void removeSidebar(Player player, String objective) {
        send(player, new WrapperPlayServerScoreboardObjective(
            objective,
            WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
            Component.empty(),
            WrapperPlayServerScoreboardObjective.RenderType.INTEGER));
    }

    // --- transport -----------------------------------------------------------

    public void send(Player player, PacketWrapper<?> packet) {
        PacketEventsAPI<?> events = api;
        if (events == null) {
            return;
        }
        try {
            events.getPlayerManager().sendPacket(player, packet);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Failed to send " + packet.getClass().getSimpleName(), failure);
        }
    }
}
