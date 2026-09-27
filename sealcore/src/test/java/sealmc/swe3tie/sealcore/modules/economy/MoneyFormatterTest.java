package sealmc.swe3tie.sealcore.modules.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the money format the language files are written against: {@code $89.89M}. */
class MoneyFormatterTest {

    private static MoneyFormatter compact() {
        return compact("$", 2);
    }

    private static MoneyFormatter compact(String symbol, int decimals) {
        return new MoneyFormatter(symbol, decimals, true, List.of("K", "M", "B", "T", "Qa", "Qi"));
    }

    @Test
    void plainAmountsKeepTheSymbolAndTwoDecimals() {
        assertEquals("$0.00", compact().format(0.0));
        assertEquals("$89.89", compact().format(89.89));
        assertEquals("$999.00", compact().format(999.0));
    }

    @Test
    void aThousandMovesUpToTheFirstSuffix() {
        assertEquals("$1.00K", compact().format(1_000.0));
        assertEquals("$89.89M", compact().format(89_890_000.0));
        assertEquals("$1.25B", compact().format(1_250_000_000.0));
        assertEquals("$1.00T", compact().format(1_000_000_000_000.0));
    }

    @Test
    void roundingPromotesInsteadOfPrinting1000OfASuffix() {
        assertEquals("$1.00M", compact().format(999_999.0));
        assertEquals("$1.00B", compact().format(999_999_999.0));
    }

    @Test
    void theLargestSuffixIsTheLastStep() {
        assertEquals("$1.00Qi", compact().format(1e18));
    }

    @Test
    void aNegativeBalanceKeepsItsSignInFrontOfTheSymbol() {
        assertEquals("-$5.00", compact().format(-5.0));
        assertEquals("-$1.50K", compact().format(-1_500.0));
    }

    @Test
    void valuesThatAreNotNumbersReadAsZero() {
        assertEquals("$0.00", compact().format(Double.NaN));
        assertEquals("$0.00", compact().format(Double.POSITIVE_INFINITY));
    }

    @Test
    void theSymbolAndTheDecimalsAreConfigurable() {
        assertEquals("đ89.89", compact("đ", 2).format(89.89));
        assertEquals("$90", compact("$", 0).format(89.6));
    }

    @Test
    void compactOffGroupsTheFullAmount() {
        var plain = new MoneyFormatter("$", 2, false, List.of());
        assertEquals("$1,234.56", plain.format(1_234.56));
        assertEquals("$0.00", plain.format(0.0));
    }
}
