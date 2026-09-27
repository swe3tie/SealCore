package su.nightexpress.excellenteconomy.command.currency;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;

public interface CurrencyCommand {

    boolean isFallback();

    void build(@NonNull LiteralNodeBuilder builder, @NonNull ExcellentCurrency currency);

    boolean execute(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                    @NonNull ExcellentCurrency currency);
}
