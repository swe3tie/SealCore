package sealmc.swe3tie.sealcore.gui.packet

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.score.ScoreFormat
import com.github.retrooper.packetevents.util.Dummy
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleTimes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sealmc.swe3tie.sealcore.gui.GuiItem
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The single place that talks to PacketEvents.
 *
 * Nothing else in the plugin imports a packet class, so the encoding details
 * that differ between Minecraft versions stay contained in one file and a
 * protocol change only ever breaks this class.
 */
class PacketBridge(private val plugin: Plugin, private val logger: Logger) {

    private var api: PacketEventsAPI<*>? = null

    /** True when this plugin installed the API, so [terminate] may shut it down. */
    private var owned: Boolean = false

    /** Channels are injected but the managers are not up yet. */
    val isLoaded: Boolean get() = api?.isLoaded == true

    val isReady: Boolean get() = api?.isInitialized == true

    val packetEventsVersion: String
        get() = runCatching {
            val version = api?.version ?: return@runCatching null
            "${version.major()}.${version.minor()}.${version.patch()}"
        }.getOrNull() ?: "unavailable"

    /**
     * Builds the embedded API and injects the channels. Called from `onLoad` so
     * the injector is in place before any player connects.
     *
     * A relocated PacketEvents has its own static state, so the API always has
     * to be constructed here rather than picked up from a standalone install.
     */
    fun load(): Boolean = runCatching {
        warnIfStandalonePresent()
        val events = SpigotPacketEventsBuilder.build(plugin)
        PacketEvents.setAPI(events)
        events.load()
        api = events
        owned = true
        logger.info("PacketEvents $packetEventsVersion loaded.")
        true
    }.onFailure {
        logger.log(Level.SEVERE, "PacketEvents failed to load; GUI and HUD features are disabled.", it)
    }.getOrDefault(false)

    /**
     * Starts the PacketEvents managers. Called from `onEnable`, matching the
     * upstream plugin: the channel injector is safe during plugin load, the
     * managers are not.
     */
    fun init(): Boolean = runCatching {
        val events = api ?: return false
        if (events.isInitialized) return true
        events.init()
        logger.info("PacketEvents ready ($packetEventsVersion).")
        true
    }.onFailure {
        logger.log(Level.SEVERE, "PacketEvents failed to start; GUI and HUD features are disabled.", it)
    }.getOrDefault(false)

    private fun warnIfStandalonePresent() {
        if (plugin.server.pluginManager.getPlugin("packetevents") != null) {
            logger.warning(
                "A standalone PacketEvents plugin is installed. SealCore embeds its own copy; " +
                    "remove the standalone one to avoid two injectors fighting over the same channels."
            )
        }
    }

    fun registerListener(listener: PacketListener) {
        val events = api ?: return
        runCatching { events.eventManager.registerListener(listener, PacketListenerPriority.NORMAL) }
            .onFailure { logger.log(Level.SEVERE, "Failed to register the SealCore packet listener.", it) }
    }

    fun terminate() {
        if (owned) runCatching { api?.terminate() }
        api = null
        owned = false
    }

    // --- items ---------------------------------------------------------------

