package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.WorthItem;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedGuiToolbar;

import java.util.EnumMap;
import java.util.Map;

/** InventoryHolder for the {@code /worth} lookup menu. */
public final class WorthInventoryHolder implements InventoryHolder {

    private final Player player;
    private final PagedGuiToolbar<WorthItem> toolbar;
    private final Map<SellCategory, Double> multipliers = new EnumMap<>(SellCategory.class);
    private Inventory inventory;

    public WorthInventoryHolder(@NotNull Player player, @NotNull PagedGuiToolbar<WorthItem> toolbar) {
        this.player = player;
        this.toolbar = toolbar;
    }

    @NotNull
    public Player player() {
        return this.player;
    }

    @NotNull
    public PagedGuiToolbar<WorthItem> toolbar() {
        return this.toolbar;
    }

    @NotNull
    public Map<SellCategory, Double> multipliers() {
        return this.multipliers;
    }

    public void setMultipliers(@NotNull Map<SellCategory, Double> multipliers) {
        this.multipliers.clear();
        this.multipliers.putAll(multipliers);
    }

    public void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        Inventory inv = this.inventory;
        if (inv == null) throw new IllegalStateException("inventory not initialized");
        return inv;
    }
}
