package sealmc.swe3tie.sealcore.economy;

import java.util.List;
import java.util.Locale;

/**
 * A currency as SealCore sees it, independent of the backing provider.
 *
 * <p>{@code config.yml} maps each key onto a provider specific currency id, so
 * feature modules only ever pass {@link #MONEY}, {@link #SHARDS} or
 * {@link #COINS} around.
 */
public record CurrencyKey(String id) {

    public static final CurrencyKey MONEY = new CurrencyKey("money");
    public static final CurrencyKey SHARDS = new CurrencyKey("shards");
    public static final CurrencyKey COINS = new CurrencyKey("coins");

    public static final List<CurrencyKey> BUILT_IN = List.of(MONEY, SHARDS, COINS);

    public String lowerId() {
        return id.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return id;
    }

    public static CurrencyKey of(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "money" -> MONEY;
            case "shards" -> SHARDS;
            case "coins" -> COINS;
            default -> new CurrencyKey(id);
        };
    }
}
