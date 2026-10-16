package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;

import java.util.Set;

/** Identifies an active /sell or category-progress GUI session for a player. */
public final class SellInventoryHolder implements InventoryHolder {

    public enum Kind { SELL, PROGRESS, MULTIPLIER }

    private final Player player;
    private final Kind kind;
    private final Kind parentKind;
    private final Set<Integer> sellSlots;
    private final SellCategory category;
    private int page;
    private boolean suppressClose;
    private Inventory inventory;

    public SellInventoryHolder(@NotNull Player player, @NotNull Set<Integer> sellSlots) {
        this(player, Kind.SELL, null, sellSlots, null, 1);
    }

    public SellInventoryHolder(@NotNull Player player, @NotNull SellCategory category, int page) {
        this(player, Kind.PROGRESS, Kind.SELL, Set.of(), category, page);
    }

    public SellInventoryHolder(@NotNull Player player, @NotNull SellCategory category, int page, @Nullable Kind parentKind) {
        this(player, Kind.PROGRESS, parentKind != null ? parentKind : Kind.SELL, Set.of(), category, page);
    }

    public static SellInventoryHolder createMultiplier(@NotNull Player player) {
        return new SellInventoryHolder(player, Kind.MULTIPLIER, null, Set.of(), null, 1);
    }

    private SellInventoryHolder(@NotNull Player player, @NotNull Kind kind, @Nullable Kind parentKind,
                                @NotNull Set<Integer> sellSlots, @Nullable SellCategory category, int page) {
        this.player = player;
        this.kind = kind;
        this.parentKind = parentKind;
        this.sellSlots = Set.copyOf(sellSlots);
        this.category = category;
        this.page = page;
    }

    @NotNull
    public Player player() {
        return this.player;
    }

    @NotNull
    public Kind kind() {
        return this.kind;
    }

    public boolean isSellGui() {
        return this.kind == Kind.SELL;
    }

    public boolean isProgressGui() {
        return this.kind == Kind.PROGRESS;
    }

    public boolean isMultiplierGui() {
        return this.kind == Kind.MULTIPLIER;
    }

    @Nullable
    public Kind parentKind() {
        return this.parentKind;
    }

    @NotNull
    public Set<Integer> sellSlots() {
        return this.sellSlots;
    }

    public boolean isSellSlot(int slot) {
        return this.sellSlots.contains(slot);
    }

    @Nullable
    public SellCategory category() {
        return this.category;
    }

    public int page() {
        return this.page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void suppressClose() {
        this.suppressClose = true;
    }

    public boolean consumeSuppressedClose() {
        boolean value = this.suppressClose;
        this.suppressClose = false;
        return value;
    }

    public void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (this.inventory == null) throw new IllegalStateException("GUI inventory not initialized");
        return this.inventory;
    }
}
