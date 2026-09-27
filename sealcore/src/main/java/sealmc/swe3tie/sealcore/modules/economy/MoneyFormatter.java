package sealmc.swe3tie.sealcore.modules.economy;

import java.util.List;
import java.util.Locale;

/**
 * Writes a balance the way the server shows it: {@code $89.89M}.
 *
 * <p>A value of 1000 or more picks the smallest suffix that keeps it under 1000,
 * and rounding is allowed to promote it again, so 999 999 reads {@code $1.00M}
 * rather than {@code $1000.00K}. Two decimals always, and a negative balance keeps
 * its sign in front of the symbol.
 */
public final class MoneyFormatter {

    private final String symbol;
    private final int decimals;
    private final boolean compact;
    private final List<String> suffixes;

    public MoneyFormatter(String symbol, int decimals, boolean compact, List<String> suffixes) {
        this.symbol = symbol;
        this.decimals = clamp(decimals);
        this.compact = compact;
        this.suffixes = suffixes;
    }

    public String format(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return symbol + fixed(0.0);
        }
        boolean negative = amount < 0.0;
        double value = Math.abs(amount);
        int unit = 0;

        if (compact) {
            // Scaling and promotion are the same loop: a value is scaled again
            // whenever the rounded form would read 1000 or more.
            int guard = 0;
            while (guard++ <= suffixes.size()) {
                if (rounded(value) < 1_000.0 || unit >= suffixes.size()) {
                    break;
                }
                value = rounded(value) / 1_000.0;
                unit++;
            }
        }

        String body;
        if (compact && unit > 0) {
            body = fixed(value) + suffixes.get(unit - 1);
        } else {
            body = grouped(value);
        }
        return (negative ? "-" : "") + symbol + body;
    }

    private static int clamp(int decimals) {
        return Math.max(0, Math.min(8, decimals));
    }

    private double rounded(double value) {
        double factor = 1.0;
        for (int i = 0; i < decimals; i++) {
            factor *= 10.0;
        }
        // rint, not Math.round(long): a long would overflow past 1e16 and a
        // quintillion balance would come back as 92.23Qa.
        return Math.rint(value * factor) / factor;
    }

    private String fixed(double value) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private String grouped(double value) {
        return String.format(Locale.ROOT, "%," + "." + decimals + "f", value);
    }
}
