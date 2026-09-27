package su.nightexpress.excellenteconomy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult;
import su.nightexpress.excellenteconomy.command.CommandManager;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.excellenteconomy.data.DataHandler;
import su.nightexpress.excellenteconomy.migration.MigrationManager;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.UserManager;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ExcellentEconomyProvider implements ExcellentEconomyAPI {

    private final EconomyPlugin    plugin;
    private final OperationContext defaultContext;

    public ExcellentEconomyProvider(@NonNull EconomyPlugin plugin) {
        this.plugin = plugin;
        this.defaultContext = OperationContext.custom("API").silentFor(NotificationTarget.USER,
            NotificationTarget.EXECUTOR, NotificationTarget.CONSOLE_LOGGER);
    }

    @Override
    @NonNull
    public EconomyPlugin plugin() {
        return this.plugin;
    }

    @Override
    @NonNull
    public CurrencyRegistry currencyRegistry() {
        return this.plugin.currencyRegistry;
    }

    @Override
    @NonNull
    public CommandManager commandManager() {
        return this.plugin.commandManager;
    }

    @Override
    @NonNull
    public CurrencyManager currencyManager() {
        return this.plugin.currencyManager;
    }

    @Override
    @NonNull
    public DataHandler dataHandler() {
        return this.plugin.dataHandler;
    }

    @Override
    @NonNull
    public Optional<MigrationManager> migrationManager() {
        return Optional.ofNullable(this.plugin.migrationManager);
    }

    @Override
    @NonNull
    public Optional<TopManager> topManager() {
        return Optional.ofNullable(this.plugin.topManager);
    }

    @Override
    @NonNull
    public UserManager userManager() {
        return this.plugin.userManager;
    }

    @Override
    public boolean canPerformOperations() {
        return this.currencyManager().canPerformOperations();
    }

    @Override
    public boolean hasCurrency(@NonNull String id) {
        return this.currencyRegistry().isRegistered(id);
    }

    @Override
    @NonNull
    public Set<ExcellentCurrency> getCurrencies() {
        return this.currencyRegistry().getCurrencies();
    }

    @Override
    @Nullable
    public ExcellentCurrency getCurrency(@NonNull String id) {
        return this.currencyRegistry().getById(id);
    }

    @Override
    @NonNull
    public Optional<ExcellentCurrency> currencyById(@NonNull String id) {
        return Optional.ofNullable(this.getCurrency(id));
    }


    @Override
    @NonNull
    public CompletableFuture<Double> getBalanceAsync(@NonNull UUID playerId, @NonNull String currencyName) {
        return this.currencyById(currencyName).map(currency -> this.getBalanceAsync(playerId, currency)).orElse(
            CompletableFuture.completedFuture(0D));
    }

    @Override
    @NonNull
    public CompletableFuture<Double> getBalanceAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency) {
        return this.userManager().loadByIdAsync(playerId).thenApply(opt -> opt.map(user -> user.getBalance(currency))
            .orElse(0D));
    }

    @Override
    public double getBalance(@NonNull Player player, @NonNull String currencyId) {
        return this.currencyById(currencyId).map(currency -> this.getBalance(player, currency)).orElse(0D);
    }

    @Override
    public double getBalance(@NonNull Player player, @NonNull ExcellentCurrency currency) {
        return this.getCachedUserData(player).getBalance(currency);
    }


    @Override
    @NonNull
    public CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull String currencyName,
                                                           double amount) {
        return this.depositAsync(playerId, currencyName, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull String currencyName,
                                                           double amount, @NonNull OperationContext context) {
        return this.currencyById(currencyName).map(currency -> this.depositAsync(playerId, currency, amount, context))
            .orElse(CompletableFuture.completedFuture(OperationResult.FAILURE));
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                           double amount) {
        return this.depositAsync(playerId, currency, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                           double amount, @NonNull OperationContext context) {
        return this.userManager().loadByIdAsync(playerId)
            .thenApply(opt -> opt.map(user -> this.currencyManager().give(context, user, currency, amount)).orElse(
                OperationResult.FAILURE));
    }

    @Override
    public boolean deposit(@NonNull Player player, @NonNull String currencyId, double amount) {
        return this.deposit(player, currencyId, amount, this.defaultContext);
    }

    @Override
    public boolean deposit(@NonNull Player player, @NonNull String currencyId, double amount,
                           @NonNull OperationContext context) {
        return this.currencyById(currencyId).map(currency -> this.deposit(player, currency, amount, context)).orElse(
            false);
    }

    @Override
    public boolean deposit(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount) {
        return this.deposit(player, currency, amount, this.defaultContext);
    }

    @Override
    public boolean deposit(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                           @NonNull OperationContext context) {
        return this.currencyManager().give(context, player, currency, amount) == OperationResult.SUCCESS;
    }


    @Override
    @NonNull
    public CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                            double amount) {
        return this.withdrawAsync(playerId, currencyId, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                            double amount, @NonNull OperationContext context) {
        return this.currencyById(currencyId).map(currency -> this.withdrawAsync(playerId, currency, amount, context))
            .orElse(CompletableFuture.completedFuture(OperationResult.FAILURE));
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                            double amount) {
        return this.withdrawAsync(playerId, currency, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                            double amount, @NonNull OperationContext context) {
        return this.userManager().loadByIdAsync(playerId)
            .thenApply(opt -> opt.map(user -> this.currencyManager().remove(context, user, currency, amount)).orElse(
                OperationResult.FAILURE));
    }

    @Override
    public boolean withdraw(@NonNull Player player, @NonNull String currencyId, double amount) {
        return this.withdraw(player, currencyId, amount, this.defaultContext);
    }

    @Override
    public boolean withdraw(@NonNull Player player, @NonNull String currencyId, double amount,
                            @NonNull OperationContext context) {
        return this.currencyById(currencyId).map(currency -> this.withdraw(player, currency, amount, context)).orElse(
            false);
    }

    @Override
    public boolean withdraw(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount) {
        return this.withdraw(player, currency, amount, this.defaultContext);
    }

    @Override
    public boolean withdraw(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                            @NonNull OperationContext context) {
        return this.currencyManager().remove(context, player, currency, amount) == OperationResult.SUCCESS;
    }


    @Override
    @NonNull
    public CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                              double amount) {
        return this.setBalanceAsync(playerId, currencyId, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                              double amount, @NonNull OperationContext context) {
        return this.currencyById(currencyId).map(currency -> this.setBalanceAsync(playerId, currency, amount, context))
            .orElse(CompletableFuture.completedFuture(OperationResult.FAILURE));
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId,
                                                              @NonNull ExcellentCurrency currency, double amount) {
        return this.setBalanceAsync(playerId, currency, amount, this.defaultContext);
    }

    @Override
    @NonNull
    public CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId,
                                                              @NonNull ExcellentCurrency currency, double amount,
                                                              @NonNull OperationContext context) {
        return this.userManager().loadByIdAsync(playerId)
            .thenApply(opt -> opt.map(user -> this.currencyManager().set(context, user, currency, amount)).orElse(
                OperationResult.FAILURE));
    }

    @Override
    public boolean setBalance(@NonNull Player player, @NonNull String currencyId, double amount) {
        return this.setBalance(player, currencyId, amount, this.defaultContext);
    }

    @Override
    public boolean setBalance(@NonNull Player player, @NonNull String currencyId, double amount,
                              @NonNull OperationContext context) {
        return this.currencyById(currencyId).map(currency -> this.setBalance(player, currency, amount, context)).orElse(
            false);
    }

    @Override
    public boolean setBalance(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount) {
        return this.setBalance(player, currency, amount, this.defaultContext);
    }

    @Override
    public boolean setBalance(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                              @NonNull OperationContext context) {
        return this.currencyManager().set(context, player, currency, amount) == OperationResult.SUCCESS;
    }


    @Override
    @NonNull
    public CoinsUser getCachedUserData(@NonNull Player player) {
        return this.userManager().getOrFetch(player);
    }

    @Override
    @NonNull
    public Optional<CoinsUser> getCachedUserData(@NonNull UUID playerId) {
        return this.userManager().getRepository().getById(playerId);
    }

    @Override
    @NonNull
    public Optional<CoinsUser> getCachedUserData(@NonNull String playerName) {
        return this.userManager().getRepository().getByName(playerName);
    }

    @Override
    @NonNull
    public CompletableFuture<Optional<CoinsUser>> loadUserDataByName(@NonNull String name) {
        return this.userManager().loadByNameAsync(name);
    }

    @Override
    @NonNull
    public CompletableFuture<Optional<CoinsUser>> loadUserDataById(@NonNull UUID uuid) {
        return this.userManager().loadByIdAsync(uuid);
    }
}
