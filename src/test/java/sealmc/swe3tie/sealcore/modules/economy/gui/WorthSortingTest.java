package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.WorthItem;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.FilterOption;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.SortOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorthSortingTest {

    private static WorthItem item(String id, Material mat, double price, SellCategory cat, String name) {
        return new WorthItem(id, mat, null, Map.of(), price, cat, name);
    }

    @Test
    void verifiesFourSortOptions() {
        List<SortOption<WorthItem>> options = WorthGui.buildSortOptions();
        assertEquals(4, options.size());

        WorthItem apple = item("apple", Material.APPLE, 10.0, SellCategory.CROPS, "Apple");
        WorthItem diamond = item("diamond", Material.DIAMOND, 100.0, SellCategory.ORES, "Diamond");
        WorthItem carrot = item("carrot", Material.CARROT, 5.0, SellCategory.CROPS, "Carrot");

        List<WorthItem> items = new ArrayList<>(List.of(apple, diamond, carrot));

        // 1. Price descending
        SortOption<WorthItem> priceDesc = options.stream().filter(o -> o.id().equals("price_desc")).findFirst().orElseThrow();
        items.sort(priceDesc.comparator());
        assertEquals(List.of(diamond, apple, carrot), items);

        // 2. Price ascending
        SortOption<WorthItem> priceAsc = options.stream().filter(o -> o.id().equals("price_asc")).findFirst().orElseThrow();
        items.sort(priceAsc.comparator());
        assertEquals(List.of(carrot, apple, diamond), items);

        // 3. Name A-Z
        SortOption<WorthItem> nameAsc = options.stream().filter(o -> o.id().equals("name_asc")).findFirst().orElseThrow();
        items.sort(nameAsc.comparator());
        assertEquals(List.of(apple, carrot, diamond), items);

        // 4. Name Z-A
        SortOption<WorthItem> nameDesc = options.stream().filter(o -> o.id().equals("name_desc")).findFirst().orElseThrow();
        items.sort(nameDesc.comparator());
        assertEquals(List.of(diamond, carrot, apple), items);
    }

    @Test
    void verifiesTenFilterOptions() {
        List<FilterOption<WorthItem>> options = WorthGui.buildFilterOptions();
        assertEquals(10, options.size());

        List<WorthItem> items = List.of(
            item("wheat", Material.WHEAT, 5.0, SellCategory.CROPS, "Wheat"),
            item("diamond", Material.DIAMOND, 100.0, SellCategory.ORES, "Diamond"),
            item("flesh", Material.ROTTEN_FLESH, 2.0, SellCategory.MOBS, "Rotten Flesh"),
            item("log", Material.OAK_LOG, 10.0, SellCategory.NATURAL, "Oak Log"),
            item("sword", Material.IRON_SWORD, 50.0, SellCategory.TOOLS, "Iron Sword"),
            item("cod", Material.COD, 8.0, SellCategory.FISH, "Cod"),
            item("book", Material.BOOK, 15.0, SellCategory.BOOKS, "Book"),
            item("potion", Material.POTION, 20.0, SellCategory.POTIONS, "Potion"),
            item("stone", Material.STONE, 1.0, SellCategory.BLOCKS, "Stone"),
            item("barrier", Material.BARRIER, 0.0, null, "Barrier")
        );

        for (FilterOption<WorthItem> filter : options) {
            List<WorthItem> filtered = items.stream().filter(filter.predicate()).toList();
            switch (filter.id()) {
                case "all" -> assertEquals(10, filtered.size());
                case "crops" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("wheat", filtered.getFirst().id());
                }
                case "ores" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("diamond", filtered.getFirst().id());
                }
                case "mobs" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("flesh", filtered.getFirst().id());
                }
                case "natural" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("log", filtered.getFirst().id());
                }
                case "tools" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("sword", filtered.getFirst().id());
                }
                case "fish" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("cod", filtered.getFirst().id());
                }
                case "books" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("book", filtered.getFirst().id());
                }
                case "potions" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("potion", filtered.getFirst().id());
                }
                case "blocks" -> {
                    assertEquals(1, filtered.size());
                    assertEquals("stone", filtered.getFirst().id());
                }
                default -> throw new AssertionError("Unexpected filter: " + filter.id());
            }
        }
    }
}
