package su.nightexpress.excellenteconomy.data;


import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.user.data.CurrencySettings;
import su.nightexpress.nightcore.db.column.Column;
import su.nightexpress.nightcore.db.column.ColumnDataReader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataColumns {

    private DataColumns() {
    }

    public static final Column<Integer> ID = Column.intType("id").primaryKey().autoIncrement().build();

    public static final Column<UUID>   USER_UUID      = Column.uuidType("uuid").build();
    public static final Column<String> USER_NAME      = Column.stringType("name", 32).build();
    public static final Column<Long>   USER_LAST_SEEN = Column.longType("last_seen")
        .defaultValue(String.valueOf(System.currentTimeMillis()))
        .build();

    public static final Column<Map<String, CurrencySettings>> USER_SETTINGS = Column
        .json("settings", ColumnDataReader.jsonMap(DataHandler.GSON, String.class, CurrencySettings.class))
        .build();

    public static final Column<Boolean> USER_HIDE_FROM_TOPS = Column.booleanType("hiddenFromTops")
        .defaultValue(false)
        .build();

    private static final Map<String, Column<Double>> CURRENCY_MAP = new HashMap<>();

    @Contract(pure = true)
    @NonNull
    @Unmodifiable
    public static Map<String, Column<Double>> currencies() {
        return Map.copyOf(CURRENCY_MAP);
    }

    @NonNull
    public static Column<Double> forCurrency(@NonNull ExcellentCurrency currency) {
        return CURRENCY_MAP.computeIfAbsent(currency.getId(), k -> Column.doubleType(currency.getColumnName())
            .defaultValue(currency.getStartValue()).build());
    }

    public static void uncacheCurrency(@NonNull ExcellentCurrency currency) {
        CURRENCY_MAP.remove(currency.getId());
    }

    public static boolean containsCurrency(@NonNull ExcellentCurrency currency) {
        return CURRENCY_MAP.containsKey(currency.getId());
    }

    public static void clearCache() {
        CURRENCY_MAP.clear();
    }
}
