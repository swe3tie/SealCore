package su.nightexpress.excellenteconomy.migration;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlaceholders;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.command.CommandManager;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.excellenteconomy.hook.HookPlugin;
import su.nightexpress.excellenteconomy.migration.command.MigrationCommand;
import su.nightexpress.excellenteconomy.migration.impl.PlayerPointsMigrator;
import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.UserManager;
import su.nightexpress.nightcore.manager.SimpleManager;
import su.nightexpress.nightcore.util.LowerCase;
import su.nightexpress.nightcore.util.Plugins;

import java.util.*;
import java.util.function.Supplier;

public class MigrationManager extends SimpleManager<EconomyPlugin> {

    private final CurrencyRegistry currencyRegistry;
    private final CurrencyManager  currencyManager;
    private final CommandManager   commandManager;
    private final UserManager      userManager;

    private final Map<String, Migrator> migrators;

    public MigrationManager(@NonNull EconomyPlugin plugin,
                            @NonNull CurrencyRegistry currencyRegistry,
                            @NonNull CurrencyManager currencyManager,
                            @NonNull CommandManager commandManager,
                            @NonNull UserManager userManager) {
        super(plugin);
        this.userManager = userManager;
        this.commandManager = commandManager;
        this.currencyRegistry = currencyRegistry;
        this.currencyManager = currencyManager;
        this.migrators = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        this.commandManager.addPluginCommand(MigrationCommand.create(this.currencyRegistry, this));

        this.registerMigrator(HookPlugin.PLAYER_POINTS, () -> new PlayerPointsMigrator(this.plugin));

        // Schedule to ensure 3rd party economy plugins are loaded.
        this.plugin.runTask(() -> {
            if (!this.currencyRegistry.hasPrimary()) {
                this.registerMigrator(HookPlugin.VAULT, () -> MigratorFactory.forVault(this.plugin));
            }
        });
    }

    @Override
    protected void onShutdown() {
        this.migrators.clear();
    }

    public boolean registerMigrator(@NonNull String name, @NonNull Supplier<Migrator> supplier) {
        if (!Plugins.isInstalled(name)) return false;

        Migrator migrator = supplier.get();
        if (migrator == null) return false;

        this.migrators.put(LowerCase.INTERNAL.apply(migrator.getName()), migrator);
        this.plugin.info("Available balance data migration from " + migrator.getName() + ".");

        return true;
    }

    public boolean startMigration(@NonNull CommandSender sender, @NonNull String name,
                                  @NonNull ExcellentCurrency currency) {
        if (!this.currencyManager.canPerformOperations()) {
            Lang.MIGRATION_START_BLOCKED.message().send(sender);
            return false;
        }

        Migrator migrator = this.getMigrator(name);
        if (migrator == null) {
            Lang.MIGRATION_START_BAD_PLUGIN.message().send(sender);
            return false;
        }

        if (!migrator.canMigrate(currency)) {
            Lang.MIGRATION_START_BAD_CURRENCY.message().sendWith(sender, builder -> builder
                .with(EconomyPlaceholders.GENERIC_NAME, migrator::getName)
                .with(currency.placeholders())
            );
            return false;
        }

        this.plugin.runTaskAsync(() -> {
            this.currencyManager.disableOperations();
            Lang.MIGRATION_STARTED.message().sendWith(sender, builder -> builder.with(EconomyPlaceholders.GENERIC_NAME,
                migrator::getName));
            this.migrate(migrator, currency);
            Lang.MIGRATION_COMPLETED.message().sendWith(sender, builder -> builder.with(
                EconomyPlaceholders.GENERIC_NAME, migrator::getName));
            this.currencyManager.allowOperations();
        });

        return true;
    }

    public void migrate(@NonNull Migrator migrator, @NonNull ExcellentCurrency currency) {
        Map<OfflinePlayer, Double> balances = migrator.getBalances(currency);
        balances.forEach((player, amount) -> {
            String name = player.getName();
            if (name == null) return;

            UUID uuid = player.getUniqueId();
            CoinsUser user = this.userManager.getOrFetch(uuid).orElse(null);
            if (user == null) {
                user = this.userManager.create(uuid, name);
                this.userManager.getDataAccessor().insert(user);
            }

            user.setBalance(currency, amount);
            user.markDirty();
        });
    }

    @NonNull
    public List<String> getMigratorNames() {
        return new ArrayList<>(this.migrators.keySet());
    }

    @NonNull
    public Map<String, Migrator> getMigratorMap() {
        return this.migrators;
    }

    @Nullable
    public Migrator getMigrator(@NonNull String name) {
        return this.migrators.get(LowerCase.INTERNAL.apply(name));
    }
}
