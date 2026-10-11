package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import sealmc.swe3tie.sealcore.modules.economy.util.ContainerHelper;

/** Manages item sell unit prices loaded from configuration. */
public final class ItemPriceService {

    private final Map<String, Double> materialPrices = new HashMap<>();
    private final Map<String, Double> subtypePrices = new HashMap<>();
    private final Map<String, SellCategory> materialCategories = new HashMap<>();
    private final Map<String, SellCategory> subtypeCategories = new HashMap<>();
    private final List<WorthItem> worthItems = new ArrayList<>();

    /** Loads either flat prices or category sections under {@code prices}. */
    public void load(@Nullable ConfigurationSection section) {
        this.materialPrices.clear();
        this.subtypePrices.clear();
        this.materialCategories.clear();
        this.subtypeCategories.clear();
        this.worthItems.clear();
        if (section == null) return;

        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection categorySection = section.getConfigurationSection(rawKey);
            SellCategory category = SellCategory.fromId(rawKey);
            if (categorySection != null && category != null) {
                this.loadPrices(categorySection, category);
            } else if (categorySection == null) {
                this.loadPrice(rawKey, section.getDouble(rawKey, 0D), null);
            }
        }
    }

    private void loadPrices(@NotNull ConfigurationSection section, @NotNull SellCategory category) {
        for (String rawKey : section.getKeys(false)) {
            this.loadPrice(rawKey, section.getDouble(rawKey, 0D), category);
        }
    }

    private void loadPrice(@NotNull String rawKey, double price, @Nullable SellCategory category) {
        if (!(price > 0D) || !Double.isFinite(price)) return;
        String key = normalize(rawKey);
        int colon = key.indexOf(':');
        if (colon > 0) {
            this.subtypePrices.put(key, price);
            if (category != null) this.subtypeCategories.put(key, category);
            String matName = key.substring(0, colon);
            String sub = key.substring(colon + 1);
            Material mat = parseMaterial(matName);
            if (mat != null) {
                if (mat == Material.ENCHANTED_BOOK) {
                    int under = sub.lastIndexOf('_');
                    if (under > 0) {
                        String enchantName = sub.substring(0, under);
                        int level = 1;
                        try {
                            level = Integer.parseInt(sub.substring(under + 1));
                        } catch (NumberFormatException ignored) {}
                        String display = "Enchanted Book (" + formatDisplayName(enchantName) + " " + level + ")";
                        this.worthItems.add(new WorthItem(key, mat, null, Map.of(enchantName.toLowerCase(Locale.ROOT), level), price, category, display));
                    }
                } else {
                    String display = WorthItem.formatMaterialName(mat) + " (" + formatDisplayName(sub) + ")";
                    this.worthItems.add(new WorthItem(key, mat, sub.toLowerCase(Locale.ROOT), Map.of(), price, category, display));
                }
            }
        } else {
            this.materialPrices.put(key, price);
            if (category != null) this.materialCategories.put(key, category);
            Material mat = parseMaterial(key);
            if (mat != null) {
                this.worthItems.add(new WorthItem(key, mat, null, Map.of(), price, category, WorthItem.formatMaterialName(mat)));
            }
        }
    }

    private static String formatDisplayName(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = clean.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (i > 0) builder.append(' ');
            builder.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return builder.toString();
    }

    private static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Material.matchMaterial(name);
        }
    }

    /** Returns all sellable items configured for display in worth lookup. */
    @NotNull
    public List<WorthItem> allWorthItems() {
        return Collections.unmodifiableList(this.worthItems);
    }

    /** Returns unit price for material, or 0 if unsellable. */
    public double getUnitPrice(@NotNull Material material) {
        return this.materialPrices.getOrDefault(material.name(), 0D);
    }

    /** Returns unit price for item metadata, or 0 if unsellable. */
    public double getUnitPrice(@NotNull ItemStack item) {
        if (ContainerHelper.isEmptyOrAir(item)) return 0D;
        Metadata metadata = metadata(item);
        return this.getUnitPrice(item.getType(), metadata.potionType(), metadata.enchantments());
    }

    /** Pure metadata lookup, kept package-private for tests without a server registry. */
    double getUnitPrice(@NotNull Material material, @Nullable String potionType,
                        @NotNull Map<String, Integer> enchantments) {
        if (potionType != null) {
            Double price = this.subtypePrices.get(material.name() + ':' + normalize(potionType));
            if (price != null) return price;
        }

        if (material == Material.ENCHANTED_BOOK && !enchantments.isEmpty()) {
            double total = 0D;
            boolean found = false;
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                String key = material.name() + ':' + normalize(entry.getKey()) + '_' + entry.getValue();
                Double price = this.subtypePrices.get(key);
                if (price != null) {
                    total += price;
                    found = true;
                }
            }
            if (found) return total;
        }
        return this.getUnitPrice(material);
    }

    /** Returns category for item, or null when item has no configured price. */
    @Nullable
    public SellCategory category(@Nullable ItemStack item) {
        if (ContainerHelper.isEmptyOrAir(item)) return null;
        Metadata metadata = metadata(item);
        return this.category(item.getType(), metadata.potionType(), metadata.enchantments());
    }

    /** Pure category lookup for tests and non-server logic. */
    @Nullable
    SellCategory category(@NotNull Material material, @Nullable String potionType,
                          @NotNull Map<String, Integer> enchantments) {
        if (potionType != null) {
            SellCategory category = this.subtypeCategories.get(material.name() + ':' + normalize(potionType));
            if (category != null) return category;
        }
        if (material == Material.ENCHANTED_BOOK && !enchantments.isEmpty()) {
            for (String enchantment : enchantments.keySet()) {
                SellCategory category = this.subtypeCategories.get(
                    material.name() + ':' + normalize(enchantment) + '_' + enchantments.get(enchantment));
                if (category != null) return category;
            }
        }
        return this.materialCategories.get(material.name());
    }

    /** Returns total price for item stack, or 0 if item cannot be sold. */
    public double getItemPrice(@Nullable ItemStack item) {
        if (ContainerHelper.isEmptyOrAir(item)) return 0D;
        return this.getUnitPrice(item) * item.getAmount();
    }

    /** Pure stack total, kept package-private for tests without a server registry. */
    double getItemPrice(@NotNull Material material, int amount, @Nullable String potionType,
                        @NotNull Map<String, Integer> enchantments) {
        if (amount <= 0) return 0D;
        return this.getUnitPrice(material, potionType, enchantments) * amount;
    }

    /**
     * Returns total worth of item, recursively inspecting Shulker Boxes and Bundles.
     * Empty containers return their configured unit price if any.
     */
    public double getItemWorth(@Nullable ItemStack item) {
        if (ContainerHelper.isEmptyOrAir(item)) return 0D;
        if (ContainerHelper.isShulkerBox(item)) {
            List<ItemStack> inner = ContainerHelper.getShulkerItems(item);
            if (!inner.isEmpty()) {
                double total = 0D;
                for (ItemStack in : inner) total += this.getItemWorth(in);
                return total;
            }
        } else if (ContainerHelper.isBundle(item)) {
            List<ItemStack> inner = ContainerHelper.getBundleItems(item);
            if (!inner.isEmpty()) {
                double total = 0D;
                for (ItemStack in : inner) total += this.getItemWorth(in);
                return total;
            }
        }
        return this.getItemPrice(item);
    }

    /**
     * Pure container/item worth calculation for testing and non-server logic without ItemStack/Bukkit Registry.
     */
    double getItemWorth(@NotNull Material material, int amount, @Nullable String potionType,
                        @NotNull Map<String, Integer> enchantments, @NotNull List<TestItem> containerContents) {
        if (amount <= 0 || ContainerHelper.isAirMaterial(material)) return 0D;
        if (ContainerHelper.isContainer(material) && !containerContents.isEmpty()) {
            double total = 0D;
            for (TestItem in : containerContents) {
                total += this.getItemWorth(in.material(), in.amount(), in.potionType(), in.enchantments(), in.containerContents());
            }
            return total * amount;
        }
        return this.getItemPrice(material, amount, potionType, enchantments);
    }

    /** Helper record for testing container contents without ItemStack. */
    record TestItem(
        @NotNull Material material,
        int amount,
        @Nullable String potionType,
        @NotNull Map<String, Integer> enchantments,
        @NotNull List<TestItem> containerContents
    ) {
        TestItem(@NotNull Material material, int amount) {
            this(material, amount, null, Map.of(), List.of());
        }
        TestItem(@NotNull Material material, int amount, @NotNull List<TestItem> containerContents) {
            this(material, amount, null, Map.of(), containerContents);
        }
    }

    /** Pure container worth calculation for testing and non-server logic. */
    double calculateContainerWorth(@NotNull List<ItemStack> contents) {
        double total = 0D;
        for (ItemStack item : contents) {
            total += this.getItemWorth(item);
        }
        return total;
    }

    /** Calculates total value of all sellable items in the inventory. */
    public double calculateTotal(@NotNull Inventory inventory) {
        double total = 0D;
        for (ItemStack item : inventory.getContents()) total += this.getItemWorth(item);
        return total;
    }

    /** Calculates total value only in the configured sell slots. */
    public double calculateTotal(@NotNull Inventory inventory, @NotNull Set<Integer> slots) {
        double total = 0D;
        for (int slot : slots) total += this.getItemWorth(inventory.getItem(slot));
        return total;
    }

    private static Metadata metadata(@NotNull ItemStack item) {
        String potionType = null;
        Map<String, Integer> enchantments = Map.of();
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta potion && potion.getBasePotionType() != null) {
                potionType = potion.getBasePotionType().key().value();
            } else if (meta instanceof EnchantmentStorageMeta book && !book.getStoredEnchants().isEmpty()) {
                Map<String, Integer> stored = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
                    stored.put(entry.getKey().key().value(), entry.getValue());
                }
                enchantments = stored;
            }
        } catch (Throwable ignored) {
            // Server registry not available in unit tests
        }
        return new Metadata(potionType, enchantments);
    }

    private static String normalize(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }

    private record Metadata(String potionType, Map<String, Integer> enchantments) {
    }
}
