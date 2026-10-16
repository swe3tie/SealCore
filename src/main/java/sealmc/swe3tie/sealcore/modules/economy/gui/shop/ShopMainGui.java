package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.util.Map;

/**
 * Main shop GUI allowing players to select a category.
 */
public final class ShopMainGui {

    private ShopMainGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Player player;
        private final ShopMainGuiConfig config;
        private boolean suppressClose;
        private Inventory inventory;

        public Holder(@NotNull Player player, @NotNull ShopMainGuiConfig config) {
            this.player = player;
            this.config = config;
        }

        public @NotNull Player player() {
            return this.player;
        }

        public @NotNull ShopMainGuiConfig config() {
            return this.config;
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
            if (this.inventory == null) throw new IllegalStateException("ShopMainGui inventory not initialized");
            return this.inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull EconomyModule module) {
        ShopMainGuiConfig cfg = module.shopMainGuiConfig();
        Component title = module.core().lang().parse(cfg.title(), player);
        Holder holder = new Holder(player, cfg);
        Inventory inv = Bukkit.createInventory(holder, cfg.size(), title);
        holder.setInventory(inv);

        for (Map.Entry<String, GuiConfigHelper.ItemDefinition> entry : cfg.categories().entrySet()) {
            GuiConfigHelper.ItemDefinition item = entry.getValue();
            if (item.slot() >= 0 && item.slot() < inv.getSize()) {
                String plainName = GuiConfigHelper.stripTags(item.name());
                Map<String, String> placeholders = Map.of("SECTION", plainName);
                inv.setItem(item.slot(), item.createItemStack(player, module.core().lang(), placeholders));
            }
        }

        player.openInventory(inv);
    }

    public static void handleClick(
        @NotNull InventoryClickEvent event,
        @NotNull Player player,
        @NotNull EconomyModule module
    ) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot != event.getSlot() || rawSlot < 0 || rawSlot >= top.getSize()) return;

        for (Map.Entry<String, GuiConfigHelper.ItemDefinition> entry : holder.config().categories().entrySet()) {
            if (entry.getValue().slot() == rawSlot) {
                holder.suppressClose();
                ShopCategoryGui.open(player, module, entry.getKey());
                return;
            }
        }
    }
}
