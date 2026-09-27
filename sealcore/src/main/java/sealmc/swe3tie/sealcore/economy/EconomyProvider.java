package sealmc.swe3tie.sealcore.economy;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything SealCore needs from a currency backend.
 *
 * <p>Implementations return futures and never block, so a caller decides
 * whether to await on an async worker or chain. A provider that is absent still
 * has to be registered, so callers never deal with {@code null}; it reports
 * {@link EconomyResult.Reason#PROVIDER_ABSENT} instead.
 */
public interface EconomyProvider {

    String id();

    String displayName();

    boolean isAvailable();

    /** Currency ids the provider knows about. */
    Set<String> currencies();

    CompletableFuture<Double> balance(UUID playerId, CurrencyKey currency);

    CompletableFuture<EconomyResult> deposit(UUID playerId, CurrencyKey currency, double amount, String reason);

    CompletableFuture<EconomyResult> withdraw(UUID playerId, CurrencyKey currency, double amount, String reason);

    CompletableFuture<EconomyResult> transfer(
        UUID from,
        UUID to,
        CurrencyKey currency,
        double amount,
        String reason
    );

    String format(CurrencyKey currency, double amount);
}
