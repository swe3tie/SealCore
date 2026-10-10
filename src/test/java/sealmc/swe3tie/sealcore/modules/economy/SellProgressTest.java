package sealmc.swe3tie.sealcore.modules.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SellProgressTest {

    @Test
    void buildsFortyRoundedThresholdsToThreeMultiplier() {
        assertEquals(40, SellProgress.thresholds().size());
        assertEquals(25_000D, SellProgress.thresholds().getFirst().required());
        assertEquals(50_000D, SellProgress.thresholds().get(1).required());
        assertEquals(1_000_000D, SellProgress.thresholds().get(5).required());
        assertEquals(1_500_000D, SellProgress.thresholds().get(6).required());
        assertEquals(2_000_000D, SellProgress.thresholds().get(7).required());
        assertEquals(3_000_000D, SellProgress.thresholds().get(8).required());
        assertEquals(5_000_000D, SellProgress.thresholds().get(9).required());
        assertEquals(500_000_000_000D, SellProgress.thresholds().getLast().required());
        assertEquals(3.0D, SellProgress.thresholds().getLast().multiplier());
    }

    @Test
    void unlocksMultiplierOnlyAtThreshold() {
        assertEquals(1.0D, SellProgress.multiplier(24_999D));
        assertEquals(1.05D, SellProgress.multiplier(25_000D));
        assertEquals(1.10D, SellProgress.multiplier(50_000D));
        assertEquals(1.35D, SellProgress.multiplier(1_999_999D));
        assertEquals(1.40D, SellProgress.multiplier(2_000_000D));
        assertEquals(3.0D, SellProgress.multiplier(Double.MAX_VALUE));
        assertNotNull(SellProgress.next(0D));
        assertNull(SellProgress.next(Double.MAX_VALUE));
    }

    @Test
    void rendersCategoryAndMilestonePlaceholdersFromRoundedData() {
        var category = SellProgress.placeholders(SellCategory.CROPS, 2_000_000D);
        assertEquals("Cây trồng", category.get("SECTION"));
        assertEquals("1.40", category.get("MULTIPLIER"));
        assertEquals("1.45", category.get("NEXT_MULTIPLIER"));
        assertEquals("3,000,000", category.get("NEXT_TARGET"));
        assertEquals("3M", category.get("NEXT_TARGET_FORMATTED"));

        var milestone = SellProgress.thresholdPlaceholders(SellCategory.CROPS, 1_500_000D,
            SellProgress.thresholds().get(7), "x", "-");
        assertEquals("2,000,000", milestone.get("NEED"));
        assertEquals("2M", milestone.get("NEED_FORMATTED"));
        assertEquals("500,000", milestone.get("REMAINING"));
        assertEquals("500K", milestone.get("REMAINING_FORMATTED"));
        assertEquals("1.40", milestone.get("MULTIPLIER"));
        assertEquals(16, milestone.get("PROGRESS_BAR").length());
    }

    @Test
    void usesConfiguredCategoryNameForSectionWithoutFormattingTags() {
        var placeholders = SellProgress.placeholders(SellCategory.CROPS, 0D, "<#9CC0D9>ᴄâʏ ᴛʀồɴɢ");
        assertEquals("ᴄâʏ ᴛʀồɴɢ", placeholders.get("SECTION"));
    }

    @Test
    void setsIncompleteMilestonesToZeroProgress() {
        var incomplete = SellProgress.thresholdPlaceholders(SellCategory.CROPS, 10_000D,
            SellProgress.thresholds().get(1), "x", "-");
        assertEquals("0.0", incomplete.get("PERCENTAGE"));
        assertEquals("10,000", incomplete.get("CURRENT"));
        assertEquals("10K", incomplete.get("CURRENT_FORMATTED"));
        assertEquals("----------------", incomplete.get("PROGRESS_BAR"));
        assertEquals("40,000", incomplete.get("REMAINING"));
        assertEquals("40K", incomplete.get("REMAINING_FORMATTED"));
    }

    @Test
    void calculatesProgressBarAndPercentage() {
        SellProgress.Threshold threshold = SellProgress.thresholds().getFirst();
        assertEquals("0.0", SellProgress.percentageText(0D, threshold));
        assertEquals("100.0", SellProgress.percentageText(25_000D, threshold));
        assertEquals(16, SellProgress.bar(25_000D, threshold, "x", "-").length());
        assertEquals(16, SellProgress.bar(0D, null, "x", "-").length());
        assertEquals(25_000D, SellProgress.nextTarget(0D));
        assertEquals(0D, SellProgress.remaining(Double.MAX_VALUE));
    }
}
