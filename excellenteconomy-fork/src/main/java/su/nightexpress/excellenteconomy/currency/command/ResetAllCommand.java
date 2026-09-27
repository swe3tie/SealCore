package su.nightexpress.excellenteconomy.currency.command;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.command.CommandArguments;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;

public class ResetAllCommand {

    @NonNull
    public static LiteralNodeBuilder create(@NonNull CurrencyRegistry registry, @NonNull CurrencyManager manager) {
        return Commands.literal("resetall")
            .permission(Perms.COMMAND_RESET_ALL)
            .description(Lang.COMMAND_RESET_ALL_DESC)
            .withArguments(CommandArguments.currency(registry).optional())
            .executes((context, arguments) -> {
                if (arguments.contains(CommandArguments.CURRENCY)) {
                    ExcellentCurrency currency = arguments.get(CommandArguments.CURRENCY, ExcellentCurrency.class);
                    manager.resetBalances(context.getSender(), currency);
                }
                else {
                    manager.resetBalances(context.getSender());
                }
                return true;
            });
    }
}
