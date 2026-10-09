package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Identifies an active /sell GUI session for a player. */
public final class SellInventoryHolder implements InventoryHolder {

    private final Player player;
    private final Set<Integer> sellSlots;
    private Inventory inventory;

    public SellInventoryHolder(@NotNull Player player, @NotNull Set<Integer> sellSlots) {
        this.player = player;
        this.sellSlots = Set.copyOf(sellSlots);
    }

    @NotNull
    public Player player() {
        return this.player;
    }

    @NotNull
    public Set<Integer> sellSlots() {
        return this.sellSlots;
    }

    public boolean isSellSlot(int slot) {
        return this.sellSlots.contains(slot);
    }

    public void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
