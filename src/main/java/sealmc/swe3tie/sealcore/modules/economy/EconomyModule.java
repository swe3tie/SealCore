package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import sealmc.swe3tie.sealcore.core.config.YamlFile;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.module.SealModule;
import sealmc.swe3tie.sealcore.core.storage.StorageService;
import sealmc.swe3tie.sealcore.modules.economy.gui.PaginationConfig;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellGuiConfig;
import sealmc.swe3tie.sealcore.modules.economy.gui.WorthGuiConfig;
import sealmc.swe3tie.sealcore.modules.economy.packet.WorthInventoryListener;
import sealmc.swe3tie.sealcore.modules.economy.packet.WorthLoreConfig;
import sealmc.swe3tie.sealcore.modules.economy.packet.WorthPacketListener;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.sql.Statement;
import java.util.List;
import java.util.Set;

/**
 * Economy module integrating with ExcellentEconomy.
 * Features: {@code /balance [player]} (alias {@code /bal}), {@code /pay <player> <amount>}, and {@code /sell}.
 */
public final class EconomyModule extends SealModule {

    private final ItemPriceService priceService = new ItemPriceService();
    private final YamlFile pricesFile = this.resolveFile("prices.yml");
    private final YamlFile sellGuiFile = this.resolveGuiFile("sell-gui.yml");
    private final YamlFile multiplierGuiFile = this.resolveGuiFile("sell-multiplier.yml");
    private final YamlFile worthGuiFile = this.resolveGuiFile("worth-gui.yml");
    private final BalanceCommand balanceCommand = new BalanceCommand(this);
    private final PayCommand payCommand = new PayCommand(this);
    private final SellCommand sellCommand = new SellCommand(this);
    private final WorthCommand worthCommand = new WorthCommand(this);
    private volatile ProgressStore progressStore;
    private volatile SellGuiConfig guiConfig;
    private volatile WorthGuiConfig worthGuiConfig;
    private volatile WorthLoreConfig worthLoreConfig = WorthLoreConfig.load(null);
    private volatile PaginationConfig paginationConfig = PaginationConfig.DEFAULT;
    private WorthPacketListener packetListener;

    public EconomyModule(@NotNull SealCore core) {
        super(core, "economy");
    }

    @Override
    public @NotNull Set<Hook> requiredHooks() {
        return Set.of(Hook.ECONOMY);
    }

