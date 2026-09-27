package su.nightexpress.excellenteconomy.currency.impl;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlaceholders;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.config.Config;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.integration.placeholder.PAPI;
import su.nightexpress.nightcore.locale.entry.MessageLocale;
import su.nightexpress.nightcore.locale.message.LangMessage;
import su.nightexpress.nightcore.manager.ConfigBacked;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.bukkit.NightItem;
import su.nightexpress.nightcore.util.number.CompactNumber;
import su.nightexpress.nightcore.util.placeholder.PlaceholderContext;
import su.nightexpress.nightcore.util.placeholder.PlaceholderResolver;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

public abstract class AbstractCurrency implements ExcellentCurrency, ConfigBacked {

    private static final DecimalFormat RAW_FORMAT = new DecimalFormat("#");

    static {
        RAW_FORMAT.setMaximumFractionDigits(8);
    }

    protected final Path                path;
    protected final String              id;
    protected final Map<String, Double> exchangeRates;

    protected String    name;
    protected String    symbol;
    protected String    prefix;
    protected String[]  commandAliases;
    protected String    format;
    protected String    formatShort;
    protected NightItem icon;

    protected String  dataColumn;
    protected boolean dataSync;

    protected boolean permissionRequired;
    protected boolean decimal;
    protected double  startValue;
    protected double  maxValue;

    protected boolean transferAllowed;
    protected double  minTransferAmount;

    protected boolean exchangeAllowed;
    protected boolean leaderboardEnabled;

    protected String messagePrefix;

    protected AbstractCurrency(@NonNull Path path, @NonNull String id) {
        this.path = path;
        this.id = id;
        this.exchangeRates = new HashMap<>();

        this.setName(StringUtil.capitalizeUnderscored(id));
        this.setSymbol("");
        this.setPrefix(this.name);
        this.setCommandAliases(new String[]{id});
        this.setFormat(EconomyPlaceholders.GENERIC_AMOUNT + EconomyPlaceholders.CURRENCY_SYMBOL);
        this.setFormatShort(EconomyPlaceholders.GENERIC_AMOUNT + EconomyPlaceholders.CURRENCY_SYMBOL);
        this.setIcon(NightItem.fromType(Material.EMERALD));
        this.dataColumn = id;
        this.dataSync = false;
        this.setPermissionRequired(false);
        this.setDecimal(false);
        this.setStartValue(0);
        this.setMaxValue(-1);
        this.setTransferAllowed(true);
        this.setMinTransferAmount(-1D);
        this.setExchangeAllowed(true);
        this.leaderboardEnabled = true;

        this.updateMessagePrefix();
    }

    public void load() throws IllegalStateException {
        this.loadConfig().edit(this::load);
    }

    public void write() {
        this.loadConfig().edit(this::write);
    }

