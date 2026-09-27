package sealmc.swe3tie.sealcore;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.java.JavaPlugin;
import sealmc.swe3tie.sealcore.command.SealCommandAdapter;
import sealmc.swe3tie.sealcore.command.SealCoreCommand;
import sealmc.swe3tie.sealcore.config.ConfigManager;
import sealmc.swe3tie.sealcore.config.Messages;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyService;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge;
import sealmc.swe3tie.sealcore.gui.GuiManager;
import sealmc.swe3tie.sealcore.gui.packet.GuiPacketListener;
import sealmc.swe3tie.sealcore.gui.packet.PacketBridge;
import sealmc.swe3tie.sealcore.hud.HudManager;
import sealmc.swe3tie.sealcore.listener.PlayerListener;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.ModuleMigration;
import sealmc.swe3tie.sealcore.module.ModuleRegistry;
import sealmc.swe3tie.sealcore.module.SealCommand;
import sealmc.swe3tie.sealcore.module.SealModule;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine;
import sealmc.swe3tie.sealcore.placeholder.SealCoreExpansion;
import sealmc.swe3tie.sealcore.platform.RegionizedTaskScheduler;
import sealmc.swe3tie.sealcore.platform.ServerPlatform;
import sealmc.swe3tie.sealcore.platform.TaskScheduler;
import sealmc.swe3tie.sealcore.service.ServiceRegistry;
import sealmc.swe3tie.sealcore.storage.DataStore;
import sealmc.swe3tie.sealcore.storage.Database;
import sealmc.swe3tie.sealcore.storage.MigrationRunner;
import sealmc.swe3tie.sealcore.storage.PlayerRepository;
import sealmc.swe3tie.sealcore.storage.StorageMigrations;
import sealmc.swe3tie.sealcore.text.Text;

/**
 * SealCore entry point.
 *
 * <p>Boot order matters: PacketEvents is started in {@link #onLoad()} so its channel
 * injector is in place before the first player connects, everything else is wired in
 * {@link #onEnable()} and torn down in reverse in {@link #onDisable()}.
 */
public final class SealCore extends JavaPlugin {

    private static final long BALANCE_REFRESH_TICKS = 20L * 30L;

    private final ServiceRegistry services = new ServiceRegistry();

    private ConfigManager configManager;
    private TaskScheduler scheduler;
    private Database database;
    private PlayerRepository players;
    private DataStore dataStore;
    private EconomyService economy;
    private PlaceholderEngine placeholders;
    private PacketBridge bridge;
    private GuiManager gui;
    private HudManager hud;
    private SealCoreExpansion expansion;
    private ServerPlatform platform;

    private boolean accentReported;

    /**
     * Reads {@code config.yml}, the module files and the language files, and applies
     * modules in two phases so one bad file cannot half reload the server.
     */
    public ConfigManager configManager() {
        return require(configManager, "configManager");
    }

    public SealCoreConfig config() {
        return configManager().config();
    }

    public Messages messages() {
        return configManager().messages();
    }

    /** Registered feature modules, keyed by id. */
    public ModuleRegistry modules() {
        return configManager().modules();
    }

    public TaskScheduler scheduler() {
        return require(scheduler, "scheduler");
    }

    public Database database() {
        return require(database, "database");
    }

    public PlayerRepository players() {
        return require(players, "players");
    }

    public DataStore dataStore() {
        return require(dataStore, "dataStore");
    }

    public EconomyService economy() {
        return require(economy, "economy");
    }

    public PlaceholderEngine placeholders() {
        return require(placeholders, "placeholders");
    }

    public PacketBridge bridge() {
        return require(bridge, "bridge");
    }

    public GuiManager gui() {
        return require(gui, "gui");
    }

    public HudManager hud() {
        return require(hud, "hud");
    }

    public ServiceRegistry services() {
        return services;
    }

