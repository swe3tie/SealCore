package su.nightexpress.excellenteconomy.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.nightcore.util.LowerCase;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CurrencyRegistry {

    private final Map<String, ExcellentCurrency> currencyByIdMap;

    public CurrencyRegistry() {
        this.currencyByIdMap = new HashMap<>();
    }

    public void clear() {
        this.currencyByIdMap.clear();
    }

    public void add(@NotNull ExcellentCurrency currency) {
        this.currencyByIdMap.put(currency.getId(), currency);
    }

    @Nullable
    public ExcellentCurrency remove(@NotNull ExcellentCurrency currency) {
        return this.remove(currency.getId());
    }

    @Nullable
    public ExcellentCurrency remove(@NotNull String id) {
        return this.currencyByIdMap.remove(LowerCase.INTERNAL.apply(id));
    }

    public boolean hasPrimary() {
        return this.findPrimary().isPresent();
    }

    public boolean isRegistered(@NotNull String id) {
        return this.currencyByIdMap.containsKey(LowerCase.INTERNAL.apply(id));
    }

    public void forEach(@NonNull Consumer<ExcellentCurrency> consumer) {
        this.stream().forEach(consumer);
    }

    @NonNull
    public Stream<ExcellentCurrency> stream() {
        return this.currencyByIdMap.values().stream();
    }

    @NotNull
    public Optional<ExcellentCurrency> findPrimary() {
        return this.stream().filter(ExcellentCurrency::isPrimary).findFirst();
    }

    @NotNull
    @Deprecated
    public Map<String, ExcellentCurrency> getCurrencyMap() {
        return this.getCurrencyByIdMap();
    }

    @NonNull
    public Map<String, ExcellentCurrency> getCurrencyByIdMap() {
        return Map.copyOf(this.currencyByIdMap);
    }

    @NotNull
    public List<String> getCurrencyIds() {
        return new ArrayList<>(this.currencyByIdMap.keySet());
    }

    @NotNull
    public Set<ExcellentCurrency> getCurrencies() {
        return Set.copyOf(this.currencyByIdMap.values());
    }

    @Nullable
    public ExcellentCurrency getById(@NotNull String id) {
        return this.currencyByIdMap.get(LowerCase.INTERNAL.apply(id));
    }

    @NotNull
    public Optional<ExcellentCurrency> byId(@NotNull String id) {
        return Optional.ofNullable(this.getById(id));
    }
}