    private void load(@NonNull FileConfig config) throws IllegalStateException {
        this.setName(ConfigValue.create("Name", StringUtil.capitalizeUnderscored(this.id),
            "Currency display name.",
            Placeholders.URL_WIKI_TEXT
        ).read(config));

        this.setSymbol(ConfigValue.create("Symbol", this.name,
            "Currency symbol.",
            Placeholders.URL_WIKI_TEXT
        ).read(config));

        this.setPrefix(ConfigValue.create("Prefix", this.name,
            "Currency prefix.",
            Placeholders.URL_WIKI_TEXT
        ).read(config));

        this.setCommandAliases(ConfigValue.create("Command_Aliases", new String[]{this.id},
            "Currency command aliases. Split with comma.",
            "[*] Server reboot is required for the changes to apply."
        ).read(config));

        this.setIcon(ConfigValue.create("Icon",
            NightItem.fromType(Material.GOLD_NUGGET),
            "Currency icon.",
            Placeholders.URL_WIKI_ITEMS
        ).read(config));

        this.setFormat(ConfigValue.create("Format",
            EconomyPlaceholders.GENERIC_AMOUNT + EconomyPlaceholders.CURRENCY_SYMBOL,
            "Currency display format.",
            "Placeholders:",
            "- " + EconomyPlaceholders.GENERIC_AMOUNT + " - Amount value.",
            "- Currency placeholders: " + EconomyPlaceholders.WIKI_PLACEHOLDERS,
            "- " + PAPI.NAME +
                " placeholders that are NOT bound to a player (e.g. Oraxen or ItemsAdder %img% placeholders)",
            Placeholders.URL_WIKI_TEXT
        ).read(config));

        this.setFormatShort(ConfigValue.create("Format_Short",
            EconomyPlaceholders.CURRENCY_SYMBOL + EconomyPlaceholders.GENERIC_AMOUNT,
            "Currency short display format.",
            "- " + EconomyPlaceholders.GENERIC_AMOUNT + " - Amount value.",
            "- Currency placeholders: " + EconomyPlaceholders.WIKI_PLACEHOLDERS,
            "- " + PAPI.NAME +
                " placeholders that are NOT bound to a player (e.g. Oraxen or ItemsAdder %img% placeholders)",
            Placeholders.URL_WIKI_TEXT
        ).read(config));

        this.setColumnName(ConfigValue.create("Column_Name", this.id,
            "Database column name where this currency will be saved."
        ).read(config));

        this.setSynchronizable(ConfigValue.create("Synchronized", true,
            "Controls whether currency is included in data synchronization."
        ).read(config));

        this.setDecimal(ConfigValue.create("Decimal", false,
            "Controls whether decimal values are allowed for this currency."
        ).read(config));

        this.setPermissionRequired(ConfigValue.create("Permission_Required",
            false,
            "Controls whether permission is required for this currency."
        ).read(config));

        this.setTransferAllowed(ConfigValue.create("Transfer_Allowed",
            true,
            "Controls whether players can send this currency to other players."
        ).read(config));

        this.setMinTransferAmount(ConfigValue.create("Transfer_Min_Amount",
            1D,
            "Min. amount to send this currency to other players.",
            "[*] Set to '-1' for no limit."
        ).read(config));

        this.setStartValue(ConfigValue.create("Start_Value",
            0D,
            "Start currency value for new players."
        ).read(config));

        this.setMaxValue(ConfigValue.create("Max_Value",
            -1D,
            "Max. possible value that players can have on their balance.",
            "[*] Set to '-1' to disable."
        ).read(config));

        this.setExchangeAllowed(ConfigValue.create("Exchange.Allowed",
            true,
            "Controls whether this currency can be exchanged for other ones."
        ).read(config));

        if (config.getSection("Exchange.Rates").isEmpty()) {
            config.set("Exchange.Rates.mystery_coins", 5);
            config.set("Exchange.Rates.magic_coins", 10);
        }

        config.getSection("Exchange.Rates").forEach(sId -> {
            double rate = config.getDouble("Exchange.Rates." + sId);
            this.exchangeRates.put(LowerCase.INTERNAL.apply(sId), rate);
        });

        this.leaderboardEnabled = ConfigValue.create("Leaderboard.Enabled",
            true,
            "Controls whether this currency can have a leaderboard.",
            "[*] Requires the Tops module to be enabled."
        ).read(config);

        this.updateMessagePrefix();
    }

    private void write(@NonNull FileConfig config) {
        config.set("Name", this.name);
        config.set("Symbol", this.symbol);
        config.set("Prefix", this.prefix);
        config.set("Command_Aliases", String.join(",", Arrays.asList(this.commandAliases)));
        config.set("Format", this.format);
        config.set("Format_Short", this.formatShort);
        config.set("Icon", this.icon);

        config.set("Column_Name", this.dataColumn);
        config.set("Synchronized", this.dataSync);

        config.set("Permission_Required", this.permissionRequired);
        config.set("Decimal", this.decimal);
        config.set("Start_Value", this.startValue);
        config.set("Max_Value", this.maxValue);

        config.set("Transfer_Allowed", this.transferAllowed);
        config.set("Transfer_Min_Amount", this.minTransferAmount);

        config.set("Exchange.Allowed", this.exchangeAllowed);
        config.remove("Exchange.Rates");
        this.exchangeRates.forEach((otherId, rate) -> {
            config.set("Exchange.Rates." + otherId, rate);
        });

        config.set("Leaderboard.Enabled", this.leaderboardEnabled);
    }

