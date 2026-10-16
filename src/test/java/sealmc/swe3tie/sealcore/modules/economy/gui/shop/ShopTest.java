package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShopTest {

    @Test
    void currencyPrefixAndSuffixFormattedCorrectly() {
        assertEquals("<green>$", GuiConfigHelper.getCurrencyPrefix("money"));
        assertEquals("", GuiConfigHelper.getCurrencySuffix("money"));

        assertEquals("<dark_purple>", GuiConfigHelper.getCurrencyPrefix("shards"));
        assertEquals(" ⭐", GuiConfigHelper.getCurrencySuffix("shards"));

        assertEquals("<green>$100", GuiConfigHelper.formatPrice("money", 100));
        assertEquals("<dark_purple>200 ⭐", GuiConfigHelper.formatPrice("shards", 200));
    }

    @Test
    void pricePlaceholdersContainPrefixAndSuffix() {
        Map<String, String> placeholders = GuiConfigHelper.formatPricePlaceholders("shards", 500);
        assertEquals("<dark_purple>", placeholders.get("CURRENCY_PREFIX"));
        assertEquals(" ⭐", placeholders.get("CURRENCY_SUFFIX"));
        assertEquals("500", placeholders.get("PRICE_FORMATTED"));

        String template = "{CURRENCY_PREFIX}{PRICE_FORMATTED}{CURRENCY_SUFFIX}";
        String replaced = GuiConfigHelper.replacePlaceholders(template, placeholders);
        assertEquals("<dark_purple>500 ⭐", replaced);

        String accentTemplate = "<white>{AMOUNT} <accent>{BUYING_ITEM}</accent>";
        String accentReplaced = GuiConfigHelper.replacePlaceholders(accentTemplate, Map.of("AMOUNT", "1", "BUYING_ITEM", "Ender Pearl"));
        assertEquals("<white>1 <#9CC0D9>Ender Pearl</#9CC0D9>", accentReplaced);
    }

    @Test
    void stripTagsRemovesMiniMessageTags() {
        assertEquals("ᴇɴᴅ", GuiConfigHelper.stripTags("<#FFFFF6>ᴇɴᴅ"));
        assertEquals("ѕʜᴀʀᴅѕ", GuiConfigHelper.stripTags("<dark_purple>ѕʜᴀʀᴅѕ"));
        assertEquals("Plain Text", GuiConfigHelper.stripTags("Plain Text"));
    }

    @Test
    void parseMaterialHandlesNames() {
        assertNotNull(GuiConfigHelper.parseMaterial("STONE"));
        assertNotNull(GuiConfigHelper.parseMaterial("minecraft:ender_pearl"));
        assertNotNull(GuiConfigHelper.parseMaterial("totem_of_undying"));
        assertNull(GuiConfigHelper.parseMaterial("invalid_material_xyz"));
        assertNull(GuiConfigHelper.parseMaterial(null));
    }

    @Test
    void buttonVisibilityRules() {
        int maxStack = 64;
        int current = 1;

        // At 1: sub buttons hidden, add buttons visible
        boolean canSub1 = maxStack > 1 && current - 1 >= 1;
        boolean canSub10 = maxStack > 1 && current - 10 >= 1;
        boolean canReset = maxStack > 1 && current > 1;
        boolean canAdd1 = maxStack > 1 && current + 1 <= maxStack;
        boolean canAdd10 = maxStack > 1 && current + 10 <= maxStack;
        boolean canMax = maxStack > 1 && current < maxStack;

        assertEquals(false, canSub1);
        assertEquals(false, canSub10);
        assertEquals(false, canReset);
        assertEquals(true, canAdd1);
        assertEquals(true, canAdd10);
        assertEquals(true, canMax);

        // At 64: add buttons hidden, sub buttons visible
        current = 64;
        canSub1 = maxStack > 1 && current - 1 >= 1;
        canSub10 = maxStack > 1 && current - 10 >= 1;
        canReset = maxStack > 1 && current > 1;
        canAdd1 = maxStack > 1 && current + 1 <= maxStack;
        canAdd10 = maxStack > 1 && current + 10 <= maxStack;
        canMax = maxStack > 1 && current < maxStack;

        assertEquals(true, canSub1);
        assertEquals(true, canSub10);
        assertEquals(true, canReset);
        assertEquals(false, canAdd1);
        assertEquals(false, canAdd10);
        assertEquals(false, canMax);

        // For unstackable items (maxStack == 1, e.g. Totem): all hidden
        maxStack = 1;
        current = 1;
        boolean anyAdjustment = maxStack > 1;
        assertEquals(false, anyAdjustment);
    }

    @Test
    void loadsBundledShopAmountConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/gui/shop-amount.yml"))) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        ShopAmountGuiConfig cfg = ShopAmountGuiConfig.load(config);
        assertEquals("<dark_gray>ᴄʜọɴ ѕố ʟượɴɢ", cfg.title());
        assertEquals(3, cfg.rows());
        assertEquals(27, cfg.size());
        assertEquals(13, cfg.itemSlot()); // 1-based 14 -> index 13
        assertEquals(9, cfg.resetSlot());  // 1-based 10 -> index 9
        assertEquals(10, cfg.sub10Slot());
        assertEquals(11, cfg.sub1Slot());
        assertEquals(15, cfg.add1Slot());
        assertEquals(16, cfg.add10Slot());
        assertEquals(17, cfg.maxSlot());
        assertEquals(21, cfg.cancelSlot());
        assertEquals(23, cfg.confirmSlot()); // 1-based 24 -> index 23
        assertEquals(
            List.of("<white>{AMOUNT} <accent>{BUYING_ITEM} <white>với giá {CURRENCY_PREFIX}{PRICE_FORMATTED}{CURRENCY_SUFFIX}"),
            cfg.confirmBtn().lore()
        );
    }

    @Test
    void loadsBundledShopMainConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/gui/shop-main.yml"))) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        YamlConfiguration endConfig = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/shop/end.yml"))) {
            endConfig.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        ShopCategoryGuiConfig.CategoryData endCat = ShopCategoryGuiConfig.loadCategory("end", endConfig);

        ShopMainGuiConfig cfg = ShopMainGuiConfig.load(config, Map.of("end", endCat.sectionItem()));
        assertEquals("<dark_gray>ᴄửᴀ ʜàɴɢ", cfg.title());
        assertEquals(3, cfg.rows());
        assertEquals(1, cfg.categories().size());
        assertNotNull(cfg.categories().get("end"));
        assertEquals(11, cfg.categories().get("end").slot()); // 1-based 12 -> 11
    }

    @Test
    void loadsBundledShopEndCategoryConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/shop/end.yml"))) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        ShopCategoryGuiConfig.CategoryData endCat = ShopCategoryGuiConfig.loadCategory("end", config);
        assertEquals(3, endCat.rows());
        assertEquals("money", endCat.currency());
        assertEquals(9, endCat.items().size());
        assertNotNull(endCat.sectionItem());
        assertEquals(11, endCat.sectionItem().slot()); // 1-based 12 -> 11

        ShopCategoryGuiConfig.ItemData enderChest = endCat.items().get(9); // 1-based 10 -> 9
        assertNotNull(enderChest);
        assertEquals("ender_chest", enderChest.id());
        assertEquals(2500.0, enderChest.price());
    }

    @Test
    void loadsBundledShopShardsCategoryConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/shop/shards.yml"))) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        ShopCategoryGuiConfig.CategoryData shardsCat = ShopCategoryGuiConfig.loadCategory("shards", config);
        assertEquals("shards", shardsCat.currency());

        // Key items
        ShopCategoryGuiConfig.ItemData goldKey = shardsCat.items().get(3); // 1-based 4 -> index 3
        assertNotNull(goldKey);
        assertEquals("gold_key", goldKey.id());
        assertEquals(Material.TRIAL_KEY, goldKey.definition().material());

        ShopCategoryGuiConfig.ItemData crimsonKey = shardsCat.items().get(5); // 1-based 6 -> index 5
        assertNotNull(crimsonKey);
        assertEquals("crimson_key", crimsonKey.id());
        assertEquals(Material.OMINOUS_TRIAL_KEY, crimsonKey.definition().material());

        // Spawners shifted by 1 to start from slot 11 (index 10)
        ShopCategoryGuiConfig.ItemData pigSpawner = shardsCat.items().get(10); // 1-based 11 -> index 10
        assertNotNull(pigSpawner);
        assertEquals("pig_spawner", pigSpawner.id());
        assertEquals(true, pigSpawner.spawner());
    }
}
