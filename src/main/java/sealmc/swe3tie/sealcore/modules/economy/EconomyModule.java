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
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.command.CommandUsage;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.core.module.SealModule;
import sealmc.swe3tie.sealcore.core.storage.StorageService;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.sql.Statement;
import java.util.List;
import java.util.Set;

/**
 * Economy module integrating with ExcellentEconomy.
 * Features: {@code /balance [player]} (alias {@code /bal}) and {@code /pay <player> <amount>}.
 */
public final class EconomyModule extends SealModule {

    private static final String BALANCE_USAGE = "/balance [player]";
    private final PaySubmodule pay;

    public EconomyModule(@NotNull SealCore core) {
        super(core, "economy");
        this.pay = this.addSubmodule(new PaySubmodule(this));
    }

    @Override
    public @NotNull Set<Hook> requiredHooks() {
        return Set.of(Hook.ECONOMY);
    }

    @Override
    protected void onEnable() throws Exception {
        // Schema migrations for offline payment notifications
        this.core.storage().migrate("economy", List.of(
            new StorageService.Migration(1, (connection, storage) -> {
                String table = storage.table("economy_notifications");
                String idCol = storage.dialect() == StorageService.Dialect.MYSQL
                    ? "id BIGINT AUTO_INCREMENT PRIMARY KEY"
                    : "id INTEGER PRIMARY KEY AUTOINCREMENT";
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + idCol + ", "
                        + "receiver_uuid VARCHAR(36) NOT NULL, "
                        + "sender_name VARCHAR(64) NOT NULL, "
                        + "amount DOUBLE NOT NULL, "
                        + "created_at BIGINT NOT NULL)");
                }
            })
        ));

        this.listen(new EconomyJoinListener(this));
        this.registerBalanceCommand();
        this.registerPlaceholders(this::resolvePlaceholder);
    }

    @Nullable
    CurrencyService currency() {
        return this.core.hooks().currency();
    }

    // /balance [player] ------------------------------------------------------------------------

    private void registerBalanceCommand() {
        var section = this.config().getConfigurationSection("commands.balance");
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
                        CommandUsage.send(this.core.lang(), ctx.getSource().getSender(), BALANCE_USAGE);
                        return 0;
                    })));

        this.core.commands().register(this::isActive, root, "Show a balance", aliases, permission);
    }

    private int balanceSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.core.lang().send(sender, "core.players-only");
            return 0;
        }

        CurrencyService currency = this.currency();
        if (currency == null) {
            this.core.lang().send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        double balance = currency.balance(player, "money");
        String formatted = MoneyFormat.formatStandard(balance);
        this.core.lang().send(sender, "economy.balance.self", Placeholder.unparsed("money", formatted));
        return 1;
    }

    private int balanceOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String targetName = StringArgumentType.getString(ctx, "player");

        CurrencyService currency = this.currency();
        if (currency == null) {
            this.core.lang().send(sender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            double bal = currency.balance(onlineTarget, "money");
            String formatted = MoneyFormat.formatStandard(bal);
            this.core.lang().send(sender, "economy.balance.other",
                Placeholder.unparsed("player", onlineTarget.getName()),
                Placeholder.unparsed("money", formatted));
            return 1;
        }

        // Offline player lookup asynchronously
        this.core.scheduler().async(() -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                offline = Bukkit.getOfflinePlayer(targetName);
            }
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                this.runOnSender(sender, () ->
                    this.core.lang().send(sender, "core.player-not-found", Placeholder.unparsed("player", targetName)));
                return;
            }

            String finalName = offline.getName() != null ? offline.getName() : targetName;
            currency.balance(offline.getUniqueId(), "money").whenComplete((bal, err) -> {
                if (err != null || bal == null) {
                    this.runOnSender(sender, () -> this.core.lang().send(sender, "economy.balance.failed"));
                    return;
                }
                String formatted = MoneyFormat.formatStandard(bal);
                this.runOnSender(sender, () -> {
                    this.core.lang().send(sender, "economy.balance.other",
                        Placeholder.unparsed("player", finalName),
                        Placeholder.unparsed("money", formatted));
                });
            });
        });

        return 1;
    }

    private void runOnSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            this.core.scheduler().entity(player, action);
        } else {
            this.core.scheduler().global(action);
        }
    }

    // Placeholders ------------------------------------------------------------------------------

    @Nullable
    private String resolvePlaceholder(@Nullable OfflinePlayer player, @NotNull String params) {
        CurrencyService currency = this.currency();
        if (currency == null || player == null) return null;

        Player online = player.getPlayer();
        double balance = online != null ? currency.balance(online, "money") : 0D;

        return switch (params) {
            case "balance" -> MoneyFormat.formatStandard(balance);
            case "balance_compact" -> MoneyFormat.formatCompact(balance);
            default -> null;
        };
    }
}
