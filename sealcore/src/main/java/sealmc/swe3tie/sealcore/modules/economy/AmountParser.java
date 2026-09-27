package sealmc.swe3tie.sealcore.modules.economy;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads an amount a player typed: {@code 250}, {@code 1.5k}, {@code 2m}, {@code 1,250}.
 *
 * <p>Returns null for anything that is not a usable amount, so the caller only has
 * to turn null into one message. Zero, negatives, {@code NaN} and trailing rubbish
 * such as {@code 1kk} are all rejected.
 */
public final class AmountParser {

    /**
     * The token has to be a plain decimal: an exponent and a trailing float marker
     * are allowed, hex, {@code NaN} and {@code Infinity} are not.
     */
    private static final Pattern NUMBER =
        Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fFdD]?");

    private static final Map<Character, Double> MULTIPLIERS = Map.of(
        'k', 1_000.0,
        'm', 1_000_000.0,
        'b', 1_000_000_000.0,
        't', 1_000_000_000_000.0);

    private AmountParser() {
    }

    public static Double parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (text.isEmpty()) {
            return null;
        }

        double multiplier = 1.0;
        char last = text.charAt(text.length() - 1);
        if (MULTIPLIERS.containsKey(last)) {
            multiplier = MULTIPLIERS.get(last);
            text = text.substring(0, text.length() - 1);
        }

        Double value = toDouble(text);
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        double result = value * multiplier;
        if (!Double.isFinite(result) || result <= 0.0) {
            return null;
        }
        return result;
    }

    private static Double toDouble(String text) {
        if (text.isEmpty() || !NUMBER.matcher(text).matches()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
