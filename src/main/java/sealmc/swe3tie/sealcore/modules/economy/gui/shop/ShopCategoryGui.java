package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.util.Locale;
import java.util.Map;

/**
 * Category shop GUI displaying purchasable items for a specific section.
 */
public final class ShopCategoryGui {

    private ShopCategoryGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Player player;
        private final ShopCategoryGuiConfig config;
        private final String categoryId;
        private final ShopCategoryGuiConfig.CategoryData category;
        private boolean suppressClose;
        private Inventory inventory;

        public Holder(
            @NotNull Player player,
            @NotNull ShopCategoryGuiConfig config,
            @NotNull String categoryId,
            @NotNull ShopCategoryGuiConfig.CategoryData category
        ) {
            this.player = player;
            this.config = config;
            this.categoryId = categoryId;
            this.category = category;
        }

        public @NotNull Player player() {
            return this.player;
        }

        public @NotNull ShopCategoryGuiConfig config() {
            return this.config;
        }

        public @NotNull String categoryId() {
            return this.categoryId;
        }

        public @NotNull ShopCategoryGuiConfig.CategoryData category() {
            return this.category;
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
            if (this.inventory == null) throw new IllegalStateException("ShopCategoryGui inventory not initialized");
            return this.inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull EconomyModule module, @NotNull String categoryId) {
        ShopCategoryGuiConfig cfg = module.shopCategoryGuiConfig();
        ShopCategoryGuiConfig.CategoryData category = cfg.categories().get(categoryId);
        if (category == null) {
            ShopMainGui.open(player, module);
            return;
        }

        String sectionName = category.sectionItem() != null
            ? GuiConfigHelper.stripTags(category.sectionItem().name())
            : (categoryId.substring(0, 1).toUpperCase(Locale.ROOT) + categoryId.substring(1));
        String titleRaw = category.title().replace("{SECTION}", sectionName).replace("{section}", sectionName);
        Component title = module.core().lang().parse(titleRaw, player);

        Holder holder = new Holder(player, cfg, categoryId, category);
        Inventory inv = Bukkit.createInventory(holder, category.size(), title);
        holder.setInventory(inv);

        for (ShopCategoryGuiConfig.ItemData item : category.items().values()) {
            if (item.slot() >= 0 && item.slot() < inv.getSize()) {
                Map<String, String> placeholders = GuiConfigHelper.formatPricePlaceholders(item.currency(), item.price());
                ItemStack stack = item.definition().createItemStack(player, module.core().lang(), placeholders, category.defaultLore());
                inv.setItem(item.slot(), stack);
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

        ShopCategoryGuiConfig.ItemData item = holder.category().items().get(rawSlot);
        if (item != null) {
            if (item.spawner()) {
                module.core().lang().send(player, "economy.shop.spawner-unavailable");
                return;
            }
            holder.suppressClose();
            ShopAmountGui.open(player, module, holder.categoryId(), item, 1);
        }
    }
}
