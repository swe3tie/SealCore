package sealmc.swe3tie.sealcore.modules.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AmountParserTest {

    @Test
    void plainDecimalsAreReadAsTyped() {
        assertEquals(100.0, AmountParser.parse("100"));
        assertEquals(12.5, AmountParser.parse("12.5"));
        assertEquals(1250.0, AmountParser.parse("1,250"));
    }

    @Test
    void aSuffixMultipliesByAThousandSteps() {
        assertEquals(1_500.0, AmountParser.parse("1.5k"));
        assertEquals(2_000_000.0, AmountParser.parse("2m"));
        assertEquals(3_000_000_000.0, AmountParser.parse("3B"));
        assertEquals(4_000_000_000_000.0, AmountParser.parse("4t"));
    }

    @Test
    void surroundingSpaceAndCaseDoNotMatter() {
        assertEquals(1_500.0, AmountParser.parse("  1.5K "));
    }

    @Test
    void anythingThatIsNotAUsableAmountIsRejected() {
        assertNull(AmountParser.parse(""));
        assertNull(AmountParser.parse("   "));
        assertNull(AmountParser.parse("abc"));
        assertNull(AmountParser.parse("0"));
        assertNull(AmountParser.parse("-5"));
        assertNull(AmountParser.parse("1kk"));
        assertNull(AmountParser.parse("5 dollars"));
        assertNull(AmountParser.parse("k"));
        assertNull(AmountParser.parse("NaN"));
        assertNull(AmountParser.parse("Infinity"));
    }
}
