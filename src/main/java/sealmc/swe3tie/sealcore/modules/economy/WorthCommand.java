package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.gui.WorthGui;
import sealmc.swe3tie.sealcore.modules.economy.gui.WorthInventoryHolder;

import java.util.List;

/** {@code /worth} command and GUI event listener. */
public final class WorthCommand implements Listener {

    private final EconomyModule module;

    public WorthCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void register() {
        var section = this.module.config().getConfigurationSection("commands.worth");
        String name = section != null ? section.getString("name", "worth") : "worth";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.worth") : "sealcore.economy.worth";

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.openGui(ctx.getSource().getSender()));
        this.module.core().commands().register(this.module::isActive, root, "Open worth menu", aliases, permission);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory().getHolder() instanceof WorthInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private int openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.module.core().lang().send(sender, "core.players-only");
            return 0;
        }
        WorthGui.open(player, this.module, this.module.worthGuiConfig());
        return 1;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WorthInventoryHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= 0 && rawSlot < topSize) {
            holder.toolbar().handleClick(rawSlot, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WorthInventoryHolder) {
            event.setCancelled(true);
        }
    }
}
