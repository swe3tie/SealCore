package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService.LeaderboardEntry;
import sealmc.swe3tie.sealcore.core.module.SealSubmodule;

import java.util.List;
import java.util.Optional;

/** {@code /baltop [currency] [page]} reading ExcellentEconomy's leaderboard. */
final class BaltopSubmodule extends SealSubmodule<EconomyModule> {

    BaltopSubmodule(@NotNull EconomyModule parent) {
        super(parent, "baltop");
    }

    @Override
    protected void onEnable() {
        var command = this.settings().getConfigurationSection("command");
        String name = command != null ? command.getString("name", "baltop") : "baltop";
        List<String> aliases = command != null ? command.getStringList("aliases") : List.of();
        String permission = command != null ? command.getString("permission", "sealcore.economy.baltop") : "sealcore.economy.baltop";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.show(ctx.getSource().getSender(), null, 1))
            .then(Commands.argument("currency", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    CurrencyService currency = this.parent.currency();
                    if (currency != null) {
                        currency.keys().stream().filter(k -> k.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> this.show(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "currency"), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> this.show(ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "currency"), IntegerArgumentType.getInteger(ctx, "page")))));

        this.parent.core().commands().register(() -> this.parent.isActive() && this.isEnabled(), root,
            "Show the richest players", aliases, permission);
    }

    private int show(CommandSender sender, String currencyInput, int page) {
        var lang = this.parent.core().lang();
        CurrencyService currency = this.parent.currency();
        if (currency == null) {
            lang.send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        String key = currencyInput == null ? currency.defaultKey() : this.parent.resolveKey(currency, currencyInput);
        if (key == null) {
            lang.send(sender, "economy.unknown-currency", Placeholder.unparsed("currency", currencyInput));
            return 0;
        }

        Optional<List<LeaderboardEntry>> board = currency.leaderboard(key);
        if (board.isEmpty()) {
            lang.send(sender, "economy.baltop.unavailable");
            return 0;
        }
        List<LeaderboardEntry> entries = board.get();
        if (entries.isEmpty()) {
            lang.send(sender, "economy.baltop.empty");
            return 1;
        }

        int size = Math.max(1, this.settings().getInt("page-size", 10));
        int pages = (entries.size() + size - 1) / size;
        int current = Math.min(page, pages);
        lang.send(sender, "economy.baltop.header",
            Placeholder.unparsed("currency", currency.displayName(key)),
            Placeholder.unparsed("page", String.valueOf(current)),
            Placeholder.unparsed("pages", String.valueOf(pages)));

        int from = (current - 1) * size;
        for (LeaderboardEntry entry : entries.subList(from, Math.min(entries.size(), from + size))) {
            lang.send(sender, "economy.baltop.entry",
                Placeholder.unparsed("position", String.valueOf(entry.position())),
                Placeholder.unparsed("player", entry.name()),
                Placeholder.unparsed("amount", currency.format(key, entry.balance())));
        }
        return 1;
    }
}
