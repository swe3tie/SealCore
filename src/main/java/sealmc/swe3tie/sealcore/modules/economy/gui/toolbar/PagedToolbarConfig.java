package sealmc.swe3tie.sealcore.modules.economy.gui.toolbar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.lang.LangService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Configuration for navigation, sorting, filtering, and refresh toolbar buttons. */
public record PagedToolbarConfig(
    @NotNull ItemDefinition previousButton,
    @NotNull ItemDefinition nextButton,
    @NotNull ItemDefinition sortButton,
    @NotNull ItemDefinition refreshButton,
    @NotNull ItemDefinition filterButton
) {

    public static final PagedToolbarConfig DEFAULT = builder().build();

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Loads toolbar configuration from YAML with sensible defaults. */
    @NotNull
    public static PagedToolbarConfig load(@Nullable ConfigurationSection section) {
        return load(section, null);
    }

    /** Loads toolbar configuration with optional pagination defaults. */
    @NotNull
    public static PagedToolbarConfig load(@Nullable ConfigurationSection section, @Nullable sealmc.swe3tie.sealcore.modules.economy.gui.PaginationConfig pagination) {
        ItemDefinition defaultPrev = pagination != null && pagination.previousItem() != null
            ? pagination.previousItem()
            : new ItemDefinition("previous", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ᴛʀướᴄ", List.of("<white>Nhấp để quay về trang trước"));
        ItemDefinition defaultNext = pagination != null && pagination.nextItem() != null
            ? pagination.nextItem()
            : new ItemDefinition("next", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ѕᴀᴜ", List.of("<white>Nhấp để sang trang sau"));

        if (section == null) {
            return new PagedToolbarConfig(
                defaultPrev,
                defaultNext,
                new ItemDefinition("sort", Material.CAULDRON, "<#9CC0D9>ѕắᴘ хếᴘ", List.of()),
                new ItemDefinition("refresh", Material.ANVIL, "<#9CC0D9>ɢɪá ᴛʀị ᴠậᴛ ᴘʜẩᴍ", List.of("<white>Nhấp để làm mới")),
                new ItemDefinition("filter", Material.HOPPER, "<#9CC0D9>ʟọᴄ", List.of())
            );
        }

        ItemDefinition previous = readItem(section.getConfigurationSection("previous"), defaultPrev);
        ItemDefinition next = readItem(section.getConfigurationSection("next"), defaultNext);
        ItemDefinition sort = readItem(section.getConfigurationSection("sort"),
            new ItemDefinition("sort", Material.CAULDRON, "<#9CC0D9>ѕắᴘ хếᴘ", List.of()));
        ItemDefinition refresh = readItem(section.getConfigurationSection("refresh"),
            new ItemDefinition("refresh", Material.ANVIL, "<#9CC0D9>ɢɪá ᴛʀị ᴠậᴛ ᴘʜẩᴍ", List.of("<white>Nhấp để làm mới")));
        ItemDefinition filter = readItem(section.getConfigurationSection("filter"),
            new ItemDefinition("filter", Material.HOPPER, "<#9CC0D9>ʟọᴄ", List.of()));

        return new PagedToolbarConfig(previous, next, sort, refresh, filter);
    }

    @Nullable
    public static ItemDefinition readItem(@Nullable ConfigurationSection section, @Nullable ItemDefinition fallback) {
        if (section == null) return fallback;
        Material material = parseMaterial(section.getString("material"));
        if (material == null && fallback != null) material = fallback.material();
        if (material == null) material = Material.ARROW;
        String name = section.getString("name", fallback != null ? fallback.name() : "");
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : (fallback != null ? fallback.lore() : List.of());
        String id = fallback != null ? fallback.id() : section.getName();
        return new ItemDefinition(id, material, name, List.copyOf(lore));
    }

    private static Material parseMaterial(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Material.matchMaterial(name);
        }
    }

    public record ItemDefinition(
        @NotNull String id,
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore
    ) {
        public ItemDefinition {
            lore = List.copyOf(lore);
        }

        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang) {
            return this.createItem(player, lang, Map.of(), List.of());
        }

        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang, @NotNull List<String> dynamicLore) {
            return this.createItem(player, lang, Map.of(), dynamicLore);
        }

        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang,
                                    @NotNull Map<String, String> placeholders, @NotNull List<String> extraLore) {
            ItemStack item = new ItemStack(this.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (!this.name.isEmpty()) {
                    String renderedName = replace(this.name, placeholders);
                    meta.displayName(lang.parse(renderedName, player).decoration(TextDecoration.ITALIC, false));
                }
                List<String> combinedLore = new ArrayList<>(this.lore);
                combinedLore.addAll(extraLore);
                if (!combinedLore.isEmpty()) {
                    List<Component> renderedLore = new ArrayList<>(combinedLore.size());
                    for (String line : combinedLore) {
                        renderedLore.add(lang.parse(replace(line, placeholders), player).decoration(TextDecoration.ITALIC, false));
                    }
                    meta.lore(renderedLore);
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        private static String replace(String text, Map<String, String> placeholders) {
            String result = text;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }
    }

    public static final class Builder {
        private ItemDefinition previousButton = new ItemDefinition(
            "previous", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ᴛʀướᴄ", List.of("<white>Nhấp để quay về trang trước"));
        private ItemDefinition nextButton = new ItemDefinition(
            "next", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ѕᴀᴜ", List.of("<white>Nhấp để sang trang sau"));
        private ItemDefinition sortButton = new ItemDefinition(
            "sort", Material.CAULDRON, "<#9CC0D9>ѕắᴘ хếᴘ", List.of());
        private ItemDefinition refreshButton = new ItemDefinition(
            "refresh", Material.ANVIL, "<#9CC0D9>ɢɪá ᴛʀị ᴠậᴛ ᴘʜẩᴍ", List.of("<white>Nhấp để làm mới"));
        private ItemDefinition filterButton = new ItemDefinition(
            "filter", Material.HOPPER, "<#9CC0D9>ʟọᴄ", List.of());

        public Builder previousButton(@NotNull ItemDefinition previousButton) {
            this.previousButton = previousButton;
            return this;
        }

        public Builder nextButton(@NotNull ItemDefinition nextButton) {
            this.nextButton = nextButton;
            return this;
        }

        public Builder sortButton(@NotNull ItemDefinition sortButton) {
            this.sortButton = sortButton;
            return this;
        }

        public Builder refreshButton(@NotNull ItemDefinition refreshButton) {
            this.refreshButton = refreshButton;
            return this;
        }

        public Builder refreshButton(@NotNull Material material, @NotNull String name, @NotNull List<String> lore) {
            this.refreshButton = new ItemDefinition("refresh", material, name, lore);
            return this;
        }

        public Builder filterButton(@NotNull ItemDefinition filterButton) {
            this.filterButton = filterButton;
            return this;
        }

        @NotNull
        public PagedToolbarConfig build() {
            return new PagedToolbarConfig(this.previousButton, this.nextButton, this.sortButton, this.refreshButton, this.filterButton);
        }
    }
}
