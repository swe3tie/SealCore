package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemPriceServiceTest {

    @Test
    void loadsPricesFromConfig() {
        ItemPriceService service = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.DIAMOND", 100);
        config.set("prices.EMERALD", 50.5);
        config.set("prices.UNKNOWN_ITEM_XYZ", 999);

        service.load(config.getConfigurationSection("prices"));

        assertEquals(100.0, service.getUnitPrice(Material.DIAMOND));
        assertEquals(50.5, service.getUnitPrice(Material.EMERALD));
        assertEquals(0.0, service.getUnitPrice(Material.DIRT));
    }

    @Test
    void matchesPotionSubtypeBeforeMaterialFallback() {
        ItemPriceService service = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.POTION", 15);
        config.set("prices.POTION:WATER", 3);
        service.load(config.getConfigurationSection("prices"));

        assertEquals(3.0, service.getUnitPrice(Material.POTION, "water", java.util.Map.of()));
        assertEquals(15.0, service.getUnitPrice(Material.POTION, null, java.util.Map.of()));
    }

    @Test
    void matchesEnchantedBookLevelsAndStackAmount() {
        ItemPriceService service = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.ENCHANTED_BOOK", 5);
        config.set("prices.ENCHANTED_BOOK:SHARPNESS_3", 60);
        service.load(config.getConfigurationSection("prices"));

        java.util.Map<String, Integer> enchantments = java.util.Map.of("sharpness", 3);
        assertEquals(60.0, service.getUnitPrice(Material.ENCHANTED_BOOK, null, enchantments));
        assertEquals(120.0, service.getItemPrice(Material.ENCHANTED_BOOK, 2, null, enchantments));
    }

    @Test
    void sumsConfiguredEnchantmentValues() {
        ItemPriceService service = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.ENCHANTED_BOOK:SHARPNESS_3", 60);
        config.set("prices.ENCHANTED_BOOK:UNBREAKING_2", 40);
        service.load(config.getConfigurationSection("prices"));

        assertEquals(100.0, service.getUnitPrice(Material.ENCHANTED_BOOK, null,
            java.util.Map.of("sharpness", 3, "unbreaking", 2)));
    }

    @Test
    void calculatesStackAmount() {
        ItemPriceService service = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.DIAMOND", 10);
        service.load(config.getConfigurationSection("prices"));

        assertEquals(20.0, service.getItemPrice(Material.DIAMOND, 2, null, java.util.Map.of()));
        assertEquals(0.0, service.getItemPrice(Material.DIRT, 1, null, java.util.Map.of()));
    }

}
