package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedToolbarConfig;

import java.util.List;

/** Configuration for the {@code /sell history} GUI. */
public record SellHistoryGuiConfig(
    @NotNull String title,
    int rows,
    @NotNull List<String> itemLore,
    @NotNull PagedToolbarConfig toolbar,
    boolean paginationEnabled
) {

    public static final String DEFAULT_TITLE = "<dark_gray>ʟịᴄʜ ѕử ʙáɴ — {PAGE}/{MAX_PAGE}";
    public static final List<String> DEFAULT_LORE = List.of(
        "<white>Thời gian: <#9CC0D9>{SELL_DATE}</#9CC0D9>",
        "<white>Số lượng: <#9CC0D9>{AMOUNT}</#9CC0D9>",
        "<white>Tổng: <green>${TOTAL_PRICE_FORMATTED}</green>"
    );

    @NotNull
    public static SellHistoryGuiConfig load(@Nullable YamlConfiguration config) {
        return load(config, null);
    }

    @NotNull
    public static SellHistoryGuiConfig load(@Nullable YamlConfiguration config, @Nullable PaginationConfig paginationConfig) {
        if (config == null) {
            return new SellHistoryGuiConfig(DEFAULT_TITLE, 6, DEFAULT_LORE, PagedToolbarConfig.load(null, paginationConfig), true);
        }

        String title = config.getString("title", DEFAULT_TITLE);
        int rows = config.getInt("rows", 6);
        List<String> lore = config.contains("item-lore") ? config.getStringList("item-lore") : DEFAULT_LORE;
        PagedToolbarConfig toolbar = PagedToolbarConfig.load(config.getConfigurationSection("toolbar"), paginationConfig);

        boolean filePagination = true;
        if (config.isBoolean("pagination")) {
            filePagination = config.getBoolean("pagination");
        } else if (config.isConfigurationSection("pagination")) {
            filePagination = config.getBoolean("pagination.enabled", true);
        }
        boolean paginationEnabled = filePagination && (paginationConfig == null || paginationConfig.worthEnabled());

        return new SellHistoryGuiConfig(title, rows, List.copyOf(lore), toolbar, paginationEnabled);
    }

    public int size() {
        return this.rows * 9;
    }
}
