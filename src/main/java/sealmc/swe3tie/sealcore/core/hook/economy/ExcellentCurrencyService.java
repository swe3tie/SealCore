package sealmc.swe3tie.sealcore.core.hook.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult;
import sealmc.swe3tie.sealcore.core.config.CoreSettings.EconomySettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link CurrencyService} backed by ExcellentEconomy. This is the only class that touches
 * ExcellentEconomy types, so it must only be loaded when that plugin is installed.
 */
public final class ExcellentCurrencyService implements CurrencyService {

    private final ExcellentEconomyAPI api;
    private final Map<String, String> currencyIds;
    private final String defaultKey;

    private ExcellentCurrencyService(@NotNull ExcellentEconomyAPI api, @NotNull EconomySettings settings) {
        this.api = api;
        this.currencyIds = new LinkedHashMap<>(settings.currencies());
        this.defaultKey = this.currencyIds.containsKey(settings.defaultCurrency())
            ? settings.defaultCurrency()
            : this.currencyIds.keySet().stream().findFirst().orElse(settings.defaultCurrency());
    }

    /** @return the service, or {@code null} if ExcellentEconomy has not registered its API. */
    @Nullable
    public static ExcellentCurrencyService create(@NotNull EconomySettings settings) {
        ExcellentEconomyAPI api = Bukkit.getServicesManager().load(ExcellentEconomyAPI.class);
        return api == null ? null : new ExcellentCurrencyService(api, settings);
    }

    @Nullable
    private ExcellentCurrency currency(@NotNull String key) {
        String id = this.currencyIds.get(key);
        return id == null ? null : this.api.getCurrency(id);
    }

    @NotNull
    private static OperationContext context(@NotNull String reason) {
        return OperationContext.custom("SealCore:" + reason).silent();
    }

    @Override
    public @NotNull Set<String> keys() {
        return Collections.unmodifiableSet(this.currencyIds.keySet());
    }

    @Override
    public @NotNull String defaultKey() {
        return this.defaultKey;
    }

    @Override
    public boolean exists(@NotNull String key) {
        return this.currency(key) != null;
    }

    @Override
    public @NotNull String displayName(@NotNull String key) {
        ExcellentCurrency currency = this.currency(key);
        return currency == null ? key : currency.getName();
    }

    @Override
    public @NotNull String format(@NotNull String key, double amount) {
        ExcellentCurrency currency = this.currency(key);
        return currency == null ? String.valueOf(amount) : currency.format(amount);
    }

    @Override
    public @NotNull String formatCompact(@NotNull String key, double amount) {
        ExcellentCurrency currency = this.currency(key);
        return currency == null ? String.valueOf(amount) : currency.formatCompact(amount);
    }

    @Override
    public double balance(@NotNull Player player, @NotNull String key) {
        ExcellentCurrency currency = this.currency(key);
        return currency == null ? 0D : this.api.getBalance(player, currency);
    }

    @Override
    public @NotNull CompletableFuture<Double> balance(@NotNull UUID playerId, @NotNull String key) {
        ExcellentCurrency currency = this.currency(key);
        return currency == null ? CompletableFuture.completedFuture(0D) : this.api.getBalanceAsync(playerId, currency);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> deposit(@NotNull UUID playerId, @NotNull String key, double amount, @NotNull String reason) {
        ExcellentCurrency currency = this.currency(key);
        if (currency == null) return CompletableFuture.completedFuture(false);
        double value = currency.floorIfNeeded(amount);
        if (!(value > 0D)) return CompletableFuture.completedFuture(false);
        return this.api.depositAsync(playerId, currency, value, context(reason)).thenApply(OperationResult::success);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> withdraw(@NotNull UUID playerId, @NotNull String key, double amount, @NotNull String reason) {
        ExcellentCurrency currency = this.currency(key);
        if (currency == null) return CompletableFuture.completedFuture(false);
        double value = currency.floorIfNeeded(amount);
        if (!(value > 0D)) return CompletableFuture.completedFuture(false);

        // ExcellentEconomy's remove does not refuse overdrafts, so check first. Two concurrent
        // withdrawals can still race; callers that need strictness should serialise per player.
        return this.api.getBalanceAsync(playerId, currency).thenCompose(balance -> {
            if (balance < value) return CompletableFuture.completedFuture(false);
            return this.api.withdrawAsync(playerId, currency, value, context(reason)).thenApply(OperationResult::success);
        });
    }

    @Override
    public @NotNull CompletableFuture<TransferResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull String key,
                                                               double amount, @NotNull String reason) {
        ExcellentCurrency currency = this.currency(key);
        if (currency == null) return CompletableFuture.completedFuture(TransferResult.FAILED);
        double value = currency.floorIfNeeded(amount);
        if (!(value > 0D)) return CompletableFuture.completedFuture(TransferResult.FAILED);

        return this.api.getBalanceAsync(from, currency).thenCompose(balance -> {
            if (balance < value) return CompletableFuture.completedFuture(TransferResult.NOT_ENOUGH);
            return this.api.withdrawAsync(from, currency, value, context(reason)).thenCompose(taken -> {
                if (taken.failure()) return CompletableFuture.completedFuture(TransferResult.FAILED);
                return this.api.depositAsync(to, currency, value, context(reason)).thenCompose(given -> {
                    if (given.success()) return CompletableFuture.completedFuture(TransferResult.SUCCESS);
                    // Deposit failed: give the sender their money back.
                    return this.api.depositAsync(from, currency, value, context(reason + ":refund"))
                        .thenApply(refund -> TransferResult.FAILED);
                });
            });
        });
    }

    @Override
    public @NotNull Optional<List<LeaderboardEntry>> leaderboard(@NotNull String key) {
        ExcellentCurrency currency = this.currency(key);
        if (currency == null) return Optional.empty();
        return this.api.topManager().map(top -> {
            List<LeaderboardEntry> out = new ArrayList<>();
            top.getTopEntries(currency).forEach(e ->
                out.add(new LeaderboardEntry(e.getPosition(), e.getName(), e.getPlayerId(), e.getBalance())));
            return Collections.unmodifiableList(out);
        });
    }
}