    /**
     * Encodes a [GuiItem] for the wire.
     *
     * Built from data components rather than NBT so the same call produces a
     * correct packet on 1.21.11 and on both 26.x lines, where the legacy tag
     * names no longer exist.
     */
    fun encode(item: GuiItem): ItemStack? {
        val type = ItemTypes.getByName(item.material) ?: run {
            logger.warning("Unknown material '${item.material}', falling back to stone.")
            ItemTypes.getByName("stone")
        } ?: return null

        var builder = ItemStack.builder()
            .type(type)
            .amount(item.amount.coerceIn(1, 99))

        item.name?.let { name -> builder = builder.component(ComponentTypes.CUSTOM_NAME, name) }
        if (item.hasLore) {
            builder = builder.component(ComponentTypes.LORE, ItemLore(item.lore))
        }
        if (item.glow) {
            builder = builder.component(ComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }
        if (item.hideTooltip) {
            builder = builder.component(ComponentTypes.HIDE_TOOLTIP, Dummy.DUMMY)
        }
        item.profile?.let { texture ->
            val property = ItemProfile.Property("textures", texture, null)
            builder = builder.component(ComponentTypes.PROFILE, ItemProfile("sealcore", null, listOf(property)))
        }
        return builder.build()
    }

    // --- containers ----------------------------------------------------------

    fun sendOpen(player: Player, windowId: Int, rows: Int, title: Component) {
        // Window type ids are 0-based: 0 is a single row, 5 is six rows.
        send(player, WrapperPlayServerOpenWindow(windowId, (rows - 1).coerceIn(0, 5), title))
    }

    fun sendClose(player: Player, windowId: Int) {
        send(player, WrapperPlayServerCloseWindow(windowId))
    }

    fun sendSlot(player: Player, windowId: Int, stateId: Int, slot: Int, item: GuiItem) {
        val encoded = encode(item) ?: return
        send(player, WrapperPlayServerSetSlot(windowId, stateId, slot, encoded))
    }

    fun sendEmptySlot(player: Player, windowId: Int, stateId: Int, slot: Int) {
        val empty = ItemStack.builder().type(ItemTypes.AIR).amount(0).build()
        send(player, WrapperPlayServerSetSlot(windowId, stateId, slot, empty))
    }

    // --- text hud ------------------------------------------------------------

    fun sendActionBar(player: Player, text: Component) {
        send(player, WrapperPlayServerActionBar(text))
    }

    fun sendTitle(player: Player, title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) {
        send(player, WrapperPlayServerSetTitleTimes(fadeIn, stay, fadeOut))
        send(player, WrapperPlayServerSetTitleSubtitle(subtitle))
        send(player, WrapperPlayServerSetTitleText(title))
    }

    // --- boss bar ------------------------------------------------------------

    fun addBossBar(player: Player, id: UUID, title: Component, color: BossBar.Color, progress: Float) {
        val bar = WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.ADD)
        bar.title = title
        bar.color = color
        bar.health = progress.coerceIn(0f, 1f)
        send(player, bar)
    }

    fun updateBossBarTitle(player: Player, id: UUID, title: Component) {
        val bar = WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.UPDATE_TITLE)
        bar.title = title
        send(player, bar)
    }

    fun updateBossBarProgress(player: Player, id: UUID, progress: Float) {
        val bar = WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.UPDATE_HEALTH)
        bar.health = progress.coerceIn(0f, 1f)
        send(player, bar)
    }

    fun removeBossBar(player: Player, id: UUID) {
        send(player, WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.REMOVE))
    }

    // --- sidebar -------------------------------------------------------------

    fun createSidebar(player: Player, objective: String, title: Component) {
        val packet = WrapperPlayServerScoreboardObjective(
            objective,
            WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
            title,
            WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
        )
        send(player, packet)
        // Slot 1 is the sidebar; 0 is the tab list and 2+ are the chat areas.
        send(player, WrapperPlayServerDisplayScoreboard(1, objective))
    }

    fun updateSidebarTitle(player: Player, objective: String, title: Component) {
        send(
            player,
            WrapperPlayServerScoreboardObjective(
                objective,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                title,
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
            ),
        )
    }

    fun setSidebarLine(player: Player, objective: String, key: String, value: Component) {
        send(
            player,
            WrapperPlayServerUpdateScore(
                key,
                WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                objective,
                0,
                value,
                ScoreFormat.blankScore(),
            ),
        )
    }

    fun removeSidebarLine(player: Player, objective: String, key: String) {
        send(player, WrapperPlayServerResetScore(key, objective))
    }

    fun removeSidebar(player: Player, objective: String) {
        send(
            player,
            WrapperPlayServerScoreboardObjective(
                objective,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                Component.empty(),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
            ),
        )
    }

    // --- transport -----------------------------------------------------------

    fun send(player: Player, packet: PacketWrapper<*>) {
        val events = api ?: return
        runCatching { events.playerManager.sendPacket(player, packet) }
            .onFailure { logger.log(Level.WARNING, "Failed to send ${packet.javaClass.simpleName}", it) }
    }
}
