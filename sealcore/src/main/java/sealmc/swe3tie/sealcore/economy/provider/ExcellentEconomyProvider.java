package sealmc.swe3tie.sealcore.economy.provider;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyProvider;
import sealmc.swe3tie.sealcore.economy.EconomyResult;

/**
 * {@link EconomyProvider} backed by ExcellentEconomy.
 *
 * <p>Amounts are validated here so an obviously bad request never reaches the
 * provider, and a failed withdrawal during a transfer refunds the sender
 * instead of minting money. The chain is built with futures rather than
 * blocking, so a caller can await on an async worker or compose further.
 */
public final class ExcellentEconomyProvider implements EconomyProvider {

    private final ExcellentEconomyBridge bridge;
    private final Map<CurrencyKey, String> currencyIds;
    private final Logger logger;
    private final String displayName;

    public ExcellentEconomyProvider(
        ExcellentEconomyBridge bridge,
        Map<CurrencyKey, String> currencyIds,
        Logger logger
    ) {
        this(bridge, currencyIds, logger, "ExcellentEconomy");
    }

    public ExcellentEconomyProvider(
        ExcellentEconomyBridge bridge,
        Map<CurrencyKey, String> currencyIds,
        Logger logger,
        String displayName
    ) {
        this.bridge = bridge;
        this.currencyIds = currencyIds;
        this.logger = logger;
        this.displayName = displayName;
    }

    @Override
    public String id() {
        return "excellenteconomy";
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<String> currencies() {
        return bridge.currencyIds();
    }

    @Override
    public CompletableFuture<Double> balance(UUID playerId, CurrencyKey currency) {
        return bridge.getBalanceAsync(playerId, providerId(currency))
            .exceptionally(error -> {
                logger.warning("Balance lookup failed for " + playerId + ": " + error.getMessage());
                return 0.0;
            });
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        return bridge.depositAsync(playerId, providerId(currency), amount, reason)
            .thenCompose(result -> {
                if (!bridge.isSuccess(result)) {
                    return CompletableFuture.completedFuture(
                        EconomyResult.failure(EconomyResult.Reason.ERROR, "deposit returned " + result));
                }
                return balance(playerId, currency).thenApply(EconomyResult::success);
            });
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        return bridge.withdrawAsync(playerId, providerId(currency), amount, reason)
            .handle((result, error) -> error == null && bridge.isSuccess(result))
            .thenCompose(succeeded -> {
                if (succeeded) {
                    return balance(playerId, currency).thenApply(EconomyResult::success);
                }
                // The provider does not tell us why it refused, so an overdraw is
                // reported as insufficient funds; anything else is a generic error.
                return balance(playerId, currency).thenApply(current -> {
                    if (current < Math.abs(amount)) {
                        return EconomyResult.failure(EconomyResult.Reason.INSUFFICIENT_FUNDS);
                    }
                    return EconomyResult.failure(EconomyResult.Reason.ERROR, "withdraw was refused");
                });
            });
    }

    @Override
    public CompletableFuture<EconomyResult> transfer(
        UUID from,
        UUID to,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        return withdraw(from, currency, amount, reason + ":transfer-out").thenCompose(withdrawn -> {
            if (withdrawn instanceof EconomyResult.Failure failure) {
                return CompletableFuture.completedFuture((EconomyResult) failure);
            }
            return deposit(to, currency, amount, reason + ":transfer-in").thenCompose(deposited -> {
                if (deposited instanceof EconomyResult.Failure failure) {
                    // Roll back, otherwise the amount disappears from the economy.
                    withdraw(from, currency, amount, reason + ":transfer-refund");
                    return CompletableFuture.completedFuture(EconomyResult.failure(
                        failure.reason(), "refunded sender: " + failure.detail()));
                }
                return CompletableFuture.completedFuture(deposited);
            });
        });
    }

    @Override
    public String format(CurrencyKey currency, double amount) {
        return bridge.format(providerId(currency), amount);
    }

    private String providerId(CurrencyKey currency) {
        String mapped = currencyIds.get(currency);
        return mapped == null ? currency.id() : mapped;
    }

    private static boolean isValidAmount(double amount) {
        return !Double.isNaN(amount) && !Double.isInfinite(amount) && amount > 0.0;
    }

    private static CompletableFuture<EconomyResult> invalidAmount() {
        return CompletableFuture.completedFuture(EconomyResult.failure(EconomyResult.Reason.INVALID_AMOUNT));
    }
}
