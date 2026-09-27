package su.nightexpress.excellenteconomy.currency.command;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlaceholders;
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
import su.nightexpress.nightcore.util.placeholder.CommonPlaceholders;

public class PaymentsCommand implements CurrencyCommand {

    private final CurrencyManager manager;
    private final UserManager     userManager;

    public PaymentsCommand(@NonNull CurrencyManager manager, @NonNull UserManager userManager) {
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
            .permission(Perms.COMMAND_CURRENCY_PAYMENTS)
            .description(Lang.COMMAND_CURRENCY_PAYMENTS_DESC)
            .withArguments(Arguments.playerName(CommandArguments.PLAYER).permission(
                Perms.COMMAND_CURRENCY_PAYMENTS_OTHERS).optional())
            .withFlags(CommandArguments.FLAG_SILENT);
    }

    @Override
    public boolean execute(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                           @NonNull ExcellentCurrency currency) {
        if (!arguments.contains(CommandArguments.PLAYER)) {
            if (!context.isPlayer()) {
                currency.sendPrefixed(CoreLang.COMMAND_EXECUTION_PLAYER_ONLY, context.getSender());
                return false;
            }

            Player player = context.getPlayerOrThrow();
            return this.manager.togglePayments(player, currency);
        }

        String name = arguments.getString(CommandArguments.PLAYER);
        boolean silent = context.hasFlag(CommandArguments.FLAG_SILENT);

        this.userManager.loadByNameAsync(name).thenAccept(opt -> {
            CoinsUser user = opt.orElse(null);
            if (user == null) {
                currency.sendPrefixed(CoreLang.ERROR_INVALID_PLAYER, context.getSender());
                return;
            }

            this.manager.togglePayments(user, currency, silent);

            if (!user.isHolder(context.getSender())) {
                currency.sendPrefixed(Lang.COMMAND_CURRENCY_PAYMENTS_TARGET, context.getSender(), builder -> builder
                    .with(CommonPlaceholders.PLAYER_NAME, user::getName)
                    .with(EconomyPlaceholders.GENERIC_STATE, () -> CoreLang.STATE_ENABLED_DISALBED.get(user.getSettings(
                        currency).isPaymentsEnabled()))
                );
            }
        });

        return true;
    }
}
