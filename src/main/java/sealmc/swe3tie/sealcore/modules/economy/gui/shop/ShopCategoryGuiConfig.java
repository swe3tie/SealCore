package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for category shop menus loaded from {@code shop/<id>.yml}.
 */
public record ShopCategoryGuiConfig(
    @NotNull Map<String, CategoryData> categories
) {

    public ShopCategoryGuiConfig {
        categories = Collections.unmodifiableMap(categories);
    }

    public record CategoryData(
        @NotNull String id,
        @NotNull String title,
        int rows,
        @NotNull String currency,
        @NotNull List<String> defaultLore,
        @Nullable GuiConfigHelper.ItemDefinition sectionItem,
        @NotNull Map<Integer, ItemData> items
    ) {
        public CategoryData {
            defaultLore = List.copyOf(defaultLore);
            items = Collections.unmodifiableMap(items);
        }

        public int size() {
            return this.rows * 9;
        }
    }

    public record ItemData(
        @NotNull String id,
        int slot,
        @NotNull GuiConfigHelper.ItemDefinition definition,
        double price,
        @NotNull String currency,
        @Nullable String rewardCurrency,
        boolean spawner
    ) {
    }

    @NotNull
    public static CategoryData loadCategory(
        @NotNull String id,
        @Nullable YamlConfiguration config
    ) {
        String title = GuiConfigHelper.readTitle(config, "<dark_gray>ᴄửᴀ ʜàɴɢ {SECTION}");
        int rows = GuiConfigHelper.readRows(config, 3);
        String currency = config != null ? config.getString("currency", "money") : "money";
        List<String> defaultLore = config != null && config.contains("lore")
            ? config.getStringList("lore")
            : List.of("{CURRENCY_PREFIX}{PRICE_FORMATTED}{CURRENCY_SUFFIX}");

        GuiConfigHelper.ItemDefinition sectionItem = null;
        if (config != null && config.isConfigurationSection("section")) {
            sectionItem = GuiConfigHelper.readItemDefinition(
                config.getConfigurationSection("section"), 0, Material.CHEST, id, List.of("<white>Nhấp để mở cửa hàng {SECTION}")
            );
        }

        Map<Integer, ItemData> items = new LinkedHashMap<>();
        ConfigurationSection itemsSec = config != null ? config.getConfigurationSection("items") : null;
        if (itemsSec != null) {
            for (String itemId : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemId);
                if (itemSec == null) continue;

                GuiConfigHelper.ItemDefinition def = GuiConfigHelper.readItemDefinition(
                    itemSec, 0, Material.STONE, "", List.of()
                );
                double price = itemSec.getDouble("price", 0D);
                String itemCurrency = itemSec.getString("currency", currency);
                String rewardCurrency = itemSec.getString("reward-currency", null);
                boolean isSpawner = itemSec.getBoolean("spawner", false);

                ItemData data = new ItemData(itemId, def.slot(), def, price, itemCurrency, rewardCurrency, isSpawner);
                items.put(def.slot(), data);
            }
        }

        return new CategoryData(id, title, rows, currency, defaultLore, sectionItem, items);
    }

    @NotNull
    public static ShopCategoryGuiConfig loadFromMap(@NotNull Map<String, YamlConfiguration> configs) {
        Map<String, CategoryData> categories = new LinkedHashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : configs.entrySet()) {
            categories.put(entry.getKey(), loadCategory(entry.getKey(), entry.getValue()));
        }
        return new ShopCategoryGuiConfig(categories);
    }

    @NotNull
    public static ShopCategoryGuiConfig load(
        @Nullable YamlConfiguration config,
        @NotNull Map<String, Double> prices
    ) {
        Map<String, CategoryData> categories = new LinkedHashMap<>();
        if (config == null) return new ShopCategoryGuiConfig(categories);

        String globalTitle = GuiConfigHelper.readTitle(config, "<dark_gray>ᴄửᴀ ʜàɴɢ {SECTION}");
        int globalRows = GuiConfigHelper.readRows(config, 3);
        List<String> globalLore = config.contains("lore")
            ? config.getStringList("lore")
            : List.of("{CURRENCY_PREFIX}{PRICE_FORMATTED}{CURRENCY_SUFFIX}");

        for (String catId : config.getKeys(false)) {
            if (catId.equals("title") || catId.equals("rows") || catId.equals("lore")
                || catId.equals("default-lore") || catId.equals("config-version")) {
                continue;
            }
            if (!config.isConfigurationSection(catId)) continue;
            ConfigurationSection sec = config.getConfigurationSection(catId);
            if (sec == null) continue;

            String title = sec.getString("title", globalTitle);
            int rows = sec.getInt("rows", globalRows);
            String currency = sec.getString("currency", "money");
            List<String> defaultLore = sec.contains("lore") ? sec.getStringList("lore") : globalLore;
            GuiConfigHelper.ItemDefinition sectionItem = sec.isConfigurationSection("section")
                ? GuiConfigHelper.readItemDefinition(sec.getConfigurationSection("section"), 0, Material.CHEST, catId, List.of())
                : null;

            Map<Integer, ItemData> items = new LinkedHashMap<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String itemId : itemsSec.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemId);
                    if (itemSec == null) continue;

                    GuiConfigHelper.ItemDefinition def = GuiConfigHelper.readItemDefinition(
                        itemSec, 0, Material.STONE, "", List.of()
                    );
                    double price = itemSec.contains("price") ? itemSec.getDouble("price") : prices.getOrDefault(itemId, 0D);
                    String itemCurrency = itemSec.getString("currency", currency);
                    String rewardCurrency = itemSec.getString("reward-currency", null);
                    boolean isSpawner = itemSec.getBoolean("spawner", false);

                    items.put(def.slot(), new ItemData(itemId, def.slot(), def, price, itemCurrency, rewardCurrency, isSpawner));
                }
            }
            categories.put(catId, new CategoryData(catId, title, rows, currency, defaultLore, sectionItem, items));
        }

        return new ShopCategoryGuiConfig(categories);
    }
}
