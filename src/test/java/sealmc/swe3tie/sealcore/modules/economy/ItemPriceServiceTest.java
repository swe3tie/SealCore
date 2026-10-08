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
}
