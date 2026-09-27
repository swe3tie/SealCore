package sealmc.swe3tie.sealcore.modules.economy;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.command.CommandContext;
import sealmc.swe3tie.sealcore.command.CommandNode;
import sealmc.swe3tie.sealcore.economy.EconomyResult;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.SealModule;
import sealmc.swe3tie.sealcore.util.Async;

/**
 * {@code /pay <player> <amount>}.
 *
 * <p>Everything that can be rejected without touching storage is rejected on the
 * command thread; the transfer itself runs off it, because ExcellentEconomy's API is
 * asynchronous and blocking a region thread would stall the server. The sender gets
 * chat and an actionbar, and so does the receiver when they are online.
 */
public final class PayCommand {

    private final ModuleContext context;
    private final SealModule<EconomyModuleSpec> module;
    private final Map<UUID, Long> cooldowns;
    private final TargetResolver resolver;

    public PayCommand(
        ModuleContext context,
        SealModule<EconomyModuleSpec> module,
        Map<UUID, Long> cooldowns,
        TargetResolver resolver
    ) {
        this.context = context;
        this.module = module;
        this.cooldowns = cooldowns;
        this.resolver = resolver;
    }

    public CommandNode node() {
        EconomyModuleSpec spec = context.specOf(module);
        if (spec == null) {
            throw new IllegalStateException("the economy module has no applied spec");
        }
        CommandNode node = new CommandNode(
            "pay",
            "Send money to another player",
            spec.pay().permission(),
            true,
            "<player> <amount>",
            ctx -> handle(ctx, spec));
        return node.argSuggestions(partial -> resolver.onlineNames(partial));
    }

    private void handle(CommandContext ctx, EconomyModuleSpec spec) {
        var player = ctx.player();
        if (player == null) {
            return;
        }
        if (!CurrencyGate.requireCurrency(context, ctx.sender(), spec.currency())) {
            return;
        }
        if (ctx.args().size() != 2) {
            reply(ctx, "command.usage", "usage", "/pay <player> <amount>");
            return;
        }

        String name = ctx.string(0, "");
        if (!spec.pay().allowSelf() && name.equalsIgnoreCase(player.getName())) {
            reply(ctx, "economy.pay-self");
            return;
        }

        Double amount = AmountParser.parse(ctx.string(1, ""));
        if (amount == null) {
            reply(ctx, "economy.invalid-amount");
            return;
        }

        boolean bypass = player.hasPermission(spec.pay().bypassPermission());
        if (!bypass) {
            if (amount < spec.pay().minAmount() || amount > spec.pay().maxAmount()) {
                reply(ctx, "economy.pay-amount-range",
                    "min", spec.format().formatter().format(spec.pay().minAmount()),
                    "max", spec.format().formatter().format(spec.pay().maxAmount()));
                return;
            }
            if (onCooldown(player.getUniqueId(), spec)) {
                reply(ctx, "economy.pay-cooldown", "seconds", cooldownText(spec));
                return;
            }
        }

        context.scheduler().async(() -> {
            try {
                transfer(player, name, amount, spec);
            } catch (RuntimeException error) {
                context.logger().log(Level.WARNING, "/pay failed", error);
                reply(ctx, "economy.pay-failed");
            }
        });
    }

    private void transfer(Player sender, String name, double amount, EconomyModuleSpec spec) {
        PaymentTarget target = resolver.resolve(name, spec.allowOffline());
        if (target == null) {
            reply(sender, "economy.player-not-found", "player", name);
            return;
        }

        EconomyResult result = Async.await(
            context.economy().transfer(sender.getUniqueId(), target.uuid(), spec.currency(), amount, "pay"));
        if (result instanceof EconomyResult.Failure failure) {
            replyFailure(sender, spec, amount, failure);
            return;
        }

        markCooldown(sender.getUniqueId(), spec);
        String money = spec.format().formatter().format(amount);
        // The cache is only a shortcut, so a build without the engine still pays.
        var cache = context.placeholders();
        if (cache != null) {
            cache.invalidate(sender.getUniqueId());
            cache.invalidate(target.uuid());
        }

        if (spec.pay().chatMessage()) {
            reply(sender, "economy.pay-sent-chat", "money", money, "player", target.name());
        }
        if (spec.pay().actionbar()) {
            context.actionbar(sender,
                context.messages().component("economy.pay-sent-actionbar", "money", money, "player", target.name()));
        }

        var receiver = target.player();
        if (receiver == null) {
            return;
        }
        if (spec.pay().chatMessage()) {
            reply(receiver, "economy.pay-received-chat", "player", sender.getName(), "money", money);
        }
        if (spec.pay().actionbar()) {
            context.actionbar(receiver,
                context.messages().component("economy.pay-received-actionbar", "player", sender.getName(), "money", money));
        }
    }

    private void replyFailure(
        Player sender,
        EconomyModuleSpec spec,
        double amount,
        EconomyResult.Failure failure
    ) {
        var formatter = spec.format().formatter();
        if (failure.reason() == EconomyResult.Reason.INSUFFICIENT_FUNDS) {
            double balance = 0.0;
            try {
                balance = Async.await(context.economy().balance(sender.getUniqueId(), spec.currency()));
            } catch (RuntimeException unreadable) {
                // The provider is the reason the transfer failed; report zero rather
                // than replacing that answer with a second, more confusing error.
            }
            reply(sender, "economy.insufficient-funds",
                "amount", formatter.format(amount),
                "balance", formatter.format(balance));
            return;
        }
        switch (failure.reason()) {
            case UNKNOWN_CURRENCY -> reply(sender, "economy.unknown-currency", "currency", spec.currency().id());
            case PROVIDER_ABSENT -> reply(sender, "economy.provider-absent");
            default -> reply(sender, "economy.pay-failed");
        }
    }

    private void reply(CommandContext ctx, String path, String... keyAndValue) {
        context.reply(ctx.sender(), context.messages().component(path, keyAndValue));
    }

    private void reply(Player player, String path, String... keyAndValue) {
        context.reply(player, context.messages().component(path, keyAndValue));
    }

    private boolean onCooldown(UUID playerId, EconomyModuleSpec spec) {
        long cooldown = spec.pay().cooldownMillis();
        if (cooldown <= 0L) {
            return false;
        }
        Long last = cooldowns.get(playerId);
        return last != null && System.currentTimeMillis() - last < cooldown;
    }

    private void markCooldown(UUID playerId, EconomyModuleSpec spec) {
        if (spec.pay().cooldownMillis() <= 0L) {
            return;
        }
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    private static String cooldownText(EconomyModuleSpec spec) {
        double seconds = spec.pay().cooldownSeconds();
        if (seconds % 1.0 == 0.0) {
            return Integer.toString((int) seconds);
        }
        return String.format(Locale.ROOT, "%.1f", seconds);
    }
}
