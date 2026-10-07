package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.module.SealSubmodule;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /pay <player> <amount> [currency]} with configurable range, cooldown and currencies. */
final class PaySubmodule extends SealSubmodule<EconomyModule> {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    PaySubmodule(@NotNull EconomyModule parent) {
        super(parent, "pay");
    }

    @Override
    protected void onEnable() {
        var command = this.settings().getConfigurationSection("command");
        String name = command != null ? command.getString("name", "pay") : "pay";
        List<String> aliases = command != null ? command.getStringList("aliases") : List.of();
        String permission = command != null ? command.getString("permission", "sealcore.economy.pay") : "sealcore.economy.pay";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                    .executes(ctx -> this.pay(ctx, null))
                    .then(Commands.argument("currency", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            CurrencyService currency = this.parent.currency();
                            if (currency != null) {
                                this.allowedKeys(currency).stream()
                                    .filter(k -> k.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> this.pay(ctx, StringArgumentType.getString(ctx, "currency"))))));

        this.parent.core().commands().register(() -> this.parent.isActive() && this.isEnabled(), root,
            "Send money to a player", aliases, permission);
    }

    @Override
    protected void onDisable() {
        this.cooldowns.clear();
    }

    private List<String> allowedKeys(CurrencyService currency) {
        List<String> configured = this.settings().getStringList("currencies");
        List<String> keys = (configured.isEmpty() ? List.of(currency.defaultKey()) : configured).stream()
            .map(k -> k.toLowerCase(Locale.ROOT)).toList();
        return keys;
    }

    private int pay(CommandContext<CommandSourceStack> ctx, String currencyInput) throws CommandSyntaxException {
        var lang = this.parent.core().lang();
        if (!(ctx.getSource().getSender() instanceof Player sender)) {
            lang.send(ctx.getSource().getSender(), "core.players-only");
            return 0;
        }

        CurrencyService currency = this.parent.currency();
        if (currency == null) {
            lang.send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) return 0;
        Player target = targets.getFirst();
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            lang.send(sender, "economy.pay.self");
            return 0;
        }

        List<String> allowed = this.allowedKeys(currency);
        String key = currencyInput == null ? allowed.getFirst() : currencyInput.toLowerCase(Locale.ROOT);
        if (!currency.keys().contains(key) || !currency.exists(key)) {
            lang.send(sender, "economy.unknown-currency", Placeholder.unparsed("currency", key));
            return 0;
        }
        if (!allowed.contains(key)) {
            lang.send(sender, "economy.pay.currency-not-allowed", Placeholder.unparsed("currency", currency.displayName(key)));
            return 0;
        }

        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        var settings = this.settings();
        boolean bypass = sender.hasPermission(settings.getString("bypass-permission", "sealcore.economy.pay.bypass"));

        double min = settings.getDouble("min-amount", 1D);
        double max = settings.getDouble("max-amount", 1_000_000_000D);
        if (!bypass && (amount < min || amount > max)) {
            lang.send(sender, "economy.pay.out-of-range",
                Placeholder.unparsed("min", currency.format(key, min)),
                Placeholder.unparsed("max", currency.format(key, max)));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = settings.getLong("cooldown-seconds", 3L) * 1000L;
        if (!bypass && cooldownMs > 0) {
            Long last = this.cooldowns.get(sender.getUniqueId());
            if (last != null && now - last < cooldownMs) {
                long left = (cooldownMs - (now - last) + 999L) / 1000L;
                lang.send(sender, "economy.pay.cooldown", Placeholder.unparsed("seconds", String.valueOf(left)));
                return 0;
            }
            this.cooldowns.put(sender.getUniqueId(), now);
        }

        // Everything below runs off-thread; reply on each player's own scheduler.
        UUID from = sender.getUniqueId();
        UUID to = target.getUniqueId();
        String senderName = sender.getName();
        String targetName = target.getName();

        currency.transfer(from, to, key, amount, "pay").whenComplete((result, error) -> {
            var scheduler = this.parent.core().scheduler();
            if (error != null || result == null) {
                this.cooldowns.remove(from);
                this.parent.core().getLogger().warning("Payment " + senderName + " -> " + targetName + " failed: " + error);
                scheduler.entity(sender, () -> lang.send(sender, "economy.pay.failed"));
                return;
            }
            switch (result) {
                case SUCCESS -> {
                    String formatted = currency.format(key, amount);
                    scheduler.entity(sender, () -> lang.send(sender, "economy.pay.sent",
                        Placeholder.unparsed("amount", formatted), Placeholder.unparsed("player", targetName)));
                    scheduler.entity(target, () -> lang.send(target, "economy.pay.received",
                        Placeholder.unparsed("amount", formatted), Placeholder.unparsed("player", senderName)));
                }
                case NOT_ENOUGH -> {
                    this.cooldowns.remove(from);
                    scheduler.entity(sender, () -> lang.send(sender, "economy.pay.not-enough",
                        Placeholder.unparsed("balance", currency.format(key, currency.balance(sender, key)))));
                }
                case FAILED -> {
                    this.cooldowns.remove(from);
                    scheduler.entity(sender, () -> lang.send(sender, "economy.pay.failed"));
                }
            }
        });
        return 1;
    }
}
