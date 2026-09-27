package sealmc.swe3tie.sealcore.economy.provider;

import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyProvider;
import sealmc.swe3tie.sealcore.economy.EconomyResult;

/**
 * Stand-in used when no economy plugin is installed.
 *
 * <p>Registered unconditionally so feature modules never branch on a null
 * provider; every call comes back as
 * {@link EconomyResult.Reason#PROVIDER_ABSENT}.
 */
public final class NoopEconomyProvider implements EconomyProvider {

    private static final String ABSENT_DETAIL = "No economy provider loaded";

    private final String id;
    private final String displayName;
    private final DecimalFormat formatter = new DecimalFormat("#,##0.##");

    public NoopEconomyProvider() {
        this("none", "No economy provider");
    }

    public NoopEconomyProvider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Set<String> currencies() {
        return Set.of();
    }

    @Override
    public CompletableFuture<Double> balance(UUID playerId, CurrencyKey currency) {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID playerId, CurrencyKey currency, double amount, String reason) {
        return absent();
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(UUID playerId, CurrencyKey currency, double amount, String reason) {
        return absent();
    }

    @Override
    public CompletableFuture<EconomyResult> transfer(
        UUID from,
        UUID to,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        return absent();
    }

    @Override
    public String format(CurrencyKey currency, double amount) {
        return formatter.format(amount);
    }

    private CompletableFuture<EconomyResult> absent() {
        return CompletableFuture.completedFuture(
            EconomyResult.failure(EconomyResult.Reason.PROVIDER_ABSENT, ABSENT_DETAIL));
    }
}
