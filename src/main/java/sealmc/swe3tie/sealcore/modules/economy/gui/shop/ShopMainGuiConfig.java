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
 * Configuration for the main shop menu loaded from {@code shop-main.yml}.
 */
public record ShopMainGuiConfig(
    @NotNull String title,
    int rows,
    @NotNull Map<String, GuiConfigHelper.ItemDefinition> categories
) {

    public ShopMainGuiConfig {
        categories = Collections.unmodifiableMap(categories);
    }

    public int size() {
        return this.rows * 9;
    }

    @NotNull
    public static ShopMainGuiConfig load(@Nullable YamlConfiguration config) {
        return load(config, Map.of());
    }

    @NotNull
    public static ShopMainGuiConfig load(
        @Nullable YamlConfiguration config,
        @NotNull Map<String, GuiConfigHelper.ItemDefinition> categoryItems
    ) {
        String title = GuiConfigHelper.readTitle(config, "<dark_gray>ᴄửᴀ ʜàɴɢ");
        int rows = GuiConfigHelper.readRows(config, 3);

        Map<String, GuiConfigHelper.ItemDefinition> categories = new LinkedHashMap<>(categoryItems);
        ConfigurationSection catSec = config != null && config.isConfigurationSection("categories")
            ? config.getConfigurationSection("categories")
            : config;

        if (catSec != null) {
            for (String key : catSec.getKeys(false)) {
                if (key.equals("title") || key.equals("rows") || key.equals("config-version")) {
                    continue;
                }
                if (!catSec.isConfigurationSection(key)) continue;
                ConfigurationSection sec = catSec.getConfigurationSection(key);
                GuiConfigHelper.ItemDefinition item = GuiConfigHelper.readItemDefinition(
                    sec, 0, Material.CHEST, key, List.of("<white>Nhấp để mở cửa hàng {SECTION}")
                );
                categories.put(key, item);
            }
        }

        return new ShopMainGuiConfig(title, rows, categories);
    }
}
