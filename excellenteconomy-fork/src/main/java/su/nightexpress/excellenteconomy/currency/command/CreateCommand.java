package su.nightexpress.excellenteconomy.currency.command;

import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.command.CommandArguments;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.util.Lists;

public class CreateCommand {

    @NonNull
    public static LiteralNodeBuilder create(@NonNull CurrencyManager manager) {
        return Commands.literal("create")
            .permission(Perms.COMMAND_CREATE)
            .description(Lang.COMMAND_CREATE_DESC)
            .withArguments(
                Arguments.string(CommandArguments.NAME).localized(CoreLang.COMMAND_ARGUMENT_NAME_NAME),
                Arguments.string(CommandArguments.SYMBOL).localized(Lang.COMMAND_ARGUMENT_NAME_SYMBOL),
                Arguments.bool(CommandArguments.DECIMALS).localized(Lang.COMMAND_ARGUMENT_NAME_DECIMAL)
                    .optional()
                    .suggestions((reader, context) -> Lists.newList("true", "false"))
            )
            .executes((context, arguments) -> {
                String name = arguments.getString(CommandArguments.NAME);
                String symbol = arguments.getString(CommandArguments.SYMBOL);
                boolean decimals = arguments.getBoolean(CommandArguments.DECIMALS, true);

                return manager.createCurrency(context.getSender(), name, symbol, decimals);
            });
    }
}