    @Override
    protected void onEnable() throws Exception {
        // Schema migrations for offline payment notifications
        this.core.storage().migrate("economy", List.of(
            new StorageService.Migration(1, (connection, storage) -> {
                String table = storage.table("economy_notifications");
                String idCol = storage.dialect() == StorageService.Dialect.MYSQL
                    ? "id BIGINT AUTO_INCREMENT PRIMARY KEY"
                    : "id INTEGER PRIMARY KEY AUTOINCREMENT";
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + idCol + ", "
                        + "receiver_uuid VARCHAR(36) NOT NULL, "
                        + "sender_name VARCHAR(64) NOT NULL, "
                        + "amount DOUBLE NOT NULL, "
                        + "created_at BIGINT NOT NULL)");
                }
            }),
            new StorageService.Migration(2, (connection, storage) -> {
                String table = storage.table("economy_sell_progress");
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "player_uuid VARCHAR(36) NOT NULL, "
                        + "category VARCHAR(32) NOT NULL, "
                        + "paid DOUBLE NOT NULL, "
                        + "PRIMARY KEY (player_uuid, category))");
                }
            })
        ));
        this.progressStore = new ProgressStore(this.core.storage());
        this.sellCommand.setProgressStore(this.progressStore);

        this.loadEconomyFiles();

        this.listen(new EconomyJoinListener(this));
        this.listen(this.sellCommand);
        this.listen(this.worthCommand);

        this.balanceCommand.register();
        this.payCommand.register();
        this.sellCommand.register();
        this.worthCommand.register();

        if (this.core.hooks().isAvailable(Hook.PACKETEVENTS)) {
            try {
                this.packetListener = new WorthPacketListener(this.priceService, this.worthLoreConfig);
                this.packetListener.register();
                this.listen(new WorthInventoryListener(this));
            } catch (LinkageError | RuntimeException e) {
                this.core.getLogger().log(java.util.logging.Level.WARNING, "Could not register PacketEvents worth listener", e);
            }
        }

        this.registerPlaceholders(this::resolvePlaceholder);
    }

    @Override
    protected void onReload() throws Exception {
        this.loadEconomyFiles();
    }

    private void loadEconomyFiles() throws ConfigException {
        this.priceService.load(this.pricesFile.load().getConfigurationSection("prices"));

        ConfigurationSection worthLoreSection = this.config().getConfigurationSection("worth-lore");
        if (worthLoreSection == null) {
            worthLoreSection = this.core.config().mainFile().load().getConfigurationSection("economy.worth-lore");
        }
        if (worthLoreSection == null) {
            worthLoreSection = this.core.config().mainFile().load().getConfigurationSection("worth-lore");
        }
        this.worthLoreConfig = WorthLoreConfig.load(worthLoreSection);
        if (this.packetListener != null) {
            this.packetListener.setConfig(this.worthLoreConfig);
        }

        ConfigurationSection paginationSection = this.config().getConfigurationSection("pagination");
        if (paginationSection == null) {
            paginationSection = this.core.config().mainFile().load().getConfigurationSection("economy.pagination");
        }
        if (paginationSection == null) {
            paginationSection = this.core.config().mainFile().load().getConfigurationSection("pagination");
        }
        this.paginationConfig = PaginationConfig.load(paginationSection);

        this.guiConfig = SellGuiConfig.load(this.sellGuiFile.load(), this.multiplierGuiFile.load(), this.paginationConfig);
        this.worthGuiConfig = WorthGuiConfig.load(this.worthGuiFile.load(), this.paginationConfig);
    }

    private YamlFile resolveGuiFile(String filename) {
        java.nio.file.Path direct = this.core().getDataFolder().toPath().resolve("modules/economy/" + filename);
        if (java.nio.file.Files.exists(direct)) {
            return this.resolveFile(filename);
        }
        return this.resolveFile("gui/" + filename);
    }

    @Override
    protected void onDisable() {
        this.sellCommand.closeAll();
        this.worthCommand.closeAll();
        if (this.packetListener != null) {
            try {
                this.packetListener.unregister();
            } catch (LinkageError | RuntimeException ignored) {
            }
            this.packetListener = null;
        }
        this.payCommand.clearCooldowns();
        ProgressStore store = this.progressStore;
        if (store != null) store.clear();
        this.progressStore = null;
    }

    @NotNull
    public ProgressStore progressStore() {
        ProgressStore store = this.progressStore;
        if (store == null) throw new IllegalStateException("progress store not loaded");
        return store;
    }

    @NotNull
    public ItemPriceService priceService() {
        return this.priceService;
    }

    @NotNull
    public SellGuiConfig guiConfig() {
        SellGuiConfig current = this.guiConfig;
        if (current == null) throw new IllegalStateException("sell GUI config not loaded");
        return current;
    }

    @NotNull
    public WorthGuiConfig worthGuiConfig() {
        WorthGuiConfig current = this.worthGuiConfig;
        if (current == null) return WorthGuiConfig.load(null);
        return current;
    }

    @NotNull
    public WorthLoreConfig worthLoreConfig() {
        return this.worthLoreConfig;
    }

    @NotNull
    public PaginationConfig paginationConfig() {
        return this.paginationConfig;
    }

    @Nullable
    CurrencyService currency() {
        return this.core.hooks().currency();
    }

    @Nullable
    private String resolvePlaceholder(@Nullable OfflinePlayer player, @NotNull String params) {
        CurrencyService currency = this.currency();
        if (currency == null || player == null) return null;

        Player online = player.getPlayer();
        double balance = online != null ? currency.balance(online, "money") : 0D;

        return switch (params) {
            case "balance", "money" -> MoneyFormat.formatStandard(balance);
            case "balance_compact", "balance_formatted", "money_formatted" -> MoneyFormat.formatCompact(balance);
            default -> null;
        };
    }
}
