package su.nightexpress.excellenteconomy.api.currency;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import su.nightexpress.nightcore.locale.entry.MessageLocale;
import su.nightexpress.nightcore.util.bukkit.NightItem;
import su.nightexpress.nightcore.util.number.CompactNumber;
import su.nightexpress.nightcore.util.placeholder.PlaceholderContext;
import su.nightexpress.nightcore.util.placeholder.PlaceholderResolvable;

import java.util.Map;
import java.util.function.Consumer;

public interface ExcellentCurrency extends PlaceholderResolvable {

    void onRegister();

    void onUnregister();

    void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender);

    void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender, @NonNull Consumer<PlaceholderContext.Builder> consumer);

    void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender, @NonNull PlaceholderContext context);

    boolean hasPermission(@NonNull Player player);

    boolean isPrimary();

    boolean isUnlimited();

    boolean isLimited();

    boolean isInteger();

    boolean isUnderLimit(double value);

    double floorIfNeeded(double amount);

    double limitIfNeeded(double amount);

    double floorAndLimit(double amount);

    @NonNull String getPermission();

    @NonNull String formatValue(double balance);

    @NonNull String format(double balance);

    @NonNull CompactNumber compacted(double balance);

    @NonNull String formatCompact(double balance);

    @NonNull String formatRaw(double balance);

    @NonNull String getId();

    @NonNull String getName();

    void setName(@NonNull String name);

    @NonNull String getPrefix();

    void setPrefix(@NonNull String prefix);

    @NonNull String getSymbol();

    void setSymbol(@NonNull String symbol);

    @NonNull String getFormat();

    void setFormat(@NonNull String format);

    @NonNull String getFormatShort();

    void setFormatShort(@NonNull String formatShort);

    @NonNull String[] getCommandAliases();

    void setCommandAliases(String... commandAliases);

    @NonNull String getColumnName();

    void setColumnName(@NonNull String dataColumn);

    @NonNull NightItem icon();

    void setIcon(@NonNull NightItem icon);

    boolean isDecimal();

    void setDecimal(boolean decimal);

    boolean isPermissionRequired();

    void setPermissionRequired(boolean permissionRequired);

    boolean isSynchronizable();

    void setSynchronizable(boolean dataSync);

    boolean isTransferAllowed();

    void setTransferAllowed(boolean transferAllowed);

    double getMinTransferAmount();

    void setMinTransferAmount(double minTransferAmount);

    double getStartValue();

    void setStartValue(double startValue);

    double getMaxValue();

    void setMaxValue(double maxValue);

    boolean isExchangeAllowed();

    void setExchangeAllowed(boolean exchangeAllowed);

    @NonNull Map<String, Double> getExchangeRates();

    double getExchangeRate(@NonNull ExcellentCurrency currency);

    double getExchangeRate(@NonNull String id);

    boolean canExchangeTo(@NonNull ExcellentCurrency other);

    double getExchangeResult(@NonNull ExcellentCurrency other, double amount);

    boolean isLeaderboardEnabled();
}

