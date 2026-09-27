package su.nightexpress.excellenteconomy.currency.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.command.CommandArguments;
import su.nightexpress.excellenteconomy.config.Config;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.UserManager;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.command.NightCommand;
import su.nightexpress.nightcore.core.config.CoreLang;

public class WalletCommand {

    @NonNull
    public static NightCommand create(@NonNull EconomyPlugin plugin, @NonNull CurrencyManager manager,
                                      @NonNull UserManager userManager) {
        return NightCommand.literal(plugin, Config.WALLET_ALIASES.get(), builder -> builder
            .description(Lang.COMMAND_WALLET_DESC)
            .permission(Perms.COMMAND_WALLET)
            .withArguments(Arguments.playerName(CommandArguments.PLAYER).permission(Perms.COMMAND_WALLET_OTHERS)
                .optional())
            .executes((context, arguments) -> {
                CommandSender sender = context.getSender();

                if (!arguments.contains(CommandArguments.PLAYER)) {
                    if (!context.isPlayer()) {
                        context.errorBadPlayer();
                        return false;
                    }

                    Player player = context.getPlayerOrThrow();
                    return manager.showWallet(player);
                }

                String name = arguments.getString(CommandArguments.PLAYER);

                userManager.loadByNameAsync(name).thenAccept(opt -> {
                    CoinsUser user = opt.orElse(null);
                    if (user == null) {
                        CoreLang.ERROR_INVALID_PLAYER.withPrefix(plugin).send(sender);
                        return;
                    }

                    manager.showWallet(sender, user);
                });

                return true;
            })
        );
    }
}
