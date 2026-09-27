package su.nightexpress.excellenteconomy.tops.command;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.command.CommandArguments;
import su.nightexpress.excellenteconomy.command.currency.CurrencyCommand;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;

import java.util.stream.IntStream;

public class TopCommand implements CurrencyCommand {

    private final TopManager manager;

    public TopCommand(@NonNull TopManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean isFallback() {
        return false;
    }

    @Override
    public void build(@NonNull LiteralNodeBuilder builder, @NonNull ExcellentCurrency currency) {
        builder
            .permission(Perms.COMMAND_CURRENCY_TOP)
            .description(Lang.COMMAND_CURRENCY_TOP_DESC)
            .withArguments(Arguments.integer(CommandArguments.AMOUNT, 1)
                .optional()
                .localized(Lang.COMMAND_ARGUMENT_NAME_PAGE)
                .suggestions((reader, context) -> IntStream.range(1, 11).boxed().map(String::valueOf).toList())
            );
    }

    @Override
    public boolean execute(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                           @NonNull ExcellentCurrency currency) {
        int page = arguments.getInt(CommandArguments.AMOUNT, 1);
        this.manager.showLeaderboard(context.getSender(), currency, page);
        return true;
    }
}
