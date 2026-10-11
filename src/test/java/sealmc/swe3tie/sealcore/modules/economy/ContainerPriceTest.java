package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.modules.economy.util.ContainerHelper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerPriceTest {

    private ItemPriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new ItemPriceService();
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.ores.DIAMOND", 100.0);
        config.set("prices.ores.EMERALD", 50.0);
        config.set("prices.blocks.SHULKER_BOX", 25.0);
        config.set("prices.tools.BUNDLE", 15.0);
        priceService.load(config.getConfigurationSection("prices"));
    }

    @Test
    void detectsContainersCorrectly() {
        assertTrue(ContainerHelper.isShulkerBox(Material.SHULKER_BOX));
        assertTrue(ContainerHelper.isShulkerBox(Material.RED_SHULKER_BOX));
        assertFalse(ContainerHelper.isShulkerBox(Material.BUNDLE));
        assertFalse(ContainerHelper.isShulkerBox(Material.DIAMOND));
        assertFalse(ContainerHelper.isShulkerBox(Material.AIR));
        assertFalse(ContainerHelper.isShulkerBox((Material) null));

        assertTrue(ContainerHelper.isBundle(Material.BUNDLE));
        assertFalse(ContainerHelper.isBundle(Material.SHULKER_BOX));
        assertFalse(ContainerHelper.isBundle(Material.DIAMOND));
        assertFalse(ContainerHelper.isBundle((Material) null));

        assertTrue(ContainerHelper.isContainer(Material.SHULKER_BOX));
        assertTrue(ContainerHelper.isContainer(Material.BUNDLE));
        assertFalse(ContainerHelper.isContainer(Material.DIAMOND));
        assertFalse(ContainerHelper.isContainer((Material) null));
    }

    @Test
    void calculatesPureContainerWorth() {
        ItemPriceService.TestItem diamonds = new ItemPriceService.TestItem(Material.DIAMOND, 3);
        ItemPriceService.TestItem emerald = new ItemPriceService.TestItem(Material.EMERALD, 1);
        ItemPriceService.TestItem dirt = new ItemPriceService.TestItem(Material.DIRT, 64);

        assertEquals(300.0, priceService.getItemWorth(Material.DIAMOND, 3, null, Map.of(), List.of()));
        assertEquals(50.0, priceService.getItemWorth(Material.EMERALD, 1, null, Map.of(), List.of()));
        assertEquals(0.0, priceService.getItemWorth(Material.DIRT, 64, null, Map.of(), List.of()));

        // Container containing diamonds and emerald
        ItemPriceService.TestItem shulker = new ItemPriceService.TestItem(
            Material.SHULKER_BOX, 1, List.of(diamonds, emerald, dirt)
        );

        double total = priceService.getItemWorth(shulker.material(), shulker.amount(), null, Map.of(), shulker.containerContents());
        assertEquals(350.0, total);
    }

    @Test
    void calculatesNestedContainerWorth() {
        ItemPriceService.TestItem diamonds = new ItemPriceService.TestItem(Material.DIAMOND, 2); // 200.0
        ItemPriceService.TestItem bundle = new ItemPriceService.TestItem(Material.BUNDLE, 1, List.of(diamonds)); // 200.0
        ItemPriceService.TestItem emerald = new ItemPriceService.TestItem(Material.EMERALD, 1); // 50.0

        ItemPriceService.TestItem shulker = new ItemPriceService.TestItem(
            Material.SHULKER_BOX, 1, List.of(bundle, emerald)
        );

        double total = priceService.getItemWorth(shulker.material(), shulker.amount(), null, Map.of(), shulker.containerContents());
        assertEquals(250.0, total);
    }

    @Test
    void emptyPricedContainerHasBaseWorth() {
        assertEquals(25.0, priceService.getItemWorth(Material.SHULKER_BOX, 1, null, Map.of(), List.of()));
        assertEquals(15.0, priceService.getItemWorth(Material.BUNDLE, 1, null, Map.of(), List.of()));
        assertEquals(0.0, priceService.getItemWorth(Material.CYAN_SHULKER_BOX, 1, null, Map.of(), List.of()));
    }

    @Test
    void pureContainerItemExtractionAndCategories() {
        // Test priceService category mapping for materials
        assertEquals(SellCategory.ORES, priceService.category(Material.DIAMOND, null, Map.of()));
        assertEquals(SellCategory.BLOCKS, priceService.category(Material.SHULKER_BOX, null, Map.of()));
        assertNull(priceService.category(Material.DIRT, null, Map.of()));
        assertNull(priceService.category(Material.CYAN_SHULKER_BOX, null, Map.of()));

        // Verify sellable prices
        assertEquals(200.0, priceService.getItemPrice(Material.DIAMOND, 2, null, Map.of()));
        assertEquals(0.0, priceService.getItemPrice(Material.DIRT, 10, null, Map.of()));
        assertEquals(25.0, priceService.getItemPrice(Material.SHULKER_BOX, 1, null, Map.of()));
    }
}
