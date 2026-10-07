package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.module.SealModule;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reference module: {@code /balance}, plus the {@code pay} and {@code baltop} submodules, on top of
 * ExcellentEconomy. Placeholders: {@code %sealcore_economy_balance_<currency>[_formatted|_compact]%}.
 */
public final class EconomyModule extends SealModule {

    private final PaySubmodule pay;
    private final BaltopSubmodule baltop;

    public EconomyModule(@NotNull SealCore core) {
        super(core, "economy");
        this.pay = this.addSubmodule(new PaySubmodule(this));
        this.baltop = this.addSubmodule(new BaltopSubmodule(this));
    }

    @Override
    public @NotNull Set<Hook> requiredHooks() {
        return Set.of(Hook.ECONOMY);
    }

    @Override
    protected void onEnable() {
        this.registerBalanceCommand();
        this.registerPlaceholders(this::resolvePlaceholder);
    }

    /** The currency backend, or {@code null} if ExcellentEconomy went away. */
    @Nullable
    CurrencyService currency() {
        return this.core.hooks().currency();
    }

    /** Resolves user input to a configured currency key, or {@code null}. */
    @Nullable
    String resolveKey(@NotNull CurrencyService currency, @NotNull String input) {
        String key = input.toLowerCase(Locale.ROOT);
        return currency.keys().contains(key) && currency.exists(key) ? key : null;
    }

    // /balance ----------------------------------------------------------------------------------

    private void registerBalanceCommand() {
        var section = this.config().getConfigurationSection("commands.balance");
        String name = section != null ? section.getString("name", "balance") : "balance";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.balance") : "sealcore.economy.balance";
        String otherPermission = section != null ? section.getString("other-permission", "sealcore.economy.balance.others") : "sealcore.economy.balance.others";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.balanceSelf(ctx.getSource().getSender()))
            .then(Commands.argument("player", ArgumentTypes.player())
                .requires(source -> source.getSender().hasPermission(otherPermission))
                .executes(ctx -> this.balanceOther(ctx)));

        this.core.commands().register(this::isActive, root, "Show a balance", aliases, permission);
    }

    private int balanceSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.core.lang().send(sender, "core.players-only");
            return 0;
        }
        this.sendBalances(sender, player, "economy.balance.self-header");
        return 1;
    }

    private int balanceOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) return 0;
        Player target = targets.getFirst();
        this.sendBalances(sender, target, "economy.balance.other-header");
        return 1;
    }

    private void sendBalances(CommandSender viewer, Player target, String headerKey) {
        CurrencyService currency = this.currency();
        if (currency == null) {
            this.core.lang().send(viewer, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return;
        }
        this.core.lang().send(viewer, headerKey, Placeholder.unparsed("player", target.getName()));

        List<String> keys = this.config().getStringList("balance-currencies");
        if (keys.isEmpty()) keys = List.copyOf(currency.keys());
        for (String raw : keys) {
            String key = raw.toLowerCase(Locale.ROOT);
            if (!currency.exists(key)) continue;
            this.core.lang().send(viewer, "economy.balance.entry",
                Placeholder.unparsed("currency", currency.displayName(key)),
                Placeholder.unparsed("amount", currency.format(key, currency.balance(target, key))));
        }
    }

    // Placeholders ------------------------------------------------------------------------------

    @Nullable
    private String resolvePlaceholder(@Nullable OfflinePlayer player, @NotNull String params) {
        CurrencyService currency = this.currency();
        if (currency == null || !params.startsWith("balance_")) return null;

        String rest = params.substring("balance_".length());
        String mode = "raw";
        for (String suffix : new String[]{"_formatted", "_compact"}) {
            if (rest.endsWith(suffix)) {
                mode = suffix.substring(1);
                rest = rest.substring(0, rest.length() - suffix.length());
            }
        }
        String key = this.resolveKey(currency, rest);
        if (key == null) return null;

        Player online = player == null ? null : player.getPlayer();
        double balance = online == null ? 0D : currency.balance(online, key);
        return switch (mode) {
            case "formatted" -> currency.format(key, balance);
            case "compact" -> currency.formatCompact(key, balance);
            default -> String.valueOf(balance);
        };
    }
}
