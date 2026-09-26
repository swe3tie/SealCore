package sealmc.swe3tie.sealcore

import org.bukkit.plugin.java.JavaPlugin
import sealmc.swe3tie.sealcore.command.SealCoreCommand
import sealmc.swe3tie.sealcore.config.ConfigManager
import sealmc.swe3tie.sealcore.config.Messages
import sealmc.swe3tie.sealcore.config.SealCoreConfig
import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.economy.EconomyService
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge
import sealmc.swe3tie.sealcore.gui.GuiManager
import sealmc.swe3tie.sealcore.gui.packet.GuiPacketListener
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge
import sealmc.swe3tie.sealcore.hud.HudManager
import sealmc.swe3tie.sealcore.listener.PlayerListener
import sealmc.swe3tie.sealcore.module.ModuleMigration
import sealmc.swe3tie.sealcore.module.SealModule
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine
import sealmc.swe3tie.sealcore.placeholder.SealCoreExpansion
import sealmc.swe3tie.sealcore.platform.RegionizedTaskScheduler
import sealmc.swe3tie.sealcore.platform.ServerPlatform
import sealmc.swe3tie.sealcore.platform.TaskScheduler
import sealmc.swe3tie.sealcore.service.ServiceRegistry
import sealmc.swe3tie.sealcore.service.register
import sealmc.swe3tie.sealcore.storage.DataStore
import sealmc.swe3tie.sealcore.storage.Database
import sealmc.swe3tie.sealcore.storage.MigrationRunner
import sealmc.swe3tie.sealcore.storage.PlayerRepository
import sealmc.swe3tie.sealcore.storage.StorageMigrations
import sealmc.swe3tie.sealcore.util.runSuspendBlocking
import java.util.logging.Level

/**
 * SealCore entry point.
 *
 * Boot order matters: PacketEvents is started in [onLoad] so its channel
 * injector is in place before the first player connects, everything else is
 * wired in [onEnable] and torn down in reverse in [onDisable].
 */
class SealCore : JavaPlugin() {

    /**
     * Reads `config.yml`, the module files and the language files, and applies
     * modules in two phases so one bad file cannot half reload the server.
     */
    lateinit var configManager: ConfigManager
        private set

    val config: SealCoreConfig get() = configManager.config

    val messages: Messages get() = configManager.messages

    /** Registered feature modules, keyed by id. */
    val modules get() = configManager.modules

    lateinit var scheduler: TaskScheduler
        private set

    lateinit var database: Database
        private set

    lateinit var players: PlayerRepository
        private set

    lateinit var dataStore: DataStore
        private set

    lateinit var economy: EconomyService
        private set

    lateinit var placeholders: PlaceholderEngine
        private set

    lateinit var bridge: PacketBridge
        private set

    lateinit var gui: GuiManager
        private set

    lateinit var hud: HudManager
        private set

    val services: ServiceRegistry = ServiceRegistry()

    val platform: ServerPlatform by lazy { ServerPlatform.detect() }

    private var expansion: SealCoreExpansion? = null

    override fun onLoad() {
        bridge = PacketBridge(this, logger)
        bridge.load()
    }

    override fun onEnable() {
        // Channels are injected in onLoad; the managers come up here.
        bridge.init()
        loadConfiguration()
        configManager.loadModules()
        startStorage()
        startEconomy()
        startInterface()
        startCommands()
        startTasks()

        services.register(this)
        logger.info("SealCore enabled on $platform (Minecraft ${server.minecraftVersion}).")
    }

    private fun loadConfiguration() {
        configManager = ConfigManager(this)
        configManager.loadEngine()
        configManager.registerModules(featureModules())
    }

    /**
     * Every module SealCore ships. The registry reorders by
     * [SealModule.dependsOn], so the order here carries no meaning.
     *
     * Adding a feature means adding one class and one line to this list.
     */
    private fun featureModules(): List<SealModule<*>> = emptyList()

    /** Registers a migration for a module before it loads. */
    fun addMigration(migration: ModuleMigration) = configManager.addMigration(migration)

