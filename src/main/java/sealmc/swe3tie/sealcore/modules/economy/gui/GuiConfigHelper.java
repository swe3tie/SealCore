package sealmc.swe3tie.sealcore.modules.economy.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.lang.LangService;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Common configuration reader, item builder, and currency formatter for all economy GUIs.
 */
public final class GuiConfigHelper {

    private GuiConfigHelper() {
    }

    // -------------------------------------------------------------------------
    // Currency Prefix & Suffix Standard:
    // money:  prefix "<green>$", suffix ""
    // shards: prefix "<dark_purple>", suffix " ⭐"
    // -------------------------------------------------------------------------

    @NotNull
    public static String getCurrencyPrefix(@Nullable String currency) {
        if (currency != null && currency.equalsIgnoreCase("shards")) {
            return "<dark_purple>";
        }
        return "<green>$";
    }

    @NotNull
    public static String getCurrencySuffix(@Nullable String currency) {
        if (currency != null && currency.equalsIgnoreCase("shards")) {
            return " ⭐";
        }
        return "";
    }

    @NotNull
    public static String formatPrice(@Nullable String currency, double amount) {
        return getCurrencyPrefix(currency) + MoneyFormat.formatCompact(amount) + getCurrencySuffix(currency);
    }

    /**
     * Builds standard price placeholders for MiniMessage templates:
     * {CURRENCY_PREFIX}, {PRICE_FORMATTED}, {CURRENCY_SUFFIX}, {PRICE_COMPACT}, {PRICE}, {CURRENCY}
     */
    @NotNull
    public static Map<String, String> formatPricePlaceholders(@Nullable String currency, double amount) {
        String compact = MoneyFormat.formatCompact(amount);
        String standard = MoneyFormat.formatStandard(amount);
        String curKey = currency != null ? currency : "money";

        Map<String, String> map = new HashMap<>();
        map.put("CURRENCY_PREFIX", getCurrencyPrefix(currency));
        map.put("CURRENCY_SUFFIX", getCurrencySuffix(currency));
        map.put("PRICE_FORMATTED", compact);
        map.put("PRICE_COMPACT", compact);
        map.put("PRICE", standard);
        map.put("TOTAL_FORMATTED", compact);
        map.put("WORTH_FORMATTED", compact);
        map.put("CURRENCY", curKey);
        return map;
    }

    // -------------------------------------------------------------------------
    // Reading Common GUI Section Headers: title & rows
    // -------------------------------------------------------------------------

    @NotNull
    public static String readTitle(@Nullable ConfigurationSection section, @NotNull String defaultTitle) {
        if (section == null) return defaultTitle;
        return section.getString("title", defaultTitle);
    }

    public static int readRows(@Nullable ConfigurationSection section, int defaultRows) {
        if (section == null) return defaultRows;
        int rows = section.getInt("rows", defaultRows);
        return Math.clamp(rows, 1, 6);
    }

    // -------------------------------------------------------------------------
    // Item / Button Definition & Construction
    // -------------------------------------------------------------------------

    public record ItemDefinition(
        int slot,
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore,
        @Nullable String base64,
        @Nullable String potionType
    ) {
        public ItemDefinition {
            lore = List.copyOf(lore);
        }

        @NotNull
        public ItemStack createItemStack(
            @Nullable Player player,
            @NotNull LangService lang,
            @NotNull Map<String, String> placeholders
        ) {
            return this.createItemStack(player, lang, placeholders, List.of());
        }

        @NotNull
        public ItemStack createItemStack(
            @Nullable Player player,
            @NotNull LangService lang,
            @NotNull Map<String, String> placeholders,
            @NotNull List<String> fallbackLore
        ) {
            Material mat = this.material;
            if (this.base64 != null && !this.base64.isBlank()) {
                mat = Material.PLAYER_HEAD;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (this.base64 != null && !this.base64.isBlank() && meta instanceof SkullMeta skullMeta) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    profile.setProperty(new ProfileProperty("textures", this.base64));
                    skullMeta.setPlayerProfile(profile);
                } catch (Throwable ignored) {
                }
            }

            if (this.potionType != null && meta instanceof PotionMeta potionMeta) {
                try {
                    PotionType pt = parsePotionType(this.potionType);
                    if (pt != null) {
                        potionMeta.setBasePotionType(pt);
                    }
                } catch (Throwable ignored) {
                }
            }

            String renderedName = replacePlaceholders(this.name, placeholders);
            if (!renderedName.isEmpty()) {
                meta.displayName(lang.parse(renderedName, player).decoration(TextDecoration.ITALIC, false));
            }

            List<String> effectiveLore = !this.lore.isEmpty() ? this.lore : fallbackLore;
            if (!effectiveLore.isEmpty()) {
                List<Component> renderedLore = new ArrayList<>(effectiveLore.size());
                for (String line : effectiveLore) {
                    renderedLore.add(lang.parse(replacePlaceholders(line, placeholders), player)
                        .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(renderedLore);
            }

            item.setItemMeta(meta);
            return item;
        }

        @NotNull
        public ItemStack createPhysicalItem(int amount) {
            Material mat = this.material;
            if (this.base64 != null && !this.base64.isBlank()) {
                mat = Material.PLAYER_HEAD;
            }
            ItemStack item = new ItemStack(mat, Math.max(1, amount));
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (this.base64 != null && !this.base64.isBlank() && meta instanceof SkullMeta skullMeta) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    profile.setProperty(new ProfileProperty("textures", this.base64));
                    skullMeta.setPlayerProfile(profile);
                } catch (Throwable ignored) {
                }
            }
            if (this.potionType != null && meta instanceof PotionMeta potionMeta) {
                try {
                    PotionType pt = parsePotionType(this.potionType);
                    if (pt != null) {
                        potionMeta.setBasePotionType(pt);
                    }
                } catch (Throwable ignored) {
                }
            }
            item.setItemMeta(meta);
            return item;
        }

