package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Manages item sell unit prices loaded from configuration. */
public final class ItemPriceService {

    private final Map<String, Double> materialPrices = new HashMap<>();
    private final Map<String, Double> subtypePrices = new HashMap<>();

    public void load(@Nullable ConfigurationSection section) {
        this.materialPrices.clear();
        this.subtypePrices.clear();
        if (section == null) return;

        for (String rawKey : section.getKeys(false)) {
            double price = section.getDouble(rawKey, 0D);
            if (!(price > 0D) || !Double.isFinite(price)) continue;

            String key = normalize(rawKey);
            if (key.indexOf(':') > 0) {
                this.subtypePrices.put(key, price);
            } else {
                // Material names are resolved lazily, avoiding registry access during config load.
                this.materialPrices.put(key, price);
            }
        }
    }

    /** Returns unit price for material, or 0 if unsellable. */
    public double getUnitPrice(@NotNull Material material) {
        return this.materialPrices.getOrDefault(material.name(), 0D);
    }

    /** Returns unit price for item metadata, or 0 if unsellable. */
    public double getUnitPrice(@NotNull ItemStack item) {
        if (item.isEmpty() || item.getType().isAir()) return 0D;

        ItemMeta meta = item.getItemMeta();
        String potionType = null;
        Map<String, Integer> enchantments = Map.of();
        if (meta instanceof PotionMeta potion && potion.getBasePotionType() != null) {
            potionType = potion.getBasePotionType().key().value();
        } else if (meta instanceof EnchantmentStorageMeta book && !book.getStoredEnchants().isEmpty()) {
            Map<String, Integer> stored = new HashMap<>();
            for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
                stored.put(entry.getKey().key().value(), entry.getValue());
            }
            enchantments = stored;
        }
        return this.getUnitPrice(item.getType(), potionType, enchantments);
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

    /** Returns total price for item stack, or 0 if item cannot be sold. */
    public double getItemPrice(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || item.getType().isAir()) return 0D;
        return this.getUnitPrice(item) * item.getAmount();
    }

    /** Pure stack total, kept package-private for tests without a server registry. */
    double getItemPrice(@NotNull Material material, int amount, @Nullable String potionType,
                        @NotNull Map<String, Integer> enchantments) {
        if (amount <= 0) return 0D;
        return this.getUnitPrice(material, potionType, enchantments) * amount;
    }

    /** Calculates total value of all sellable items in the inventory. */
    public double calculateTotal(@NotNull Inventory inventory) {
        double total = 0D;
        for (ItemStack item : inventory.getContents()) total += this.getItemPrice(item);
        return total;
    }

    /** Calculates total value only in the configured sell slots. */
    public double calculateTotal(@NotNull Inventory inventory, @NotNull java.util.Set<Integer> slots) {
        double total = 0D;
        for (int slot : slots) total += this.getItemPrice(inventory.getItem(slot));
        return total;
    }

    private static String normalize(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
