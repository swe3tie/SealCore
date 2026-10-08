package sealmc.swe3tie.sealcore.modules.economy.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Utility for parsing and formatting currency amounts in standard or compact representations. */
public final class MoneyFormat {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "Oc", "N", "Dc"};

    private MoneyFormat() {
    }

    /**
     * Parses a string into a positive finite double amount (> 0).
     * Supports shorthand notation such as 10k, 1.5m, 2.5b, 10t (case-insensitive).
     *
     * @return parsed amount, or {@code null} if not a valid positive number
     */
    @Nullable
    public static Double parsePositiveAmount(@Nullable String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        double multiplier = 1D;
        char lastChar = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
        String numPart = trimmed;

        switch (lastChar) {
            case 'k' -> {
                multiplier = 1_000D;
                numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            case 'm' -> {
                multiplier = 1_000_000D;
                numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            case 'b' -> {
                multiplier = 1_000_000_000D;
                numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            case 't' -> {
                multiplier = 1_000_000_000_000D;
                numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            case 'q' -> {
                multiplier = 1_000_000_000_000_000D;
                numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            default -> {
                // No suffix
            }
        }

        if (numPart.isEmpty()) return null;

        try {
            double baseValue = Double.parseDouble(numPart);
            if (Double.isNaN(baseValue) || Double.isInfinite(baseValue) || baseValue <= 0D) {
                return null;
            }
            double total = baseValue * multiplier;
            if (Double.isNaN(total) || Double.isInfinite(total) || total <= 0D) {
                return null;
            }
            return total;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Formats amount into standard representation with comma grouping and up to 2 decimal places.
     * E.g. {@code 1234.56} -> {@code "1,234.56"}, {@code 1000} -> {@code "1,000"}.
     */
    @NotNull
    public static String formatStandard(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return "0";
        BigDecimal bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.scale() <= 0) {
            return String.format(Locale.US, "%,d", bd.longValue());
        }
        long integerPart = bd.longValue();
        String plain = bd.toPlainString();
        int dot = plain.indexOf('.');
        String fraction = dot >= 0 ? plain.substring(dot) : "";
        return String.format(Locale.US, "%,d", integerPart) + fraction;
    }

    /**
     * Formats amount into compact representation with suffixes (K, M, B, T, Q...).
     * E.g. {@code 69770000} -> {@code "69.77M"}, {@code 1500000000} -> {@code "1.5B"}.
     * Amounts below 1,000 use {@link #formatStandard(double)}.
     */
    @NotNull
    public static String formatCompact(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return "0";
        if (amount < 0D) return "-" + formatCompact(-amount);
        if (amount < 1000D) return formatStandard(amount);

        int exp = (int) (Math.log10(amount) / 3D);
        if (exp >= SUFFIXES.length) exp = SUFFIXES.length - 1;

        double base = amount / Math.pow(10D, exp * 3D);
        BigDecimal bd = BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString() + SUFFIXES[exp];
    }
}