        public int maxStackSize() {
            if (this.base64 != null && !this.base64.isBlank()) return 64;
            try {
                return this.material.getMaxStackSize();
            } catch (Throwable ignored) {
                return 64;
            }
        }
    }

    public static boolean isAir(@Nullable Material mat) {
        return mat == null || mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    @NotNull
    public static ItemDefinition readItemDefinition(
        @Nullable ConfigurationSection parent,
        @NotNull String key,
        int defaultSlot,
        @NotNull Material defaultMaterial,
        @NotNull String defaultName,
        @NotNull List<String> defaultLore
    ) {
        if (parent == null) {
            return new ItemDefinition(defaultSlot, defaultMaterial, defaultName, defaultLore, null, null);
        }
        ConfigurationSection sec = parent.getConfigurationSection(key);
        return readItemDefinition(sec, defaultSlot, defaultMaterial, defaultName, defaultLore);
    }

    @NotNull
    public static ItemDefinition readItemDefinition(
        @Nullable ConfigurationSection sec,
        int defaultSlot,
        @NotNull Material defaultMaterial,
        @NotNull String defaultName,
        @NotNull List<String> defaultLore
    ) {
        if (sec == null) {
            return new ItemDefinition(defaultSlot, defaultMaterial, defaultName, defaultLore, null, null);
        }

        int slot = defaultSlot;
        if (sec.contains("slot")) {
            slot = Math.max(0, sec.getInt("slot") - 1);
        }

        Material mat = parseMaterial(sec.getString("material"));
        if (isAir(mat)) mat = defaultMaterial;

        String name = sec.getString("name", defaultName);
        List<String> lore = sec.contains("lore") ? sec.getStringList("lore") : defaultLore;
        String base64 = sec.getString("base64", null);
        String potionType = sec.getString("potion-type", null);

        return new ItemDefinition(slot, mat, name, lore, base64, potionType);
    }

    // -------------------------------------------------------------------------
    // Material & Potion Parsing
    // -------------------------------------------------------------------------

    @Nullable
    public static Material parseMaterial(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim();
        if (clean.regionMatches(true, 0, "minecraft:", 0, 10)) {
            clean = clean.substring(10);
        }
        try {
            return Material.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            try {
                return Material.matchMaterial(name);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    @Nullable
    public static PotionType parsePotionType(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim();
        if (clean.regionMatches(true, 0, "minecraft:", 0, 10)) {
            clean = clean.substring(10);
        }
        try {
            return PotionType.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @NotNull
    public static String stripTags(@NotNull String text) {
        return text.replaceAll("<[^>]+>", "").trim();
    }

    @NotNull
    public static String replacePlaceholders(@NotNull String text, @NotNull Map<String, String> placeholders) {
        String result = text.replace("<accent>", "<#9CC0D9>").replace("</accent>", "</#9CC0D9>");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String val = entry.getValue();
            String key = entry.getKey();
            result = result.replace("{" + key + "}", val);
            result = result.replace("{" + key.toLowerCase(Locale.ROOT) + "}", val);
            result = result.replace("{" + key.toUpperCase(Locale.ROOT) + "}", val);
        }
        return result;
    }
}
