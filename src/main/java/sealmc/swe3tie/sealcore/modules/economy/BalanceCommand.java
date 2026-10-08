package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.command.CommandUsage;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.List;

/** {@code /balance [player]} (alias {@code /bal}) displays own or another player's balance. */
public final class BalanceCommand {

    private static final String USAGE = "/balance [player]";
    private final EconomyModule module;

    public BalanceCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void register() {
        var section = this.module.config().getConfigurationSection("commands.balance");
        String name = section != null ? section.getString("name", "balance") : "balance";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of("bal");
        String permission = section != null ? section.getString("permission", "sealcore.economy.balance") : "sealcore.economy.balance";
        String otherPermission = section != null ? section.getString("other-permission", "sealcore.economy.balance.others") : "sealcore.economy.balance.others";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.balanceSelf(ctx.getSource().getSender()))
            .then(Commands.argument("player", StringArgumentType.word())
                .requires(source -> otherPermission.isEmpty() || source.getSender().hasPermission(otherPermission))
                .suggests((ctx, builder) -> {
                    String rem = builder.getRemainingLowerCase();
                    Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(rem))
                        .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(this::balanceOther)
                .then(Commands.argument("extra", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        CommandUsage.send(this.module.core().lang(), ctx.getSource().getSender(), USAGE);
                        return 0;
                    })));

        this.module.core().commands().register(this.module::isActive, root, "Show a balance", aliases, permission);
    }

    private int balanceSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.module.core().lang().send(sender, "core.players-only");
            return 0;
        }

        CurrencyService currency = this.module.currency();
        if (currency == null) {
            this.module.core().lang().send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        double balance = currency.balance(player, "money");
        String formatted = MoneyFormat.formatStandard(balance);
        this.module.core().lang().send(sender, "economy.balance.self", Placeholder.unparsed("money", formatted));
        return 1;
    }

    private int balanceOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String targetName = StringArgumentType.getString(ctx, "player");

        CurrencyService currency = this.module.currency();
        if (currency == null) {
            this.module.core().lang().send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            double bal = currency.balance(onlineTarget, "money");
            String formatted = MoneyFormat.formatStandard(bal);
            this.module.core().lang().send(sender, "economy.balance.other",
                Placeholder.unparsed("player", onlineTarget.getName()),
                Placeholder.unparsed("money", formatted));
            return 1;
        }

        // Offline player lookup asynchronously
        this.module.core().scheduler().async(() -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                offline = Bukkit.getOfflinePlayer(targetName);
            }
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                this.runOnSender(sender, () ->
                    this.module.core().lang().send(sender, "core.player-not-found", Placeholder.unparsed("player", targetName)));
                return;
            }

            String finalName = offline.getName() != null ? offline.getName() : targetName;
            currency.balance(offline.getUniqueId(), "money").whenComplete((bal, err) -> {
                if (err != null || bal == null) {
                    this.runOnSender(sender, () -> this.module.core().lang().send(sender, "economy.balance.failed"));
                    return;
                }
                String formatted = MoneyFormat.formatStandard(bal);
                this.runOnSender(sender, () -> {
                    this.module.core().lang().send(sender, "economy.balance.other",
                        Placeholder.unparsed("player", finalName),
                        Placeholder.unparsed("money", formatted));
                });
            });
        });

        return 1;
    }

    private void runOnSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            this.module.core().scheduler().entity(player, action);
        } else {
            this.module.core().scheduler().global(action);
        }
    }
}
