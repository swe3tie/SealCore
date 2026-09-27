package sealmc.swe3tie.sealcore.modules.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.ModuleSection;
import sealmc.swe3tie.sealcore.module.SealCommand;
import sealmc.swe3tie.sealcore.module.SealModule;

/**
 * The economy feature module: {@code /balance} and {@code /pay}, with
 * {@code /sell} and {@code /shop} to come in the same file.
 *
 * <p>The module itself only reads its file and hands out its commands; the work lives
 * in {@link BalanceCommand} and {@link PayCommand}, which take the spec they need so
 * they can be built and exercised without a server.
 */
public final class EconomyModule implements SealModule<EconomyModuleSpec> {

    /** K, M, B, T, then the quadrillion and quintillion steps. */
    private static final List<String> DEFAULT_SUFFIXES = List.of("K", "M", "B", "T", "Qa", "Qi");

    /** Anti spam state, owned here so {@link #disable()} can clear it. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public EconomyModuleSpec parse(ModuleSection section) {
        return new EconomyModuleSpec(
            CurrencyKey.of(section.string("currency", "money")),
            section.bool("players.allow-offline", true),
            new EconomyModuleSpec.Balance(
                section.string("balance.permission", "sealcore.economy.balance"),
                section.bool("balance.allow-other-players", true)),
            new EconomyModuleSpec.Pay(
                section.string("pay.permission", "sealcore.economy.pay"),
                section.string("pay.bypass-permission", "sealcore.economy.bypass"),
                section.decimal("pay.min-amount", 1.0, 0.0, Double.MAX_VALUE),
                section.decimal("pay.max-amount", 1_000_000_000.0, 0.0, Double.MAX_VALUE),
                section.bool("pay.allow-self", false),
                section.bool("pay.chat-message", true),
                section.bool("pay.actionbar", true),
                section.decimal("pay.cooldown-seconds", 0.0, 0.0, 86_400.0)),
            new EconomyModuleSpec.Format(
                section.string("format.symbol", "$"),
                section.integer("format.decimals", 2, 0, 8),
                section.bool("format.compact", true),
                suffixFallback(section)));
    }

    private static List<String> suffixFallback(ModuleSection section) {
        List<String> suffixes = section.stringList("format.suffixes");
        return suffixes.isEmpty() ? DEFAULT_SUFFIXES : suffixes;
    }

    @Override
    public List<String> validate(EconomyModuleSpec spec, ModuleSection section) {
        List<String> problems = new ArrayList<>();
        if (spec.pay().minAmount() > spec.pay().maxAmount()) {
            problems.add("pay.min-amount (" + spec.pay().minAmount() + ") is above pay.max-amount ("
                + spec.pay().maxAmount() + ")");
        }
        if (spec.format().symbol().isBlank()) {
            problems.add("format.symbol is empty, so amounts would render without a currency");
        }
        for (String suffix : spec.format().suffixes()) {
            if (suffix.isBlank()) {
                problems.add("format.suffixes contains an empty entry");
                break;
            }
        }
        return problems;
    }

    /**
     * Nothing to apply: the commands are rebuilt from the applied spec on every bind,
     * so there is no live state this module owns.
     */
    @Override
    public void enable(EconomyModuleSpec spec) {
        // Intentionally empty.
    }

    @Override
    public List<SealCommand> commands(ModuleContext context) {
        return List.of(
            new SealCommand(this, "balance", "Show a balance", "/balance [player]",
                ctx -> new BalanceCommand(ctx, this, new TargetResolver(ctx)).node()),
            new SealCommand(this, "pay", "Send money to another player", "/pay <player> <amount>",
                ctx -> new PayCommand(ctx, this, cooldowns, new TargetResolver(ctx)).node()));
    }

    @Override
    public void disable() {
        cooldowns.clear();
    }
}
