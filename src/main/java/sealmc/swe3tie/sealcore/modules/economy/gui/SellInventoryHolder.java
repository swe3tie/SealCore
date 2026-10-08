package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an active /sell GUI session for a player. */
public final class SellInventoryHolder implements InventoryHolder {

    private final Player player;
    private Inventory inventory;

    public SellInventoryHolder(@NotNull Player player) {
        this.player = player;
    }

    public @NotNull Player player() {
        return this.player;
    }

    public void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