    public ServerPlatform platform() {
        ServerPlatform detected = platform;
        if (detected == null) {
            detected = ServerPlatform.detect();
            platform = detected;
        }
        return detected;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " was used before SealCore finished enabling");
        }
        return value;
    }

    @Override
    public void onLoad() {
        bridge = new PacketBridge(this, getLogger());
        bridge.load();
    }

    @Override
    public void onEnable() {
        // Channels are injected in onLoad; the managers come up here.
        bridge().init();
        loadConfiguration();
        configManager().loadModules();
        startStorage();
        startEconomy();
        startInterface();
        startCommands();
        startTasks();

        services.register(SealCore.class, this);
        getLogger().info("SealCore enabled on " + platform() + " (Minecraft " + getServer().getMinecraftVersion() + ").");
    }

    private void loadConfiguration() {
        configManager = new ConfigManager(this);
        configManager.loadEngine();
        applyAccent();
        configManager.registerModules(featureModules());
    }

    /**
     * The {@code <accent>} MiniMessage tag, from {@code general.accent-color}. A
     * value that is not a hex colour is reported once and the shipped default stays.
     */
    private void applyAccent() {
        if (Text.setAccent(config().general().accentColor()) || accentReported) {
            return;
        }
        getLogger().warning("general.accent-color '" + config().general().accentColor()
            + "' is not a hex colour; using " + Text.DEFAULT_ACCENT + ".");
        accentReported = true;
    }

    /**
     * Every module SealCore ships. The registry reorders by
     * {@link SealModule#dependsOn()}, so the order here carries no meaning.
     *
     * <p>Adding a feature means adding one class and one line to this list.
     */
    private static List<SealModule<?>> featureModules() {
        return List.of(new EconomyModule());
    }

    /** Registers a migration for a module before it loads. */
    public void addMigration(ModuleMigration migration) {
        configManager().addMigration(migration);
    }

    private void startStorage() {
        database = new Database(config().storage(), getDataFolder(), getLogger());
        try {
            database.open();
            new MigrationRunner(database, getLogger()).apply(StorageMigrations.all(database.dialect()));
            players = new PlayerRepository(database);
            dataStore = new DataStore(database);
            services.register(Database.class, database);
            services.register(PlayerRepository.class, players);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Storage failed to start; continuing without persistence.", failure);
        }
    }

    private void startEconomy() {
        economy = new EconomyService(config().economy(), getLogger());
        economy.install(ExcellentEconomyBridge.createOrNull(getLogger()));
        services.register(EconomyService.class, economy);
    }

    private void startInterface() {
        scheduler = new RegionizedTaskScheduler(this);
        services.register(TaskScheduler.class, scheduler);

        placeholders = new PlaceholderEngine(economy);
        placeholders.installBuiltins();
        services.register(PlaceholderEngine.class, placeholders);

        PacketBridge packets = bridge();
        if (packets.isReady() && config().gui().enabled()) {
            packets.registerListener(new GuiPacketListener(guiManager()));
            getLogger().info("Packet GUI framework enabled.");
        } else {
            getLogger().warning("Packet GUI framework is disabled.");
        }

        if (packets.isReady() && config().hud().enabled()) {
            hud = new HudManager(packets, scheduler, getLogger());
            hud.start(config().hud().updateIntervalTicks());
            services.register(HudManager.class, hud);
            getLogger().info("HUD widgets enabled.");
        }
    }

    private void startCommands() {
        var command = new SealCoreCommand(this);
        var registered = getCommand("sealcore");
        if (registered == null) {
            getLogger().severe("The /sealcore command is missing from plugin.yml.");
        } else {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }

        var context = moduleContext();
        for (SealModule<?> module : modules().registered()) {
            for (SealCommand sealCommand : module.commands(context)) {
                var bound = getCommand(sealCommand.name());
                if (bound == null) {
                    getLogger().severe("The /" + sealCommand.name()
                        + " command is missing from plugin.yml (module " + module.id() + ").");
                    continue;
                }
                var adapter = new SealCommandAdapter(sealCommand, context, getLogger());
                bound.setExecutor(adapter);
                bound.setTabCompleter(adapter);
                reportShadowed(bound);
            }
        }
    }

    /**
     * Warns when another plugin answers to one of this plugin's names.
     *
     * <p>A name declared in {@code plugin.yml} is registered while the plugin loads,
     * and a plugin that registers later can take it. The server builds its command
     * tree at that point, so the loser cannot get the name back by writing the
     * command map afterwards; the name has to be left alone by the other plugin, and
     * the fork of that plugin is what does it. All that is left to do here is say so,
     * because otherwise a command that silently answers from somewhere else looks
     * like a bug in this plugin.
     */
    private void reportShadowed(PluginCommand command) {
        var known = getServer().getCommandMap().getKnownCommands();
        for (String name : new String[] {command.getName()}) {
            var owner = known.get(name.toLowerCase(java.util.Locale.ROOT));
            if (owner instanceof PluginIdentifiableCommand held && held.getPlugin() != this) {
                getLogger().warning("The /" + name + " command is answered by " + held.getPlugin().getName()
                    + ", not by SealCore. SealCore is the intended owner; remove that plugin's"
                    + " command declaration or the name will not come back.");
            }
        }
    }

    /** The services feature modules are allowed to reach. */
    private ModuleContext moduleContext() {
        return new ModuleContext(
            messages(),
            economy(),
            scheduler(),
            placeholders(),
            modules(),
            () -> isStorageReady() ? players() : null,
            getLogger());
    }

    /**
     * Re-reads config, the module files and the language file without a full restart.
     * Returns what each module did, so the caller can report it.
     */
    public ConfigManager.ReloadReport reloadPlugin() {
        ConfigManager.ReloadReport report = configManager().reload();
        registerExpansion();
        // Command trees are built from the specs that just loaded, so they are
        // rebound rather than left pointing at the values from boot.
        startCommands();
        return report;
    }

    private void startTasks() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        List<CurrencyKey> currencies = List.of(CurrencyKey.MONEY, CurrencyKey.SHARDS, CurrencyKey.COINS);
        // Balances are cached off the server thread so a HUD tick never blocks.
        scheduler().repeating(config().general().refreshIntervalTicks(), BALANCE_REFRESH_TICKS, () -> {
            try {
                placeholders().refreshAllBalances(currencies);
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Balance refresh failed", failure);
            }
        });

        scheduler().repeating(config().general().refreshIntervalTicks(), config().general().refreshIntervalTicks(), () -> {
            if (!bridge().isReady()) {
                return;
            }
            try {
                if (gui != null) {
                    gui.refreshAll();
                }
                if (hud != null) {
                    hud.tick();
                }
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Interface refresh failed", failure);
            }
        });
    }

    private GuiManager guiManager() {
        GuiManager manager = gui;
        if (manager == null) {
            manager = new GuiManager(this, bridge(), scheduler(), placeholders(), getLogger());
            gui = manager;
            services.register(GuiManager.class, manager);
        }
        return manager;
    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.modules().disableAll();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (gui != null) {
            gui.closeAll();
        }
        if (hud != null) {
            hud.clearAll();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Failed to unregister the PlaceholderAPI expansion", failure);
            }
            expansion = null;
        }
        if (placeholders != null) {
            placeholders.clearCache();
        }
        if (database != null) {
            database.close();
        }
        services.clear();
        if (bridge != null) {
            bridge.terminate();
        }
        getLogger().info("SealCore disabled.");
    }

    /** Registers the PlaceholderAPI expansion when that plugin is present. */
    public void registerExpansion() {
        if (expansion != null) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        var registered = new SealCoreExpansion(this, placeholders());
        expansion = registered;
        try {
            registered.register();
        } catch (RuntimeException failure) {
            getLogger().log(Level.WARNING, "Failed to register the PlaceholderAPI expansion", failure);
        }
    }

    public boolean hasGui() {
        return bridge().isReady() && config().gui().enabled();
    }

    public boolean hasHud() {
        return bridge().isReady() && config().hud().enabled();
    }

    public boolean isStorageReady() {
        return database != null && database.isOpen();
    }
}
