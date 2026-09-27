package su.nightexpress.excellenteconomy.user;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

public class UserBalance {

    private final Map<String, Double> balanceMap;

    public UserBalance() {
        this(new ConcurrentHashMap<>());
    }

    public UserBalance(@NonNull Map<String, Double> balanceMap) {
        this.balanceMap = new ConcurrentHashMap<>(balanceMap);
    }

    /**
     * Returns a map of all currency balances.
     * 
     * @return An unmodifiable copy of the balance map.
     */
    @NonNull
    public Map<String, Double> getBalanceMap() {
        return Map.copyOf(this.balanceMap);
    }

    public void clear() {
        this.balanceMap.clear();
    }

    public void clear(@NonNull ExcellentCurrency currency) {
        this.clear(currency.getId());
    }

    public void clear(@NonNull String currencyId) {
        this.balanceMap.remove(currencyId);
    }

    public boolean has(@NonNull ExcellentCurrency currency, double amount) {
        return this.get(currency) >= amount;
    }

    public double get(@NonNull ExcellentCurrency currency) {
        return this.get(currency.getId());
    }

    public double get(@NonNull String currencyId) {
        return Math.max(0, this.balanceMap.getOrDefault(currencyId, 0D));
    }

    public void add(@NonNull ExcellentCurrency currency, double amount) {
        this.add(currency.getId(), amount);
    }

    public void add(@NonNull String currencyId, double amount) {
        this.balanceMap.compute(currencyId, (k, v) -> Math.max(0, (v == null ? 0D : v) + Math.abs(amount)));
    }

    public void remove(@NonNull ExcellentCurrency currency, double amount) {
        this.remove(currency.getId(), amount);
    }

    public void remove(@NonNull String currencyId, double amount) {
        this.balanceMap.compute(currencyId, (k, v) -> Math.max(0, (v == null ? 0D : v) - Math.abs(amount)));
    }

    public void set(@NonNull ExcellentCurrency currency, double amount) {
        this.set(currency.getId(), currency.floorAndLimit(amount));
    }

    public void set(@NonNull String currencyId, double amount) {
        this.balanceMap.put(currencyId, Math.max(0, amount));
    }
}
