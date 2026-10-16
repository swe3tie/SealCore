package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.modules.economy.gui.shop.ShopAmountGui;
import sealmc.swe3tie.sealcore.modules.economy.gui.shop.ShopCategoryGui;
import sealmc.swe3tie.sealcore.modules.economy.gui.shop.ShopMainGui;

import java.util.List;

/**
 * Handles the {@code /shop} command and dispatches GUI interaction events to respective GUI handlers.
 */
public final class ShopCommand implements Listener {

    private final EconomyModule module;

    public ShopCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void register() {
        ConfigurationSection section = this.module.config().getConfigurationSection("commands.shop");
        String name = section != null ? section.getString("name", "shop") : "shop";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.shop") : "sealcore.economy.shop";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.openGui(ctx.getSource().getSender()));

        this.module.core().commands().register(this.module::isActive, root, "Open shop menu", aliases, permission);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            Inventory top = view.getTopInventory();
            if (top.getHolder() instanceof ShopMainGui.Holder holder) {
                holder.suppressClose();
                player.closeInventory();
            } else if (top.getHolder() instanceof ShopCategoryGui.Holder holder) {
                holder.suppressClose();
                player.closeInventory();
            } else if (top.getHolder() instanceof ShopAmountGui.Holder holder) {
                holder.suppressClose();
                player.closeInventory();
            }
        }
    }

    private int openGui(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.module.core().lang().send(sender, "core.players-only");
            return 0;
        }
        if (this.module.currency() == null) {
            this.module.core().lang().send(player, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }
        ShopMainGui.open(player, this.module);
        return 1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (top.getHolder() instanceof ShopMainGui.Holder) {
            ShopMainGui.handleClick(event, player, this.module);
        } else if (top.getHolder() instanceof ShopCategoryGui.Holder) {
            ShopCategoryGui.handleClick(event, player, this.module);
        } else if (top.getHolder() instanceof ShopAmountGui.Holder) {
            ShopAmountGui.handleClick(event, player, this.module);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopMainGui.Holder
            || top.getHolder() instanceof ShopCategoryGui.Holder
            || top.getHolder() instanceof ShopAmountGui.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();

        if (inv.getHolder() instanceof ShopAmountGui.Holder amountHolder) {
            if (amountHolder.consumeSuppressedClose()) return;
            this.module.core().scheduler().entityLater(player, () -> {
                if (!player.isOnline()) return;
                ShopCategoryGui.open(player, this.module, amountHolder.categoryId());
            }, null, 1L);
            return;
        }

        if (inv.getHolder() instanceof ShopCategoryGui.Holder catHolder) {
            if (catHolder.consumeSuppressedClose()) return;
            this.module.core().scheduler().entityLater(player, () -> {
                if (!player.isOnline()) return;
                ShopMainGui.open(player, this.module);
            }, null, 1L);
        }
    }
}
