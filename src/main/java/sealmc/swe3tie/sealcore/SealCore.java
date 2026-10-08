package sealmc.swe3tie.sealcore;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.command.CommandService;
import sealmc.swe3tie.sealcore.core.command.SealCommand;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.config.ConfigService;
import sealmc.swe3tie.sealcore.core.config.CoreSettings;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.HookManager;
import sealmc.swe3tie.sealcore.core.lang.LangService;
import sealmc.swe3tie.sealcore.core.module.ModuleManager;
import sealmc.swe3tie.sealcore.core.scheduler.SealScheduler;
import sealmc.swe3tie.sealcore.core.storage.StorageService;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import java.util.logging.Level;

/** SealCore: all-in-one Economy SMP plugin for Paper and Folia. */
public final class SealCore extends JavaPlugin {

    private ConfigService config;
    private LangService lang;
    private SealScheduler scheduler;
    private StorageService storage;
    private HookManager hooks;
    private CommandService commands;
    private ModuleManager modules;

    @Override
    public void onEnable() {
        try {
            this.config = new ConfigService(this);
            this.config.load();

            this.scheduler = new SealScheduler(this);
            this.lang = new LangService(this);
            this.lang.load(this.config.settings());

            this.storage = new StorageService(this);
            this.storage.start(this.config.settings().storage());

            this.hooks = new HookManager(this);
            this.hooks.enable(this.config.settings());
            this.lang.setPlaceholderParser(this.hooks.placeholders()::parse);

            if (this.config.settings().economy().required() && !this.hooks.isAvailable(Hook.ECONOMY)) {
                throw new IllegalStateException("ExcellentEconomy is required (hooks.excellent-economy.required) but is not available");
            }
        } catch (Exception | LinkageError e) {
            this.getLogger().log(Level.SEVERE, "SealCore could not start: " + e.getMessage(), e);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.commands = new CommandService(this);
        this.commands.install();
        new SealCommand(this).register(this.commands);

        this.modules = new ModuleManager(this);
        this.modules.register(new EconomyModule(this));
        this.modules.freeze();
        this.modules.sync();

        this.getLogger().info("SealCore " + this.getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (this.modules != null) this.modules.disableAll();
        if (this.scheduler != null) this.scheduler.cancelAll();
        if (this.hooks != null) this.hooks.disable();
        if (this.storage != null) this.storage.close();
    }

    /**
     * Re-reads config.yml and languages and applies module toggles. If config.yml or a language file
     * is invalid, the previous state stays active and the error is thrown to the caller.
     * Storage settings are restart-only.
     */
    public void reload() throws ConfigException {
        this.config.load();
        this.lang.load(this.config.settings());
        this.hooks.enable(this.config.settings());
        this.modules.sync();
    }

    @NotNull
    public ConfigService config() {
        return this.config;
    }

    @NotNull
    public CoreSettings settings() {
        return this.config.settings();
    }

    @NotNull
    public LangService lang() {
        return this.lang;
    }

    @NotNull
    public SealScheduler scheduler() {
        return this.scheduler;
    }

    @NotNull
    public StorageService storage() {
        return this.storage;
    }

    @NotNull
    public HookManager hooks() {
        return this.hooks;
    }

    @NotNull
    public CommandService commands() {
        return this.commands;
    }

    @NotNull
    public ModuleManager modules() {
        return this.modules;
    }
}
