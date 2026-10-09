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

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /pay <player> <amount>} sends money to an online or offline player. */
public final class PayCommand {

    private static final String USAGE = "/pay <player> <amount>";
    private final EconomyModule module;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public PayCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void register() {
        var section = this.module.config().getConfigurationSection("commands.pay");
        String name = section != null ? section.getString("name", "pay") : "pay";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.pay") : "sealcore.economy.pay";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> {
                CommandUsage.send(this.module.core().lang(), ctx.getSource().getSender(), USAGE);
                return 0;
            })
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String rem = builder.getRemainingLowerCase();
                    Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(rem))
                        .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    CommandUsage.send(this.module.core().lang(), ctx.getSource().getSender(), USAGE);
                    return 0;
                })
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(this::executePay)
                    .then(Commands.argument("extra", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandUsage.send(this.module.core().lang(), ctx.getSource().getSender(), USAGE);
                            return 0;
                        }))));

        this.module.core().commands().register(this.module::isActive, root,
            "Send money to a player", aliases, permission);
    }

    public void clearCooldowns() {
        this.cooldowns.clear();
    }

    private int executePay(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var lang = this.module.core().lang();

        if (!(sender instanceof Player playerSender)) {
            lang.send(sender, "core.players-only");
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        String amountInput = StringArgumentType.getString(ctx, "amount");

        Double amount = MoneyFormat.parsePositiveAmount(amountInput);
        if (amount == null) {
            CommandUsage.send(lang, playerSender, USAGE);
            return 0;
        }

        CurrencyService currency = this.module.currency();
        if (currency == null) {
            lang.send(playerSender, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        if (targetName.equalsIgnoreCase(playerSender.getName())) {
            lang.send(playerSender, "economy.pay.self");
            return 0;
        }

        var section = this.module.config().getConfigurationSection("commands.pay");
        boolean bypass = section != null && playerSender.hasPermission(section.getString("bypass-permission", "sealcore.economy.pay.bypass"));

        double min = section != null ? section.getDouble("min-amount", 1D) : 1D;
        double max = section != null ? section.getDouble("max-amount", 1_000_000_000D) : 1_000_000_000D;
        if (!bypass && (amount < min || amount > max)) {
            lang.send(playerSender, "economy.pay.out-of-range",
                Placeholder.unparsed("min", MoneyFormat.formatStandard(min)),
                Placeholder.unparsed("min_formatted", MoneyFormat.formatCompact(min)),
                Placeholder.unparsed("max", MoneyFormat.formatStandard(max)),
                Placeholder.unparsed("max_formatted", MoneyFormat.formatCompact(max)));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = (section != null ? section.getLong("cooldown-seconds", 3L) : 3L) * 1000L;
        if (!bypass && cooldownMs > 0L) {
            Long last = this.cooldowns.get(playerSender.getUniqueId());
            if (last != null && now - last < cooldownMs) {
                long left = (cooldownMs - (now - last) + 999L) / 1000L;
                lang.send(playerSender, "economy.pay.cooldown", Placeholder.unparsed("seconds", String.valueOf(left)));
                return 0;
            }
            this.cooldowns.put(playerSender.getUniqueId(), now);
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            this.processTransfer(playerSender, onlineTarget.getUniqueId(), onlineTarget.getName(), true, amount, currency);
            return 1;
        }

        // Offline player lookup asynchronously
        this.module.core().scheduler().async(() -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                offline = Bukkit.getOfflinePlayer(targetName);
            }
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                this.module.core().scheduler().entity(playerSender, () -> {
                    this.cooldowns.remove(playerSender.getUniqueId());
                    lang.send(playerSender, "core.player-not-found", Placeholder.unparsed("player", targetName));
                });
                return;
            }

            UUID targetId = offline.getUniqueId();
            String finalTargetName = offline.getName() != null ? offline.getName() : targetName;
            this.processTransfer(playerSender, targetId, finalTargetName, false, amount, currency);
        });

        return 1;
    }

    private void processTransfer(Player sender, UUID targetId, String targetName, boolean isOnline,
                                 double amount, CurrencyService currency) {
        String key = "money";
        var lang = this.module.core().lang();
        var scheduler = this.module.core().scheduler();
        String senderName = sender.getName();
        UUID senderId = sender.getUniqueId();

        currency.transfer(senderId, targetId, key, amount, "pay").whenComplete((result, error) -> {
            if (error != null || result == null) {
                this.cooldowns.remove(senderId);
                scheduler.entity(sender, () -> lang.send(sender, "economy.pay.failed"));
                return;
            }

            switch (result) {
                case SUCCESS -> {
                    String unformatted = MoneyFormat.formatStandard(amount);
                    String formatted = MoneyFormat.formatCompact(amount);

                    // Sender feedback: Chat and Actionbar
                    scheduler.entity(sender, () -> {
                        lang.send(sender, "economy.pay.sender.chat",
                            Placeholder.unparsed("amount", unformatted),
                            Placeholder.unparsed("amount_formatted", formatted),
                            Placeholder.unparsed("money", unformatted),
                            Placeholder.unparsed("money_formatted", formatted),
                            Placeholder.unparsed("player", targetName));
                        sender.sendActionBar(lang.component("economy.pay.sender.actionbar", sender,
                            Placeholder.unparsed("amount", unformatted),
                            Placeholder.unparsed("amount_formatted", formatted),
                            Placeholder.unparsed("money", unformatted),
                            Placeholder.unparsed("money_formatted", formatted),
                            Placeholder.unparsed("player", targetName)));
                    });

                    // Receiver feedback
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null && target.isOnline()) {
                        scheduler.entity(target, () -> {
                            lang.send(target, "economy.pay.receiver.chat",
                                Placeholder.unparsed("amount", unformatted),
                                Placeholder.unparsed("amount_formatted", formatted),
                                Placeholder.unparsed("money", unformatted),
                                Placeholder.unparsed("money_formatted", formatted),
                                Placeholder.unparsed("player", senderName));
                            target.sendActionBar(lang.component("economy.pay.receiver.actionbar", target,
                                Placeholder.unparsed("amount", unformatted),
                                Placeholder.unparsed("amount_formatted", formatted),
                                Placeholder.unparsed("money", unformatted),
                                Placeholder.unparsed("money_formatted", formatted),
                                Placeholder.unparsed("player", senderName)));
                        });
                    } else {
                        // Persist offline notification
                        String table = this.module.core().storage().table("economy_notifications");
                        this.module.core().storage().execute(connection -> {
                            String sql = "INSERT INTO " + table + " (receiver_uuid, sender_name, amount, created_at) VALUES (?, ?, ?, ?)";
                            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                                ps.setString(1, targetId.toString());
                                ps.setString(2, senderName);
                                ps.setDouble(3, amount);
                                ps.setLong(4, System.currentTimeMillis());
                                ps.executeUpdate();
                            }
                        });
                    }
                }
                case NOT_ENOUGH -> {
                    this.cooldowns.remove(senderId);
                    scheduler.entity(sender, () -> lang.send(sender, "economy.pay.not-enough"));
                }
                case FAILED -> {
                    this.cooldowns.remove(senderId);
                    scheduler.entity(sender, () -> lang.send(sender, "economy.pay.failed"));
                }
            }
        });
    }
}
