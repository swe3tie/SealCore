package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quantity selection menu allowing players to choose how many items to buy and confirm purchase.
 */
public final class ShopAmountGui {

    private ShopAmountGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Player player;
        private final ShopAmountGuiConfig config;
        private final String categoryId;
        private final ShopCategoryGuiConfig.ItemData item;
        private int amount;
        private boolean suppressClose;
        private Inventory inventory;

        public Holder(
            @NotNull Player player,
            @NotNull ShopAmountGuiConfig config,
            @NotNull String categoryId,
            @NotNull ShopCategoryGuiConfig.ItemData item,
            int initialAmount
        ) {
            this.player = player;
            this.config = config;
            this.categoryId = categoryId;
            this.item = item;
            int maxStack = item.definition().maxStackSize();
            this.amount = Math.max(1, Math.min(initialAmount, maxStack));
        }

        public @NotNull Player player() {
            return this.player;
        }

        public @NotNull ShopAmountGuiConfig config() {
            return this.config;
        }

        public @NotNull String categoryId() {
            return this.categoryId;
        }

        public @NotNull ShopCategoryGuiConfig.ItemData item() {
            return this.item;
        }

        public int amount() {
            return this.amount;
        }

        public void setAmount(int amount) {
            int maxStack = this.item.definition().maxStackSize();
            this.amount = Math.max(1, Math.min(amount, maxStack));
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
            if (this.inventory == null) throw new IllegalStateException("ShopAmountGui inventory not initialized");
            return this.inventory;
        }
    }

    public static void open(
        @NotNull Player player,
        @NotNull EconomyModule module,
        @NotNull String categoryId,
        @NotNull ShopCategoryGuiConfig.ItemData item,
        int initialAmount
    ) {
        ShopAmountGuiConfig cfg = module.shopAmountGuiConfig();
        Component title = module.core().lang().parse(cfg.title(), player);

        Holder holder = new Holder(player, cfg, categoryId, item, initialAmount);
        Inventory inv = Bukkit.createInventory(holder, cfg.size(), title);
        holder.setInventory(inv);

        refresh(inv, holder, module);
        player.openInventory(inv);
    }

    public static void refresh(
        @NotNull Inventory inv,
        @NotNull Holder holder,
        @NotNull EconomyModule module
    ) {
        ShopAmountGuiConfig cfg = holder.config();
        Player player = holder.player();
        ShopCategoryGuiConfig.ItemData item = holder.item();
        int maxStack = item.definition().maxStackSize();
        int current = holder.amount();

        // 1. Buying item preview
        Map<String, String> itemPlaceholders = GuiConfigHelper.formatPricePlaceholders(item.currency(), item.price());
        String itemName = resolveDisplayName(item);
        itemPlaceholders.put("ITEM", itemName);
        itemPlaceholders.put("BUYING_ITEM", itemName);
        itemPlaceholders.put("AMOUNT", String.valueOf(current));

        ShopCategoryGuiConfig.CategoryData category = module.shopCategoryGuiConfig().categories().get(holder.categoryId());
        List<String> defaultLore = category != null ? category.defaultLore() : List.of();
        List<String> effectiveLore = !cfg.buyingItem().lore().isEmpty()
            ? cfg.buyingItem().lore()
            : defaultLore;

        ItemStack preview = item.definition().createItemStack(
            player, module.core().lang(), itemPlaceholders, effectiveLore
        );
        if (!cfg.buyingItem().name().isBlank()) {
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                String renderedName = GuiConfigHelper.replacePlaceholders(cfg.buyingItem().name(), itemPlaceholders);
                meta.displayName(module.core().lang().parse(renderedName, player).decoration(TextDecoration.ITALIC, false));
                preview.setItemMeta(meta);
            }
        }
        preview.setAmount(current);
        inv.setItem(cfg.itemSlot(), preview);

        // 2. Adjustment buttons (only shown when item can stack > 1)
        if (maxStack > 1) {
            // Reset to 1
            if (current > 1) {
                inv.setItem(cfg.resetSlot(), cfg.resetBtn().createItemStack(player, module.core().lang(), Map.of()));
            } else {
                inv.setItem(cfg.resetSlot(), null);
            }

            // -10
            if (current - 10 >= 1) {
                inv.setItem(cfg.sub10Slot(), cfg.sub10Btn().createItemStack(player, module.core().lang(), Map.of()));
            } else {
                inv.setItem(cfg.sub10Slot(), null);
            }

            // -1
            if (current - 1 >= 1) {
                inv.setItem(cfg.sub1Slot(), cfg.sub1Btn().createItemStack(player, module.core().lang(), Map.of()));
            } else {
                inv.setItem(cfg.sub1Slot(), null);
            }

            // +1
            if (current + 1 <= maxStack) {
                inv.setItem(cfg.add1Slot(), cfg.add1Btn().createItemStack(player, module.core().lang(), Map.of()));
            } else {
                inv.setItem(cfg.add1Slot(), null);
            }

            // +10
            if (current + 10 <= maxStack) {
                inv.setItem(cfg.add10Slot(), cfg.add10Btn().createItemStack(player, module.core().lang(), Map.of()));
            } else {
                inv.setItem(cfg.add10Slot(), null);
            }

            // Max
            if (current < maxStack) {
                Map<String, String> maxPlaceholders = Map.of("MAX_VALUE", String.valueOf(maxStack));
                inv.setItem(cfg.maxSlot(), cfg.maxBtn().createItemStack(player, module.core().lang(), maxPlaceholders));
            } else {
                inv.setItem(cfg.maxSlot(), null);
            }
        } else {
            inv.setItem(cfg.resetSlot(), null);
            inv.setItem(cfg.sub10Slot(), null);
            inv.setItem(cfg.sub1Slot(), null);
            inv.setItem(cfg.add1Slot(), null);
            inv.setItem(cfg.add10Slot(), null);
            inv.setItem(cfg.maxSlot(), null);
        }

        // 3. Cancel button
        inv.setItem(cfg.cancelSlot(), cfg.cancelBtn().createItemStack(player, module.core().lang(), Map.of()));

        // 4. Confirm purchase button
        double totalPrice = item.price() * current;
        Map<String, String> confirmPlaceholders = GuiConfigHelper.formatPricePlaceholders(item.currency(), totalPrice);
        String confirmItemName = resolveDisplayName(item);
        confirmPlaceholders.put("ITEM", confirmItemName);
        confirmPlaceholders.put("BUYING_ITEM", confirmItemName);
        confirmPlaceholders.put("AMOUNT", String.valueOf(current));

        inv.setItem(cfg.confirmSlot(), cfg.confirmBtn().createItemStack(player, module.core().lang(), confirmPlaceholders));
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

        ShopAmountGuiConfig cfg = holder.config();
        int current = holder.amount();
        int maxStack = holder.item().definition().maxStackSize();

        if (rawSlot == cfg.resetSlot()) {
            if (maxStack > 1 && current > 1) {
                holder.setAmount(1);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.sub10Slot()) {
            if (maxStack > 1 && current - 10 >= 1) {
                holder.setAmount(current - 10);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.sub1Slot()) {
            if (maxStack > 1 && current - 1 >= 1) {
                holder.setAmount(current - 1);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.add1Slot()) {
            if (maxStack > 1 && current + 1 <= maxStack) {
                holder.setAmount(current + 1);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.add10Slot()) {
            if (maxStack > 1 && current + 10 <= maxStack) {
                holder.setAmount(current + 10);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.maxSlot()) {
            if (maxStack > 1 && current < maxStack) {
                holder.setAmount(maxStack);
                refresh(top, holder, module);
            }
        } else if (rawSlot == cfg.cancelSlot()) {
            holder.suppressClose();
            ShopCategoryGui.open(player, module, holder.categoryId());
        } else if (rawSlot == cfg.confirmSlot()) {
            handlePurchase(player, holder, module);
        }
    }

    private static void handlePurchase(
        @NotNull Player player,
        @NotNull Holder holder,
        @NotNull EconomyModule module
    ) {
        CurrencyService currency = module.currency();
        if (currency == null) {
            module.core().lang().send(player, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return;
        }

        ShopCategoryGuiConfig.ItemData item = holder.item();
        int amount = holder.amount();
        double totalCost = item.price() * amount;

        // Check inventory space for physical items
        if (item.rewardCurrency() == null) {
            ItemStack sample = item.definition().createPhysicalItem(1);
            if (!hasInventorySpace(player, sample, amount)) {
                module.core().lang().send(player, "economy.shop.inventory-full");
                return;
            }
        }

        // Check balance
        double currentBalance = currency.balance(player, item.currency());
        if (currentBalance < totalCost) {
            module.core().lang().send(player, "economy.shop.not-enough");
            return;
        }

        currency.withdraw(player.getUniqueId(), item.currency(), totalCost, "shop_purchase:" + item.id())
            .thenAccept(success -> {
                if (!success) {
                    module.core().lang().send(player, "economy.shop.not-enough");
                    return;
                }

                if (item.rewardCurrency() != null && !item.rewardCurrency().isBlank()) {
                    currency.deposit(player.getUniqueId(), item.rewardCurrency(), amount, "shop_reward:" + item.id())
                        .thenRun(() -> {
                            sendSuccess(player, item, amount, totalCost, module);
                            module.core().scheduler().entity(player, () -> {
                                if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
                                    refresh(holder.getInventory(), holder, module);
                                }
                            });
                        });
                } else {
                    module.core().scheduler().entity(player, () -> {
                        ItemStack purchased = item.definition().createPhysicalItem(amount);
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(purchased);
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItem(player.getLocation(), drop);
                        }
                        sendSuccess(player, item, amount, totalCost, module);
                        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
                            refresh(holder.getInventory(), holder, module);
                        }
                    });
                }
            });
    }

    private static boolean hasInventorySpace(@NotNull Player player, @NotNull ItemStack sample, int totalAmount) {
        int remaining = totalAmount;
        int maxStack = sample.getMaxStackSize();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                remaining -= maxStack;
            } else if (item.isSimilar(sample)) {
                remaining -= (maxStack - item.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void sendSuccess(
        @NotNull Player player,
        @NotNull ShopCategoryGuiConfig.ItemData item,
        int amount,
        double totalCost,
        @NotNull EconomyModule module
    ) {
        Map<String, String> pricePlaceholders = GuiConfigHelper.formatPricePlaceholders(item.currency(), totalCost);
        module.core().lang().send(player, "economy.shop.purchased",
            Placeholder.unparsed("amount", String.valueOf(amount)),
            Placeholder.unparsed("item", resolveDisplayName(item)),
            Placeholder.unparsed("CURRENCY_PREFIX", pricePlaceholders.get("CURRENCY_PREFIX")),
            Placeholder.unparsed("PRICE_FORMATTED", pricePlaceholders.get("PRICE_FORMATTED")),
            Placeholder.unparsed("CURRENCY_SUFFIX", pricePlaceholders.get("CURRENCY_SUFFIX")),
            Placeholder.unparsed("price", pricePlaceholders.get("CURRENCY_PREFIX") + pricePlaceholders.get("PRICE_FORMATTED") + pricePlaceholders.get("CURRENCY_SUFFIX"))
        );
    }

    @NotNull
    public static String resolveDisplayName(@NotNull ShopCategoryGuiConfig.ItemData item) {
        if (!item.definition().name().isBlank()) {
            return item.definition().name();
        }
        return item.definition().material().name().replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
