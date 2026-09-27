package sealmc.swe3tie.sealcore.modules.economy;

import java.util.List;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.module.ModuleSpec;

/**
 * {@code modules/economy.yml} as values.
 *
 * <p>The command names and their aliases are not here: the server only reads
 * commands from {@code plugin.yml}, so keeping a second copy of the name would be a
 * setting that silently does nothing.
 *
 * @param currency      the SealCore currency key the commands act on
 * @param allowOffline  resolve players who have joined before, even while offline
 * @param balance       the {@code /balance} settings
 * @param pay           the {@code /pay} settings
 * @param format        how a balance is written in every message
 */
public record EconomyModuleSpec(
    CurrencyKey currency,
    boolean allowOffline,
    Balance balance,
    Pay pay,
    Format format
) implements ModuleSpec {

    public record Balance(String permission, boolean allowOtherPlayers) {
    }

    public record Pay(
        String permission,
        String bypassPermission,
        double minAmount,
        double maxAmount,
        boolean allowSelf,
        boolean chatMessage,
        boolean actionbar,
        double cooldownSeconds
    ) {

        public long cooldownMillis() {
            return (long) (cooldownSeconds * 1_000.0);
        }
    }

    /**
     * How a balance is written in every message. SealCore formats money itself
     * rather than delegating, so {@code $89.89M} is the same on every server no
     * matter how the currency is set up in ExcellentEconomy.
     */
    public static final class Format {

        private final String symbol;
        private final int decimals;
        private final boolean compact;
        private final List<String> suffixes;

        private volatile MoneyFormatter cached;

        public Format(String symbol, int decimals, boolean compact, List<String> suffixes) {
            this.symbol = symbol;
            this.decimals = decimals;
            this.compact = compact;
            this.suffixes = suffixes;
        }

        public String symbol() {
            return symbol;
        }

        public int decimals() {
            return decimals;
        }

        public boolean compact() {
            return compact;
        }

        public List<String> suffixes() {
            return suffixes;
        }

        public MoneyFormatter formatter() {
            MoneyFormatter formatter = cached;
            if (formatter == null) {
                formatter = new MoneyFormatter(symbol, decimals, compact, suffixes);
                cached = formatter;
            }
            return formatter;
        }
    }

    @Override
    public String describe() {
        return "currency=" + currency.id() + ", commands: balance, pay";
    }
}
