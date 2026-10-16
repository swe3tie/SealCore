package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedToolbarConfig;

import java.util.List;

/** Configuration for the {@code /worth} lookup GUI. */
public record WorthGuiConfig(
    @NotNull String title,
    int rows,
    @NotNull List<String> itemLore,
    @NotNull PagedToolbarConfig toolbar,
    boolean paginationEnabled
) {

    public WorthGuiConfig(
        @NotNull String title,
        int rows,
        @NotNull List<String> itemLore,
        @NotNull PagedToolbarConfig toolbar
    ) {
        this(title, rows, itemLore, toolbar, true);
    }

    public static final String DEFAULT_TITLE = "ɢɪá ᴛʀị ᴠậᴛ ᴘʜẩᴍ — {PAGE}/{MAX_PAGE}";
    public static final List<String> DEFAULT_LORE = List.of(
        "<white>Giá trị: <#9CC0D9>{WORTH_FORMATTED}",
        "<white>Hệ số: <#9CC0D9>{SECTION_MULTIPLIER}x",
        "<white>Phân loại: <#9CC0D9>{SECTION}"
    );

    @NotNull
    public static WorthGuiConfig load(@Nullable YamlConfiguration config) {
        return load(config, null);
    }

    @NotNull
    public static WorthGuiConfig load(@Nullable YamlConfiguration config, @Nullable PaginationConfig paginationConfig) {
        if (config == null) {
            boolean enabled = paginationConfig == null || paginationConfig.worthEnabled();
            return new WorthGuiConfig(DEFAULT_TITLE, 6, DEFAULT_LORE, PagedToolbarConfig.load(null, paginationConfig), enabled);
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

        return new WorthGuiConfig(title, rows, List.copyOf(lore), toolbar, paginationEnabled);
    }

    public int size() {
        return this.rows * 9;
    }
}
