package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.SellHistoryEntry;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.FilterOption;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.SortOption;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellHistoryTest {

    @Test
    void entryFormatsDateAndDisplayName() {
        UUID playerId = UUID.randomUUID();
        // 2026-10-15 14:30:00 in system default zone
        ZonedDateTime time = ZonedDateTime.of(2026, 10, 15, 14, 30, 0, 0, ZoneId.systemDefault());
        long epochMillis = time.toInstant().toEpochMilli();

        SellHistoryEntry entry1 = new SellHistoryEntry(
            1L, playerId, Material.DIAMOND, null, SellCategory.ORES, 64, 1280.0, epochMillis
        );
        assertEquals("15/10/26—14:30", entry1.formattedDate());
        assertEquals("Diamond", entry1.displayName());

        SellHistoryEntry entry2 = new SellHistoryEntry(
            2L, playerId, Material.WHEAT, "<yellow>Golden Wheat", SellCategory.CROPS, 10, 50.0, epochMillis
        );
        assertEquals("<yellow>Golden Wheat", entry2.displayName());

        SellHistoryEntry entry3 = new SellHistoryEntry(
            3L, playerId, Material.POTION, null, "healing", SellCategory.POTIONS, 1, 20.0, epochMillis
        );
        assertEquals("Potion (Healing)", entry3.displayName());

        SellHistoryEntry entry4 = new SellHistoryEntry(
            4L, playerId, Material.TIPPED_ARROW, null, "poison", SellCategory.POTIONS, 16, 240.0, epochMillis
        );
        assertEquals("Tipped Arrow (Poison)", entry4.displayName());

        SellHistoryEntry entry5 = new SellHistoryEntry(
            5L, playerId, Material.SPLASH_POTION, "<green>Custom Potion", "swiftness", SellCategory.POTIONS, 1, 18.0, epochMillis
        );
        assertEquals("<green>Custom Potion", entry5.displayName());
    }

    @Test
    void loadsBundledSellHistoryConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/gui/sell-history.yml"))) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        SellHistoryGuiConfig guiConfig = SellHistoryGuiConfig.load(config);
        assertEquals("<dark_gray>ʟịᴄʜ ѕử ʙáɴ — {PAGE}/{MAX_PAGE}", guiConfig.title());
        assertEquals(6, guiConfig.rows());
        assertEquals(54, guiConfig.size());

        assertEquals(3, guiConfig.itemLore().size());
        assertTrue(guiConfig.itemLore().get(0).contains("{SELL_DATE}"));
        assertTrue(guiConfig.itemLore().get(1).contains("{AMOUNT}"));
        assertTrue(guiConfig.itemLore().get(2).contains("{TOTAL_PRICE_FORMATTED}"));

        assertNotNull(guiConfig.toolbar().previousButton());
        assertNotNull(guiConfig.toolbar().nextButton());
        assertNotNull(guiConfig.toolbar().sortButton());
        assertNotNull(guiConfig.toolbar().refreshButton());
        assertNotNull(guiConfig.toolbar().filterButton());
        assertEquals("<#9CC0D9>ʟịᴄʜ ѕử ʙáɴ", guiConfig.toolbar().refreshButton().name());
    }

    @Test
    void sortOptionsOrderEntriesCorrectly() {
        List<SortOption<SellHistoryEntry>> options = SellHistoryGui.buildSortOptions();
        assertEquals(4, options.size());

        SortOption<SellHistoryEntry> dateDesc = options.get(0);
        SortOption<SellHistoryEntry> dateAsc = options.get(1);
        SortOption<SellHistoryEntry> priceDesc = options.get(2);
        SortOption<SellHistoryEntry> priceAsc = options.get(3);

        assertEquals("date_desc", dateDesc.id());
        assertEquals("Gần nhất", dateDesc.displayName());

        assertEquals("date_asc", dateAsc.id());
        assertEquals("Lâu nhất", dateAsc.displayName());

        assertEquals("price_desc", priceDesc.id());
        assertEquals("Giá cao nhất", priceDesc.displayName());

        assertEquals("price_asc", priceAsc.id());
        assertEquals("Giá thấp nhất", priceAsc.displayName());

        UUID player = UUID.randomUUID();
        SellHistoryEntry cheapOld = new SellHistoryEntry(1L, player, Material.DIRT, null, SellCategory.BLOCKS, 10, 10.0, 1000L);
        SellHistoryEntry expensiveNew = new SellHistoryEntry(2L, player, Material.DIAMOND, null, SellCategory.ORES, 1, 1000.0, 5000L);

        List<SellHistoryEntry> list = new ArrayList<>(List.of(cheapOld, expensiveNew));

        list.sort(priceDesc.comparator());
        assertEquals(expensiveNew, list.get(0));
        assertEquals(cheapOld, list.get(1));

        list.sort(priceAsc.comparator());
        assertEquals(cheapOld, list.get(0));
        assertEquals(expensiveNew, list.get(1));

        list.sort(dateDesc.comparator());
        assertEquals(expensiveNew, list.get(0));
        assertEquals(cheapOld, list.get(1));

        list.sort(dateAsc.comparator());
        assertEquals(cheapOld, list.get(0));
        assertEquals(expensiveNew, list.get(1));
    }

    @Test
    void filterOptionsFilterCorrectly() {
        List<FilterOption<SellHistoryEntry>> filters = SellHistoryGui.buildFilterOptions();
        assertEquals(10, filters.size());

        FilterOption<SellHistoryEntry> all = filters.get(0);
        assertEquals("all", all.id());
        assertEquals("Tất cả", all.displayName());

        FilterOption<SellHistoryEntry> crops = filters.get(1);
        assertEquals("crops", crops.id());
        assertEquals("Cây trồng", crops.displayName());

        UUID player = UUID.randomUUID();
        SellHistoryEntry wheat = new SellHistoryEntry(1L, player, Material.WHEAT, null, SellCategory.CROPS, 10, 5.0, 1000L);
        SellHistoryEntry diamond = new SellHistoryEntry(2L, player, Material.DIAMOND, null, SellCategory.ORES, 1, 100.0, 1000L);

        assertTrue(all.predicate().test(wheat));
        assertTrue(all.predicate().test(diamond));

        assertTrue(crops.predicate().test(wheat));
        assertFalse(crops.predicate().test(diamond));
    }
}
