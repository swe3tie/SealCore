package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.command.CommandSender;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.module.ModuleContext;

/**
 * Checks a currency is usable before a command does work with it.
 *
 * <p>Without this a typo in {@code currency:} reads as a balance of zero on every
 * player, which looks like wiped accounts rather than a wrong id. The answer is the
 * same message the rest of the plugin uses for an unknown currency.
 */
final class CurrencyGate {

    private CurrencyGate() {
    }

    static boolean requireCurrency(ModuleContext context, CommandSender sender, CurrencyKey currency) {
        if (!context.economy().isReady()) {
            context.reply(sender, context.messages().component("economy.provider-absent"));
            return false;
        }
        if (!context.economy().hasCurrency(currency)) {
            context.reply(sender, context.messages().component("economy.unknown-currency", "currency", currency.id()));
            return false;
        }
        return true;
    }
}
