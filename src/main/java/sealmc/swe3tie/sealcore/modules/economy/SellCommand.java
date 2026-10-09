package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellGuiConfig;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellInventoryHolder;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;

/** {@code /sell} opens a configurable inventory GUI to sell items. */
public final class SellCommand implements Listener {

    private final EconomyModule module;

    public SellCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void register() {
        var section = this.module.config().getConfigurationSection("commands.sell");
        String name = section != null ? section.getString("name", "sell") : "sell";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.sell") : "sealcore.economy.sell";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.openGui(ctx.getSource().getSender()));

        this.module.core().commands().register(this.module::isActive, root, "Open sell menu", aliases, permission);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory().getHolder() instanceof SellInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private int openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.module.core().lang().send(sender, "core.players-only");
            return 0;
        }

        CurrencyService currency = this.module.currency();
        if (currency == null) {
            this.module.core().lang().send(player, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }

        SellGuiConfig gui = this.module.guiConfig();
        SellInventoryHolder holder = new SellInventoryHolder(player, gui.sellSlots());
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), this.buildTitle(player, 0D));
        holder.setInventory(inventory);
        this.placeConfiguredItems(player, inventory, gui);

        player.openInventory(inventory);
        return 1;
    }

    private void placeConfiguredItems(Player player, Inventory inventory, SellGuiConfig gui) {
        for (SellGuiConfig.ItemDefinition definition : gui.items()) {
            ItemStack item = definition.createItem(player, this.module.core().lang());
            for (int slot : definition.slots()) inventory.setItem(slot, item.clone());
        }
    }

    private boolean isTopInventorySlot(InventoryView view, int rawSlot) {
        return rawSlot >= 0 && rawSlot < view.getTopInventory().getSize();
    }

    private void protectBottomInventoryClick(InventoryClickEvent event) {
        if (event.getClick().isKeyboardClick()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (this.isTopInventorySlot(event.getView(), rawSlot)) {
            if (!holder.isSellSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        } else {
            this.protectBottomInventoryClick(event);
        }
        this.scheduleTitleUpdate(player, event.getView(), holder);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !holder.isSellSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        this.scheduleTitleUpdate(player, event.getView(), holder);
    }

    @SuppressWarnings("deprecation")
    private void scheduleTitleUpdate(Player player, InventoryView view, SellInventoryHolder holder) {
        this.module.core().scheduler().entityLater(player, () -> {
            if (player.getOpenInventory() != view) return;
            double total = this.module.priceService().calculateTotal(holder.getInventory(), holder.sellSlots());
            view.setTitle(LegacyComponentSerializer.legacySection().serialize(this.buildTitle(player, total)));
        }, null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        Player player = holder.player();
        Inventory inventory = event.getInventory();

        double total = 0D;
        List<ItemStack> unsellable = new ArrayList<>();
        for (int slot : holder.sellSlots()) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || stack.getType().isAir()) continue;
            inventory.setItem(slot, null);

            double itemTotal = this.module.priceService().getItemPrice(stack);
            if (itemTotal > 0D) total += itemTotal;
            else unsellable.add(stack);
        }

        for (ItemStack item : unsellable) {
            var leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        if (!(total > 0D)) return;
        CurrencyService currency = this.module.currency();
        if (currency == null) return;

        double finalTotal = total;
        currency.deposit(player.getUniqueId(), "money", finalTotal, "sell").whenComplete((success, err) -> {
            if (err != null || !Boolean.TRUE.equals(success)) return;

            this.module.core().scheduler().entity(player, () -> {
                String unformatted = MoneyFormat.formatStandard(finalTotal);
                String formatted = MoneyFormat.formatCompact(finalTotal);
                var lang = this.module.core().lang();
                lang.send(player, "economy.sell.sold.chat",
                    Placeholder.unparsed("money", unformatted),
                    Placeholder.unparsed("money_formatted", formatted),
                    Placeholder.unparsed("total", unformatted),
                    Placeholder.unparsed("total_formatted", formatted));
                player.sendActionBar(lang.component("economy.sell.sold.actionbar", player,
                    Placeholder.unparsed("money", unformatted),
                    Placeholder.unparsed("money_formatted", formatted),
                    Placeholder.unparsed("total", unformatted),
                    Placeholder.unparsed("total_formatted", formatted)));
            });
        });
    }

    private Component buildTitle(Player player, double total) {
        String unformatted = MoneyFormat.formatStandard(total);
        String formatted = MoneyFormat.formatCompact(total);
        return this.module.core().lang().parse(this.module.guiConfig().title(), player,
            Placeholder.unparsed("total", unformatted),
            Placeholder.unparsed("total_formatted", formatted),
            Placeholder.unparsed("money", unformatted),
            Placeholder.unparsed("money_formatted", formatted));
    }
}
