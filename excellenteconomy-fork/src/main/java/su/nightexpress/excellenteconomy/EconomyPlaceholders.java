package su.nightexpress.excellenteconomy;

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.nightcore.util.placeholder.TypedPlaceholder;

public class EconomyPlaceholders {

    public static final String WIKI_URL          = "https://nightexpressdev.com/excellenteconomy/";
    public static final String WIKI_PLACEHOLDERS = WIKI_URL + "utility/internal-placeholders";

    public static final String GENERIC_NAME          = "%name%";
    public static final String GENERIC_BALANCE       = "%balance%";
    public static final String GENERIC_AMOUNT        = "%amount%";
    public static final String GENERIC_CURRENT       = "%current%";
    public static final String GENERIC_MAX           = "%max%";
    public static final String GENERIC_POS           = "%pos%";
    public static final String GENERIC_STATE         = "%state%";
    public static final String GENERIC_ENTRY         = "%entry%";
    public static final String GENERIC_NEXT_PAGE     = "%next_page%";
    public static final String GENERIC_PREVIOUS_PAGE = "%previous_page%";

    public static final String CURRENCY_ID     = "%currency_id%";
    public static final String CURRENCY_NAME   = "%currency_name%";
    public static final String CURRENCY_SYMBOL = "%currency_symbol%";
    public static final String CURRENCY_PREFIX = "%currency_prefix%";
    public static final String CURRENCY_LABEL  = "%currency_label%";

    public static final TypedPlaceholder<ExcellentCurrency> CURRENCY = TypedPlaceholder.builder(ExcellentCurrency.class)
        .with(CURRENCY_ID, ExcellentCurrency::getId)
        .with(CURRENCY_NAME, ExcellentCurrency::getName)
        .with(CURRENCY_PREFIX, ExcellentCurrency::getPrefix)
        .with(CURRENCY_SYMBOL, ExcellentCurrency::getSymbol)
        .with(CURRENCY_LABEL, currency -> currency.getCommandAliases()[0])
        .build();
}
