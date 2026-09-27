package su.nightexpress.excellenteconomy.command;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.ArgumentNodeBuilder;
import su.nightexpress.nightcore.commands.exceptions.CommandSyntaxException;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.util.Lists;

import java.util.function.Predicate;

public class CommandArguments {

    public static final String PLAYER   = "player";
    public static final String AMOUNT   = "amount";
    public static final String CURRENCY = "currency";
    public static final String NAME     = "name";
    public static final String SYMBOL   = "symbol";
    public static final String DECIMALS = "decimals";

    public static final String FLAG_SILENT      = "s";
    public static final String FLAG_NO_FEEDBACK = "sf";

    @NonNull
    public static ArgumentNodeBuilder<ExcellentCurrency> currency(@NonNull CurrencyRegistry registry) {
        return currency(registry, currency -> true);
    }

    @NonNull
    public static ArgumentNodeBuilder<ExcellentCurrency> currency(@NonNull CurrencyRegistry registry,
                                                                  @NonNull Predicate<ExcellentCurrency> predicate) {
        return Commands.argument(CURRENCY, (context, string) -> registry.byId(string).filter(predicate).orElseThrow(
            () -> CommandSyntaxException.custom(Lang.COMMAND_SYNTAX_INVALID_CURRENCY)))
            .localized(Lang.COMMAND_ARGUMENT_NAME_CURRENCY)
            .suggestions((reader, context) -> registry.stream().filter(predicate).map(ExcellentCurrency::getId)
                .toList());
    }

    @NonNull
    public static ArgumentNodeBuilder<Double> amount() {
        return Arguments.decimalCompact(AMOUNT)
            .localized(CoreLang.COMMAND_ARGUMENT_NAME_AMOUNT)
            .suggestions((reader, context) -> Lists.newList("1", "10", "100", "500"));
    }

    @NonNull
    public static ArgumentNodeBuilder<Double> positiveAmount(@NonNull ExcellentCurrency currency) {
        return Arguments.decimalCompact(AMOUNT, currency.isDecimal() ? 0.1 : 1)
            .localized(CoreLang.COMMAND_ARGUMENT_NAME_AMOUNT)
            .suggestions((reader, context) -> Lists.newList("1", "10", "100", "500"));
    }
}
