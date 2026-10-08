package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import sealmc.swe3tie.sealcore.modules.economy.gui.SellInventoryHolder;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /sell} opens a 5-row inventory GUI to sell items. */
public final class SellCommand implements Listener {

    private static final int GUI_SIZE = 45;
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

        SellInventoryHolder holder = new SellInventoryHolder(player);
        Component title = this.buildTitle(player, 0D);
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        player.openInventory(inv);
        return 1;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        this.scheduleTitleUpdate(player, event.getView(), holder.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        this.scheduleTitleUpdate(player, event.getView(), holder.getInventory());
    }

    private void scheduleTitleUpdate(Player player, InventoryView view, Inventory inventory) {
        this.module.core().scheduler().entityLater(player, () -> {
            if (player.getOpenInventory() != view) return;
            double total = this.module.priceService().calculateTotal(inventory);
            view.setTitle(this.buildTitle(player, total));
        }, null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        Player player = holder.player();
        Inventory inv = event.getInventory();

        double total = 0D;
        List<ItemStack> unsellable = new ArrayList<>();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty() || stack.getType().isAir()) continue;

            double unit = this.module.priceService().getUnitPrice(stack.getType());
            if (unit > 0D) {
                total += unit * stack.getAmount();
                inv.setItem(i, null);
            } else {
                unsellable.add(stack);
                inv.setItem(i, null);
            }
        }

        // Return unsellable items to player
        for (ItemStack item : unsellable) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        if (total <= 0D) return;

        double finalTotal = total;
        CurrencyService currency = this.module.currency();
        if (currency == null) return;

        currency.deposit(player.getUniqueId(), "money", finalTotal, "sell").whenComplete((success, err) -> {
            if (err != null || !Boolean.TRUE.equals(success)) return;

            this.module.core().scheduler().entity(player, () -> {
                String formatted = MoneyFormat.formatCompact(finalTotal);
                var lang = this.module.core().lang();
                lang.send(player, "economy.sell.sold.chat", Placeholder.unparsed("money", formatted));
                player.sendActionBar(lang.component("economy.sell.sold.actionbar", player,
                    Placeholder.unparsed("money", formatted)));
            });
        });
    }

    private Component buildTitle(Player player, double total) {
        String formatted = MoneyFormat.formatCompact(total);
        return this.module.core().lang().component("economy.sell.title", player,
            Placeholder.unparsed("total", formatted));
    }
}
