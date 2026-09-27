package su.nightexpress.excellenteconomy.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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
import su.nightexpress.nightcore.NightPlugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ExcellentEconomyAPI {

    @NonNull
    NightPlugin plugin();

    @NonNull
    CurrencyRegistry currencyRegistry();

    @NonNull
    CommandManager commandManager();

    @NonNull
    CurrencyManager currencyManager();

    @NonNull
    DataHandler dataHandler();

    @NonNull
    Optional<MigrationManager> migrationManager();

    @NonNull
    Optional<TopManager> topManager();

    @NonNull
    UserManager userManager();

    boolean canPerformOperations();

    boolean hasCurrency(@NonNull String id);

    @NonNull
    Set<ExcellentCurrency> getCurrencies();

    @Nullable
    ExcellentCurrency getCurrency(@NonNull String id);

    @NonNull
    Optional<ExcellentCurrency> currencyById(@NonNull String id);


    @NonNull
    CompletableFuture<Double> getBalanceAsync(@NonNull UUID playerId, @NonNull String currencyName);

    @NonNull
    CompletableFuture<Double> getBalanceAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency);

    double getBalance(@NonNull Player player, @NonNull String currencyId);

    double getBalance(@NonNull Player player, @NonNull ExcellentCurrency currency);


    @NonNull
    CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull String currencyName,
                                                    double amount);

    @NonNull
    CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull String currencyName, double amount,
                                                    @NonNull OperationContext context);

    @NonNull
    CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                    double amount);

    @NonNull
    CompletableFuture<OperationResult> depositAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                    double amount, @NonNull OperationContext context);

    boolean deposit(@NonNull Player player, @NonNull String currencyId, double amount);

    boolean deposit(@NonNull Player player, @NonNull String currencyId, double amount,
                    @NonNull OperationContext context);

    boolean deposit(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount);

    boolean deposit(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                    @NonNull OperationContext context);


    @NonNull
    CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull String currencyId, double amount);

    @NonNull
    CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull String currencyId, double amount,
                                                     @NonNull OperationContext context);

    @NonNull
    CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                     double amount);

    @NonNull
    CompletableFuture<OperationResult> withdrawAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                     double amount, @NonNull OperationContext context);

    boolean withdraw(@NonNull Player player, @NonNull String currencyId, double amount);

    boolean withdraw(@NonNull Player player, @NonNull String currencyId, double amount,
                     @NonNull OperationContext context);

    boolean withdraw(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount);

    boolean withdraw(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                     @NonNull OperationContext context);


    @NonNull
    CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                       double amount);

    @NonNull
    CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull String currencyId,
                                                       double amount, @NonNull OperationContext context);

    @NonNull
    CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                       double amount);

    @NonNull
    CompletableFuture<OperationResult> setBalanceAsync(@NonNull UUID playerId, @NonNull ExcellentCurrency currency,
                                                       double amount, @NonNull OperationContext context);

    boolean setBalance(@NonNull Player player, @NonNull String currencyId, double amount);

    boolean setBalance(@NonNull Player player, @NonNull String currencyId, double amount,
                       @NonNull OperationContext context);

    boolean setBalance(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount);

    boolean setBalance(@NonNull Player player, @NonNull ExcellentCurrency currency, double amount,
                       @NonNull OperationContext context);


    @NonNull
    CoinsUser getCachedUserData(@NonNull Player player);

    @NonNull
    Optional<CoinsUser> getCachedUserData(@NonNull String name);

    @NonNull
    Optional<CoinsUser> getCachedUserData(@NonNull UUID uuid);

    @NonNull
    CompletableFuture<Optional<CoinsUser>> loadUserDataByName(@NonNull String name);

    @NonNull
    CompletableFuture<Optional<CoinsUser>> loadUserDataById(@NonNull UUID uuid);
}
