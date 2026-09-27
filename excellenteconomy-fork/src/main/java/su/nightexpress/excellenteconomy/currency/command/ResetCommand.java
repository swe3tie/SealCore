package su.nightexpress.excellenteconomy.currency.command;

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.excellenteconomy.command.CommandArguments;
import su.nightexpress.excellenteconomy.command.currency.CurrencyCommand;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.UserManager;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;
import su.nightexpress.nightcore.core.config.CoreLang;

public class ResetCommand implements CurrencyCommand {

    private final CurrencyManager manager;
    private final UserManager     userManager;

    public ResetCommand(@NonNull CurrencyManager manager, @NonNull UserManager userManager) {
        this.manager = manager;
        this.userManager = userManager;
    }

    @Override
    public boolean isFallback() {
        return false;
    }

    @Override
    public void build(@NonNull LiteralNodeBuilder builder, @NonNull ExcellentCurrency currency) {
        builder
            .permission(Perms.COMMAND_RESET)
            .description(Lang.COMMAND_RESET_DESC)
            .withArguments(Arguments.playerName(CommandArguments.PLAYER))
            .withFlags(CommandArguments.FLAG_SILENT, CommandArguments.FLAG_NO_FEEDBACK);
    }

    @Override
    public boolean execute(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                           @NonNull ExcellentCurrency currency) {
        CommandSender sender = context.getSender();
        String playerName = arguments.getString(CommandArguments.PLAYER);

        this.userManager.loadByNameAsync(playerName).thenAccept(opt -> {
            CoinsUser user = opt.orElse(null);
            if (user == null) {
                currency.sendPrefixed(CoreLang.ERROR_INVALID_PLAYER, sender);
                return;
            }

            OperationContext operationContext = OperationContext.of(sender)
                .silentFor(NotificationTarget.USER, context.hasFlag(CommandArguments.FLAG_SILENT))
                .silentFor(NotificationTarget.EXECUTOR, context.hasFlag(CommandArguments.FLAG_NO_FEEDBACK));

            this.manager.reset(operationContext, user, currency);
        });
        return true;
    }
}
