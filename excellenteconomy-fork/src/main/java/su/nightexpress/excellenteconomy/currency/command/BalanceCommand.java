package su.nightexpress.excellenteconomy.currency.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
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

public class BalanceCommand implements CurrencyCommand {

    private final CurrencyManager manager;
    private final UserManager     userManager;

    public BalanceCommand(@NonNull CurrencyManager manager, @NonNull UserManager userManager) {
        this.manager = manager;
        this.userManager = userManager;
    }

    @Override
    public boolean isFallback() {
        return true;
    }

    @Override
    public void build(@NonNull LiteralNodeBuilder builder, @NonNull ExcellentCurrency currency) {
        builder
            .permission(Perms.COMMAND_CURRENCY_BALANCE)
            .description(Lang.COMMAND_CURRENCY_BALANCE_DESC)
            .withArguments(Arguments.playerName(CommandArguments.PLAYER).optional().permission(
                Perms.COMMAND_CURRENCY_BALANCE_OTHERS));
    }

    @Override
    public boolean execute(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                           @NonNull ExcellentCurrency currency) {
        CommandSender sender = context.getSender();

        if (!arguments.contains(CommandArguments.PLAYER)) {
            if (!context.isPlayer()) {
                currency.sendPrefixed(CoreLang.COMMAND_EXECUTION_PLAYER_ONLY, sender);
                return false;
            }

            Player player = context.getPlayerOrThrow();
            this.manager.showBalance(player, currency);
            return true;
        }

        String name = arguments.getString(CommandArguments.PLAYER);

        this.userManager.loadByNameAsync(name).thenAccept(opt -> {
            CoinsUser user = opt.orElse(null);
            if (user == null) {
                currency.sendPrefixed(CoreLang.ERROR_INVALID_PLAYER, sender);
                return;
            }

            this.manager.showBalance(sender, user, currency);
        });

        return true;
    }
}
