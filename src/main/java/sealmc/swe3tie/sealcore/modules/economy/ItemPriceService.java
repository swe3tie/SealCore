package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Manages item sell unit prices loaded from configuration. */
public final class ItemPriceService {

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public void load(@Nullable ConfigurationSection section) {
        this.prices.clear();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) continue;
            double price = section.getDouble(key, 0D);
            if (price > 0D) {
                this.prices.put(material, price);
            }
        }
    }

    /** Returns unit price for material, or 0 if unsellable. */
    public double getUnitPrice(@NotNull Material material) {
        return this.prices.getOrDefault(material, 0D);
    }

    /** Returns total price for item stack, or 0 if item cannot be sold. */
    public double getItemPrice(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || item.getType().isAir()) return 0D;
        double unit = this.getUnitPrice(item.getType());
        return unit * item.getAmount();
    }

    /** Calculates total value of all sellable items in the inventory. */
    public double calculateTotal(@NotNull Inventory inventory) {
        double total = 0D;
        for (ItemStack item : inventory.getContents()) {
            total += this.getItemPrice(item);
        }
        return total;
    }
}
