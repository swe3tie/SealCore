package sealmc.swe3tie.sealcore.modules.economy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MoneyFormatTest {

    @Test
    void parsesPositiveAmounts() {
        assertEquals(100.0, MoneyFormat.parsePositiveAmount("100"));
        assertEquals(69.77, MoneyFormat.parsePositiveAmount("69.77"));
        assertNull(MoneyFormat.parsePositiveAmount("0"));
        assertNull(MoneyFormat.parsePositiveAmount("-10"));
        assertNull(MoneyFormat.parsePositiveAmount("abc"));
        assertNull(MoneyFormat.parsePositiveAmount(""));
        assertNull(MoneyFormat.parsePositiveAmount(null));
        assertNull(MoneyFormat.parsePositiveAmount("NaN"));
        assertNull(MoneyFormat.parsePositiveAmount("Infinity"));
    }

    @Test
    void parsesShorthandNotation() {
        assertEquals(1_000.0, MoneyFormat.parsePositiveAmount("1k"));
        assertEquals(10_000.0, MoneyFormat.parsePositiveAmount("10K"));
        assertEquals(1_500_000.0, MoneyFormat.parsePositiveAmount("1.5m"));
        assertEquals(69_770_000.0, MoneyFormat.parsePositiveAmount("69.77M"));
        assertEquals(2_000_000_000.0, MoneyFormat.parsePositiveAmount("2b"));
        assertEquals(5_000_000_000_000.0, MoneyFormat.parsePositiveAmount("5t"));
        assertNull(MoneyFormat.parsePositiveAmount("k"));
        assertNull(MoneyFormat.parsePositiveAmount("-5m"));
        assertNull(MoneyFormat.parsePositiveAmount("0k"));
        assertNull(MoneyFormat.parsePositiveAmount("abcM"));
    }

    @Test
    void formatsStandard() {
        assertEquals("0", MoneyFormat.formatStandard(0));
        assertEquals("500", MoneyFormat.formatStandard(500));
        assertEquals("1,000", MoneyFormat.formatStandard(1000));
        assertEquals("1,234.56", MoneyFormat.formatStandard(1234.56));
        assertEquals("1,234.5", MoneyFormat.formatStandard(1234.5));
        assertEquals("10,000,000", MoneyFormat.formatStandard(10_000_000));
    }

    @Test
    void formatsCompact() {
        assertEquals("500", MoneyFormat.formatCompact(500));
        assertEquals("999", MoneyFormat.formatCompact(999));
        assertEquals("1K", MoneyFormat.formatCompact(1000));
        assertEquals("1.5K", MoneyFormat.formatCompact(1500));
        assertEquals("69.77M", MoneyFormat.formatCompact(69_770_000));
        assertEquals("1.5B", MoneyFormat.formatCompact(1_500_000_000));
        assertEquals("2T", MoneyFormat.formatCompact(2_000_000_000_000L));
    }
}