    private fun startStorage() {
        database = Database(config.storage, dataFolder, logger)
        runCatching {
            database.open()
            MigrationRunner(database, logger).apply(StorageMigrations.all(database.dialect))
            players = PlayerRepository(database)
            dataStore = DataStore(database)
            services.register(database)
            services.register(players)
        }.onFailure { error ->
            logger.log(Level.SEVERE, "Storage failed to start; continuing without persistence.", error)
        }
    }

    private fun startEconomy() {
        economy = EconomyService(config.economy, logger)
        val bridge = ExcellentEconomyBridge.createOrNull(logger)
        economy.install(bridge)
        services.register(economy)
    }

    private fun startInterface() {
        scheduler = RegionizedTaskScheduler(this)
        services.register<TaskScheduler>(scheduler)

        placeholders = PlaceholderEngine(economy)
        placeholders.installBuiltins()
        services.register(placeholders)

        if (bridge.isReady && config.gui.enabled) {
            bridge.registerListener(GuiPacketListener(guiManager()))
            logger.info("Packet GUI framework enabled.")
        } else {
            logger.warning("Packet GUI framework is disabled.")
        }

        if (bridge.isReady && config.hud.enabled) {
            hud = HudManager(bridge, scheduler, logger)
            hud.start(config.hud.updateIntervalTicks)
            services.register(hud)
            logger.info("HUD widgets enabled.")
        }
    }

    private fun startCommands() {
        val command = SealCoreCommand(this)
        val registered = getCommand("sealcore")
        if (registered == null) {
            logger.severe("The /sealcore command is missing from plugin.yml.")
            return
        }
        registered.setExecutor(command)
        registered.tabCompleter = command
    }

    /**
     * Re-reads config, the module files and the language file without a full
     * restart. Returns what each module did, so the caller can report it.
     */
    fun reloadPlugin(): ConfigManager.ReloadReport {
        val report = configManager.reload()
        registerExpansion()
        return report
    }

    private fun startTasks() {
        server.pluginManager.registerEvents(PlayerListener(this), this)

        val currencies = listOf(CurrencyKey.MONEY, CurrencyKey.SHARDS, CurrencyKey.COINS)
        // Balances are cached off the server thread so a HUD tick never blocks.
        scheduler.repeating(config.general.refreshIntervalTicks, BALANCE_REFRESH_TICKS) {
            runCatching { runSuspendBlocking { placeholders.refreshAllBalances(currencies) } }
                .onFailure { logger.log(Level.WARNING, "Balance refresh failed", it) }
        }

        scheduler.repeating(config.general.refreshIntervalTicks, config.general.refreshIntervalTicks) {
            if (!bridge.isReady) return@repeating
            runCatching {
                if (::gui.isInitialized) gui.refreshAll()
                if (::hud.isInitialized) hud.tick()
            }.onFailure { logger.log(Level.WARNING, "Interface refresh failed", it) }
        }
    }

    private fun guiManager(): GuiManager {
        if (!::gui.isInitialized) {
            gui = GuiManager(this, bridge, scheduler, placeholders, logger)
            services.register(gui)
        }
        return gui
    }

    override fun onDisable() {
        if (::configManager.isInitialized) configManager.modules.disableAll()
        if (::scheduler.isInitialized) scheduler.cancelAll()
        if (::gui.isInitialized) gui.closeAll()
        if (::hud.isInitialized) hud.clearAll()
        expansion?.let { runCatching { it.unregister() } }
        expansion = null
        if (::placeholders.isInitialized) placeholders.clearCache()
        if (::database.isInitialized) database.close()
        services.clear()
        bridge.terminate()
        logger.info("SealCore disabled.")
    }

    /** Registers the PlaceholderAPI expansion when that plugin is present. */
    fun registerExpansion() {
        if (expansion != null) return
        if (server.pluginManager.getPlugin("PlaceholderAPI") == null) return
        expansion = SealCoreExpansion(this, placeholders).also { runCatching { it.register() } }
    }

    val hasGui: Boolean get() = bridge.isReady && config.gui.enabled

    val hasHud: Boolean get() = bridge.isReady && config.hud.enabled

    val isStorageReady: Boolean get() = ::database.isInitialized && database.isOpen

    private companion object {
        const val BALANCE_REFRESH_TICKS = 20L * 30L
    }
}