    @Override
    @NonNull
    public PlaceholderResolver placeholders() {
        return EconomyPlaceholders.CURRENCY.resolver(this);
    }

    @Override
    public void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender) {
        this.getPrefixed(locale).sendWith(sender, builder -> builder.with(this.placeholders()));
    }

    @Override
    public void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender,
                             @NonNull Consumer<PlaceholderContext.Builder> consumer) {
        this.getPrefixed(locale).sendWith(sender, builder -> consumer.accept(builder.with(this.placeholders())));
    }

    @Override
    public void sendPrefixed(@NonNull MessageLocale locale, @NonNull CommandSender sender,
                             @NonNull PlaceholderContext context) {
        this.getPrefixed(locale).sendWith(sender, context);
    }

    private LangMessage getPrefixed(@NonNull MessageLocale locale) {
        return Config.CURRENCY_PREFIX_ENABLED.get() ? locale.withPrefix(this.messagePrefix) : locale.message();
    }

    public void updateMessagePrefix() {
        this.messagePrefix = PlaceholderContext.builder().with(this.placeholders()).build().apply(
            Config.CURRENCY_PREFIX_FORMAT.get());
    }

    @Override
    public boolean hasPermission(@NonNull Player player) {
        return !this.permissionRequired || (player.hasPermission(this.getPermission()) || player.hasPermission(
            Perms.CURRENCY));
    }

    @Override
    public boolean isUnlimited() {
        return this.maxValue <= 0D;
    }

    @Override
    public boolean isLimited() {
        return !this.isUnlimited();
    }

    @Override
    public boolean isInteger() {
        return !this.decimal;
    }

    @Override
    public boolean isUnderLimit(double value) {
        return this.isUnlimited() || value <= this.maxValue;
    }

    @Override
    public double floorIfNeeded(double amount) {
        return Math.max(0, this.decimal ? amount : Math.floor(amount));
    }

    @Override
    public double limitIfNeeded(double amount) {
        return this.isLimited() ? Math.min(amount, this.maxValue) : amount;
    }

    @Override
    public double floorAndLimit(double amount) {
        return this.floorIfNeeded(this.limitIfNeeded(amount));
    }

    @Override
    @NonNull
    public String getPermission() {
        return Perms.PREFIX_CURRENCY + this.getId();
    }

    @Override
    @NonNull
    public CompactNumber compacted(double balance) {
        return NumberUtil.asCompact(this.floorIfNeeded(balance));
    }

    @Override
    @NonNull
    public String formatValue(double balance) {
        return NumberUtil.format(this.floorIfNeeded(balance));
    }

    @Override
    @NonNull
    public String format(double balance) {
        return this.getFormatted(this.format, balance, this::formatValue);
    }

    @Override
    @NonNull
    public String formatCompact(double balance) {
        return this.getFormatted(this.formatShort, balance, value -> this.compacted(value).format());
    }

    @Override
    @NonNull
    public String formatRaw(double balance) {
        return RAW_FORMAT.format(this.floorIfNeeded(balance));
    }

    @NonNull
    private String getFormatted(@NonNull String originalFormat, double balance,
                                @NonNull DoubleFunction<String> valueFormatter) {
        return PlaceholderContext.builder()
            .with(this.placeholders())
            .with(EconomyPlaceholders.GENERIC_AMOUNT, () -> valueFormatter.apply(balance))
            .andThen(string -> PAPI.setPlaceholders(null, string))
            .build()
            .apply(originalFormat);
    }

    @Override
    @NonNull
    public Path getPath() {
        return this.path;
    }

    @Override
    @NonNull
    public String getId() {
        return this.id;
    }

    @NonNull
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String getPrefix() {
        return this.prefix;
    }

    @Override
    public void setPrefix(@NonNull String prefix) {
        this.prefix = prefix;
    }

    @NonNull
    @Override
    public String getSymbol() {
        return this.symbol;
    }

    @Override
    public void setSymbol(@NonNull String symbol) {
        this.symbol = symbol;
    }

    @NonNull
    @Override
    public String getFormat() {
        return this.format;
    }

    @Override
    public void setFormat(@NonNull String format) {
        this.format = format;
    }

    @NonNull
    @Override
    public String getFormatShort() {
        return this.formatShort;
    }

    @Override
    public void setFormatShort(@NonNull String formatShort) {
        this.formatShort = formatShort.replace("%currency_short_symbol%", "");
    }

    @NonNull
    @Override
    public String getColumnName() {
        return this.dataColumn;
    }

    public void setColumnName(@NonNull String dataColumn) {
        this.dataColumn = dataColumn;
    }

    @NonNull
    @Override
    public String[] getCommandAliases() {
        return this.commandAliases;
    }

    @Override
    public void setCommandAliases(String[] commandAliases) {
        this.commandAliases = commandAliases;
    }

    @Override
    @NonNull
    public NightItem icon() {
        return this.icon.copy();
    }

    @Override
    public void setIcon(@NonNull NightItem icon) {
        this.icon = icon;
    }

    @Override
    public boolean isDecimal() {
        return this.decimal;
    }

    @Override
    public void setDecimal(boolean decimal) {
        this.decimal = decimal;
    }

    @Override
    public boolean isSynchronizable() {
        return this.dataSync;
    }

    @Override
    public void setSynchronizable(boolean dataSync) {
        this.dataSync = dataSync;
    }

    @Override
    public boolean isPermissionRequired() {
        return this.permissionRequired;
    }

    @Override
    public void setPermissionRequired(boolean permissionRequired) {
        this.permissionRequired = permissionRequired;
    }

    @Override
    public boolean isTransferAllowed() {
        return this.transferAllowed;
    }

    @Override
    public void setTransferAllowed(boolean transferAllowed) {
        this.transferAllowed = transferAllowed;
    }

    public double getMinTransferAmount() {
        return this.minTransferAmount;
    }

    @Override
    public void setMinTransferAmount(double minTransferAmount) {
        this.minTransferAmount = minTransferAmount;
    }

    @Override
    public double getStartValue() {
        return this.startValue;
    }

    @Override
    public void setStartValue(double startValue) {
        this.startValue = startValue;
    }

    @Override
    public double getMaxValue() {
        return this.maxValue;
    }

    @Override
    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    @Override
    public boolean isExchangeAllowed() {
        return this.exchangeAllowed;
    }

    @Override
    public void setExchangeAllowed(boolean exchangeAllowed) {
        this.exchangeAllowed = exchangeAllowed;
    }

    @Override
    @NonNull
    public Map<String, Double> getExchangeRates() {
        return this.exchangeRates;
    }

    @Override
    public double getExchangeRate(@NonNull ExcellentCurrency currency) {
        return this.getExchangeRate(currency.getId());
    }

    @Override
    public double getExchangeRate(@NonNull String id) {
        return this.exchangeRates.getOrDefault(LowerCase.INTERNAL.apply(id), 0D);
    }

    @Override
    public double getExchangeResult(@NonNull ExcellentCurrency other, double amount) {
        double rate = this.getExchangeRate(other);
        return other.floorIfNeeded(amount * rate);
    }

    @Override
    public boolean canExchangeTo(@NonNull ExcellentCurrency other) {
        return this.exchangeRates.containsKey(other.getId());
    }

    @Override
    public boolean isLeaderboardEnabled() {
        return this.leaderboardEnabled;
    }
}
