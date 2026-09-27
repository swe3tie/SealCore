package sealmc.swe3tie.sealcore.modules.economy;

import java.util.UUID;
import java.util.logging.Level;
import sealmc.swe3tie.sealcore.command.CommandContext;
import sealmc.swe3tie.sealcore.command.CommandNode;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.SealModule;
import sealmc.swe3tie.sealcore.util.Async;

/**
 * {@code /balance [player]}.
 *
 * <p>A balance read is an async call into ExcellentEconomy, so every read happens
 * on the async scheduler and the reply hops back to the sender's thread: a region
 * thread is never blocked on the provider. The console has no balance of its own, so
 * it must name a player.
 */
public final class BalanceCommand {

    private final ModuleContext context;
    private final SealModule<EconomyModuleSpec> module;
    private final TargetResolver resolver;

    public BalanceCommand(ModuleContext context, SealModule<EconomyModuleSpec> module, TargetResolver resolver) {
        this.context = context;
        this.module = module;
        this.resolver = resolver;
    }

    public CommandNode node() {
        EconomyModuleSpec spec = context.specOf(module);
        if (spec == null) {
            throw new IllegalStateException("the economy module has no applied spec");
        }
        CommandNode node = new CommandNode(
            "balance",
            "Show a balance",
            spec.balance().permission(),
            false,
            "[player]",
            ctx -> handle(ctx, spec));
        return node.argSuggestions(partial -> resolver.onlineNames(partial));
    }

    private void handle(CommandContext ctx, EconomyModuleSpec spec) {
        if (!CurrencyGate.requireCurrency(context, ctx.sender(), spec.currency())) {
            return;
        }

        String name = ctx.arg(0);
        if (name == null) {
            var player = ctx.player();
            if (player == null) {
                reply(ctx, "command.usage", "usage", "/balance [player]");
                return;
            }
            offThread(ctx, () -> {
                double balance = read(player.getUniqueId(), spec);
                reply(ctx, "economy.balance-self", "money", money(spec, balance));
            });
            return;
        }

        if (!spec.balance().allowOtherPlayers()) {
            reply(ctx, "core.no-permission");
            return;
        }

        offThread(ctx, () -> {
            PaymentTarget target = resolver.resolve(name, spec.allowOffline());
            if (target == null) {
                reply(ctx, "economy.player-not-found", "player", name);
                return;
            }
            double balance = read(target.uuid(), spec);
            reply(ctx, "economy.balance-other", "player", target.name(), "money", money(spec, balance));
        });
    }

    /**
     * Runs {@code work} off the server thread, so the provider is never awaited on a
     * region thread, and reports anything it throws instead of losing it.
     */
    private void offThread(CommandContext ctx, Runnable work) {
        context.scheduler().async(() -> {
            try {
                work.run();
            } catch (RuntimeException error) {
                context.logger().log(Level.WARNING, "/balance failed", error);
            }
        });
    }

    private double read(UUID playerId, EconomyModuleSpec spec) {
        return Async.await(context.economy().balance(playerId, spec.currency()));
    }

    private static String money(EconomyModuleSpec spec, double amount) {
        return spec.format().formatter().format(amount);
    }

    private void reply(CommandContext ctx, String path, String... keyAndValue) {
        context.reply(ctx.sender(), context.messages().component(path, keyAndValue));
    }
}
