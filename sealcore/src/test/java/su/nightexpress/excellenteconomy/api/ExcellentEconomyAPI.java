package su.nightexpress.excellenteconomy.api;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult;

/**
 * Test stand-in for ExcellentEconomy's API, declared at the upstream package and
 * type name.
 *
 * <p>ExcellentEconomy registers this interface as a Bukkit service and its methods are
 * instance methods, so this fake does the same. The bridge under test resolves the
 * class by name and invokes the methods reflectively, which pins the exact names and
 * parameter types the plugin depends on without vendoring the third party jar.
 *
 * <p>Amounts are a primitive {@code double}, because that is what upstream declares.
 * The bridge looks the methods up by {@code double.class}, so boxing them here would
 * make the test pass against a contract the real API does not have. Verified against
 * ExcellentEconomy 2.8.0 and re-checked by the fork module's ApiSurfaceTest.
 */
public interface ExcellentEconomyAPI {

    Set<ExcellentCurrency> CURRENCIES = new LinkedHashSet<>(java.util.List.of(
        new ExcellentCurrency.Simple("money"),
        new ExcellentCurrency.Simple("shards"),
        new ExcellentCurrency.Simple("coins")));

    boolean hasCurrency(String id);

    Set<ExcellentCurrency> getCurrencies();

    ExcellentCurrency getCurrency(String id);

    CompletableFuture<Double> getBalanceAsync(UUID playerId, String currencyName);

    CompletableFuture<OperationResult> depositAsync(UUID playerId, String currencyName, double amount);

    CompletableFuture<OperationResult> depositAsync(
        UUID playerId,
        String currencyName,
        double amount,
        OperationContext context
    );

    CompletableFuture<OperationResult> withdrawAsync(UUID playerId, String currencyId, double amount);

    CompletableFuture<OperationResult> withdrawAsync(
        UUID playerId,
        String currencyId,
        double amount,
        OperationContext context
    );

    /** In-memory backing store so the tests can assert real balances. */
    final class Fake implements ExcellentEconomyAPI {

        public final Map<BalanceKey, Double> balances = new java.util.HashMap<>();
        public final java.util.ArrayList<String> calls = new java.util.ArrayList<>();
        public boolean failEverything;
        /** Deposits to this player fail, so a transfer's receiving half breaks. */
        public UUID failDepositsFor;
        /** The context the bridge passed with the last call, to inspect its notifications. */
        public OperationContext lastContext;

        @Override
        public boolean hasCurrency(String id) {
            return CURRENCIES.stream().anyMatch(currency -> currency.getId().equals(id));
        }

        @Override
        public Set<ExcellentCurrency> getCurrencies() {
            return CURRENCIES;
        }

        @Override
        public ExcellentCurrency getCurrency(String id) {
            return CURRENCIES.stream().filter(currency -> currency.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public CompletableFuture<Double> getBalanceAsync(UUID playerId, String currencyName) {
            calls.add("getBalanceAsync " + currencyName);
            return CompletableFuture.completedFuture(balances.getOrDefault(new BalanceKey(playerId, currencyName), 0.0));
        }

        @Override
        public CompletableFuture<OperationResult> depositAsync(UUID playerId, String currencyName, double amount) {
            return apply(playerId, currencyName, amount, "deposit", null);
        }

        @Override
        public CompletableFuture<OperationResult> depositAsync(
            UUID playerId,
            String currencyName,
            double amount,
            OperationContext context
        ) {
            return apply(playerId, currencyName, amount, "deposit", context);
        }

        @Override
        public CompletableFuture<OperationResult> withdrawAsync(UUID playerId, String currencyId, double amount) {
            return apply(playerId, currencyId, amount, "withdraw", null);
        }

        @Override
        public CompletableFuture<OperationResult> withdrawAsync(
            UUID playerId,
            String currencyId,
            double amount,
            OperationContext context
        ) {
            return apply(playerId, currencyId, amount, "withdraw", context);
        }

        /**
         * Mirrors ExcellentEconomy's CurrencyManager.remove, which subtracts the amount
         * and reports success without ever checking whether the player can afford it.
         *
         * <p>This stand-in used to refuse an overdraw, which is not what the real API
         * does, and every balance check in SealCore therefore looked like it worked.
         * A stand-in that is more forgiving than the real thing is worse than none at
         * all, so the real behaviour is reproduced here and the checks are tested
         * against it.
         */
        private CompletableFuture<OperationResult> apply(
            UUID playerId,
            String currencyId,
            double amount,
            String operation,
            OperationContext context
        ) {
            calls.add(operation + " " + currencyId + " " + amount + " context=" + (context != null));
            if (context != null) {
                lastContext = context;
            }
            if (failEverything || (operation.equals("deposit") && playerId.equals(failDepositsFor))) {
                return CompletableFuture.completedFuture(OperationResult.FAILURE);
            }
            BalanceKey key = new BalanceKey(playerId, currencyId);
            double current = balances.getOrDefault(key, 0.0);
            balances.put(key, operation.equals("withdraw") ? current - amount : current + amount);
            return CompletableFuture.completedFuture(OperationResult.SUCCESS);
        }
    }

    /** Balances are keyed by player and currency, so the pair needs a real key. */
    record BalanceKey(UUID playerId, String currencyId) {
    }
}
