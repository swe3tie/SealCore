package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedToolbarConfig;

/**
 * Global and per-GUI pagination settings for economy menus (/worth, /sell multiplier).
 */
public record PaginationConfig(
    boolean enabled,
    boolean worthEnabled,
    boolean sellMultiplierEnabled,
    @Nullable PagedToolbarConfig.ItemDefinition previousItem,
    @Nullable PagedToolbarConfig.ItemDefinition nextItem
) {

    public static final PaginationConfig DEFAULT = new PaginationConfig(true, true, true, null, null);

    @NotNull
    public static PaginationConfig load(@Nullable ConfigurationSection section) {
        if (section == null) return DEFAULT;

        boolean enabled = section.getBoolean("enabled", true);
        boolean worth = section.getBoolean("worth", enabled);
        boolean multiplier = section.getBoolean("sell-multiplier", enabled);

        PagedToolbarConfig.ItemDefinition previous = null;
        if (section.isConfigurationSection("previous")) {
            previous = PagedToolbarConfig.readItem(section.getConfigurationSection("previous"), null);
        }

        PagedToolbarConfig.ItemDefinition next = null;
        if (section.isConfigurationSection("next")) {
            next = PagedToolbarConfig.readItem(section.getConfigurationSection("next"), null);
        }

        return new PaginationConfig(enabled, worth, multiplier, previous, next);
    }
}
