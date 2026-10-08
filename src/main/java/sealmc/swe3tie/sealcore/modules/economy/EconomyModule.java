package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.module.SealModule;
import sealmc.swe3tie.sealcore.core.storage.StorageService;
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
    private final BalanceCommand balanceCommand = new BalanceCommand(this);
    private final PayCommand payCommand = new PayCommand(this);
    private final SellCommand sellCommand = new SellCommand(this);

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
            })
        ));

        this.priceService.load(this.config().getConfigurationSection("prices"));

        this.listen(new EconomyJoinListener(this));
        this.listen(this.sellCommand);

        this.balanceCommand.register();
        this.payCommand.register();
        this.sellCommand.register();

        this.registerPlaceholders(this::resolvePlaceholder);
    }

    @Override
    protected void onReload() {
        this.priceService.load(this.config().getConfigurationSection("prices"));
    }

    @Override
    protected void onDisable() {
        this.sellCommand.closeAll();
        this.payCommand.clearCooldowns();
    }

    @NotNull
    public ItemPriceService priceService() {
        return this.priceService;
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
            case "balance" -> MoneyFormat.formatStandard(balance);
            case "balance_compact" -> MoneyFormat.formatCompact(balance);
            default -> null;
        };
    }
}
