package su.nightexpress.excellenteconomy.api;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
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
 * <p>Amounts are boxed {@code Double} on purpose: the bridge looks the methods up by
 * {@code Double.class}, so a primitive {@code double} here would make the reflective
 * contract fail exactly the way a renamed upstream method would.
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

    CompletableFuture<OperationResult> depositAsync(UUID playerId, String currencyName, Double amount);

    CompletableFuture<OperationResult> depositAsync(UUID playerId, String currencyName, Double amount, Object context);

    CompletableFuture<OperationResult> withdrawAsync(UUID playerId, String currencyId, Double amount);

    CompletableFuture<OperationResult> withdrawAsync(UUID playerId, String currencyId, Double amount, Object context);

    /** In-memory backing store so the tests can assert real balances. */
    final class Fake implements ExcellentEconomyAPI {

        public final Map<BalanceKey, Double> balances = new java.util.HashMap<>();
        public final java.util.ArrayList<String> calls = new java.util.ArrayList<>();
        public boolean failEverything;

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
        public CompletableFuture<OperationResult> depositAsync(UUID playerId, String currencyName, Double amount) {
            return apply(playerId, currencyName, amount, "deposit", null);
        }

        @Override
        public CompletableFuture<OperationResult> depositAsync(
            UUID playerId,
            String currencyName,
            Double amount,
            Object context
        ) {
            return apply(playerId, currencyName, amount, "deposit", context);
        }

        @Override
        public CompletableFuture<OperationResult> withdrawAsync(UUID playerId, String currencyId, Double amount) {
            return apply(playerId, currencyId, amount, "withdraw", null);
        }

        @Override
        public CompletableFuture<OperationResult> withdrawAsync(
            UUID playerId,
            String currencyId,
            Double amount,
            Object context
        ) {
            return apply(playerId, currencyId, amount, "withdraw", context);
        }

        private CompletableFuture<OperationResult> apply(
            UUID playerId,
            String currencyId,
            Double amount,
            String operation,
            Object context
        ) {
            calls.add(operation + " " + currencyId + " " + amount + " context=" + (context != null));
            if (failEverything) {
                return CompletableFuture.completedFuture(OperationResult.FAILURE);
            }
            BalanceKey key = new BalanceKey(playerId, currencyId);
            double current = balances.getOrDefault(key, 0.0);
            double next = operation.equals("withdraw") ? current - amount : current + amount;
            if (next < 0) {
                return CompletableFuture.completedFuture(OperationResult.FAILURE);
            }
            balances.put(key, next);
            return CompletableFuture.completedFuture(OperationResult.SUCCESS);
        }
    }

    /** Balances are keyed by player and currency, so the pair needs a real key. */
    record BalanceKey(UUID playerId, String currencyId) {
    }
}
