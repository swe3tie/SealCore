package sealmc.swe3tie.sealcore.modules.economy.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.ItemPriceService;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellInventoryHolder;
import sealmc.swe3tie.sealcore.modules.economy.gui.WorthInventoryHolder;
import sealmc.swe3tie.sealcore.modules.economy.util.ContainerHelper;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Injects client-side worth lore into outbound inventory packets and strips it from
 * inbound client packets to ensure pristine server-side ItemStack data for crafting/splitting.
 */
public final class WorthPacketListener extends PacketListenerAbstract {

    private final ItemPriceService priceService;
    private volatile WorthLoreConfig loreConfig;

    public WorthPacketListener(@NotNull ItemPriceService priceService) {
        this(priceService, WorthLoreConfig.load(null));
    }

    public WorthPacketListener(@NotNull ItemPriceService priceService, @NotNull WorthLoreConfig loreConfig) {
        super(PacketListenerPriority.NORMAL);
        this.priceService = priceService;
        this.loreConfig = loreConfig;
    }

    public void setConfig(@NotNull WorthLoreConfig loreConfig) {
        this.loreConfig = loreConfig;
    }

    @NotNull
    public WorthLoreConfig config() {
        return this.loreConfig;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = event.getPlayer() instanceof Player p ? p : null;
        if (player != null && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            if (this.isGuiSlot(player, wrapper.getWindowId(), wrapper.getSlot())) {
                return;
            }
            com.github.retrooper.packetevents.protocol.item.ItemStack item = wrapper.getItem();
            if (item != null && !item.isEmpty()) {
                var modified = this.appendWorthLore(item);
                if (modified != null) {
                    wrapper.setItem(modified);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
            WrapperPlayServerSetCursorItem wrapper = new WrapperPlayServerSetCursorItem(event);
            com.github.retrooper.packetevents.protocol.item.ItemStack item = wrapper.getStack();
            if (item != null && !item.isEmpty()) {
                var modified = this.appendWorthLore(item);
                if (modified != null) {
                    wrapper.setStack(modified);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(event);
            com.github.retrooper.packetevents.protocol.item.ItemStack item = wrapper.getStack();
            if (item != null && !item.isEmpty()) {
                var modified = this.appendWorthLore(item);
                if (modified != null) {
                    wrapper.setStack(modified);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            int windowId = wrapper.getWindowId();
            List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = wrapper.getItems();
            if (items != null) {
                boolean changed = false;
                List<com.github.retrooper.packetevents.protocol.item.ItemStack> newItems = new ArrayList<>(items.size());
                for (int slot = 0; slot < items.size(); slot++) {
                    var item = items.get(slot);
                    if (item == null || item.isEmpty() || this.isGuiSlot(player, windowId, slot)) {
                        newItems.add(item);
                        continue;
                    }
                    var modified = this.appendWorthLore(item);
                    if (modified != null) {
                        newItems.add(modified);
                        changed = true;
                    } else {
                        newItems.add(item);
                    }
                }
                if (changed) {
                    wrapper.setItems(newItems);
                }
            }
            wrapper.getCarriedItem().ifPresent(carried -> {
                if (!carried.isEmpty()) {
                    var modified = this.appendWorthLore(carried);
                    if (modified != null) {
                        wrapper.setCarriedItem(modified);
                    }
                }
            });
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            var carried = wrapper.getCarriedItemStack();
            if (carried != null && !carried.isEmpty()) {
                var sanitized = stripWorthLore(carried);
                if (sanitized != null) {
                    wrapper.setCarriedItemStack(sanitized);
                }
            }
            wrapper.getSlots().ifPresent(slotsMap -> {
                boolean changed = false;
                Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> sanitizedMap = new HashMap<>(slotsMap.size());
                for (Map.Entry<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> entry : slotsMap.entrySet()) {
                    var item = entry.getValue();
                    if (item != null && !item.isEmpty()) {
                        var sanitized = stripWorthLore(item);
                        if (sanitized != null) {
                            sanitizedMap.put(entry.getKey(), sanitized);
                            changed = true;
                            continue;
                        }
                    }
                    sanitizedMap.put(entry.getKey(), item);
                }
                if (changed) {
                    wrapper.setSlots(sanitizedMap);
                }
            });
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            WrapperPlayClientCreativeInventoryAction wrapper = new WrapperPlayClientCreativeInventoryAction(event);
            var item = wrapper.getItemStack();
            if (item != null && !item.isEmpty()) {
                var sanitized = stripWorthLore(item);
                if (sanitized != null) {
                    wrapper.setItemStack(sanitized);
                }
            }
        }
    }

    public boolean isGuiSlot(@Nullable Player player, int windowId, int slot) {
        if (windowId <= 0 || player == null) return false;
        try {
            InventoryView view = player.getOpenInventory();
            if (view == null) return false;
            Inventory top = view.getTopInventory();
            if (slot < 0 || slot >= top.getSize()) return false;

            InventoryHolder holder = top.getHolder();
            if (holder instanceof WorthInventoryHolder) {
                return true;
            }
            if (holder instanceof SellInventoryHolder sellHolder) {
                if (sellHolder.isProgressGui()) return true;
                return !sellHolder.isSellSlot(slot);
            }
            if (!(holder instanceof org.bukkit.block.Container)
                && !(holder instanceof org.bukkit.block.DoubleChest)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public double calculateWorth(@NotNull com.github.retrooper.packetevents.protocol.item.ItemStack peItem) {
        var type = peItem.getType();
        if (type == null) return 0D;
        Object rawName = type.getName();
        if (rawName == null) return 0D;
        String clean = rawName.toString();
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            clean = clean.substring(colon + 1);
        }
        Material material;
        try {
            material = Material.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            material = Material.matchMaterial(clean);
        }
        if (material == null || material.isAir()) return 0D;

        boolean isSpecial = material == Material.ENCHANTED_BOOK
            || material == Material.POTION
            || material == Material.SPLASH_POTION
            || material == Material.LINGERING_POTION
            || ContainerHelper.isContainer(material);

        if (!isSpecial) {
            double unit = this.priceService.getUnitPrice(material);
            return unit > 0D ? unit * peItem.getAmount() : 0D;
        }

        try {
            org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
            if (bukkitItem != null && !bukkitItem.isEmpty()) {
                return this.priceService.getItemWorth(bukkitItem);
            }
        } catch (Throwable ignored) {
        }
        double unit = this.priceService.getUnitPrice(material);
        return unit > 0D ? unit * peItem.getAmount() : 0D;
    }

    @NotNull
    public List<Component> stripWorthLinesFromList(
        @NotNull List<Component> original,
        @NotNull WorthLoreConfig config
    ) {
        return config.stripWorthLines(original);
    }

    @Nullable
    public com.github.retrooper.packetevents.protocol.item.ItemStack appendWorthLore(
        @NotNull com.github.retrooper.packetevents.protocol.item.ItemStack peItem
    ) {
        if (peItem.isEmpty()) return null;
        WorthLoreConfig config = this.loreConfig;
        if (config == null || !config.enabled()) return null;

        double worth = this.calculateWorth(peItem);
        if (!(worth > 0D) || !Double.isFinite(worth)) return null;

        com.github.retrooper.packetevents.protocol.item.ItemStack copy = peItem.copy();

        Optional<ItemLore> currentLore = copy.getComponent(ComponentTypes.LORE);
        List<Component> lines;
        if (currentLore.isPresent()) {
            lines = this.stripWorthLinesFromList(currentLore.get().getLines(), config);
        } else {
            lines = new ArrayList<>(config.lines().size());
        }

        String standard = MoneyFormat.formatStandard(worth);
        String compact = MoneyFormat.formatCompact(worth);

        for (String template : config.lines()) {
            String rendered = template
                .replace("{CURRENCY_PREFIX}", "<green>$")
                .replace("{CURRENCY_SUFFIX}", "")
                .replace("{PRICE_FORMATTED}", compact)
                .replace("{price_formatted}", compact)
                .replace("{PRICE_COMPACT}", compact)
                .replace("{price_compact}", compact)
                .replace("{PRICE}", standard)
                .replace("{price}", standard)
                .replace("{WORTH_FORMATTED}", compact)
                .replace("{worth_formatted}", compact)
                .replace("{WORTH_COMPACT}", compact)
                .replace("{worth_compact}", compact)
                .replace("{WORTH}", standard)
                .replace("{worth}", standard);

            Component lineComp = MiniMessage.miniMessage()
                .deserialize(rendered)
                .decoration(TextDecoration.ITALIC, false);
            lines.add(lineComp);
        }

        copy.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        return copy;
    }

    @Nullable
    public com.github.retrooper.packetevents.protocol.item.ItemStack stripWorthLore(
        @NotNull com.github.retrooper.packetevents.protocol.item.ItemStack peItem
    ) {
        Optional<ItemLore> loreOpt = peItem.getComponent(ComponentTypes.LORE);
        if (loreOpt.isEmpty()) return null;
        ItemLore lore = loreOpt.get();
        List<Component> lines = lore.getLines();
        if (lines.isEmpty()) return null;

        WorthLoreConfig config = this.loreConfig;
        if (config == null) return null;

        boolean hasWorth = false;
        for (Component c : lines) {
            if (config.isWorthValueLine(c)) {
                hasWorth = true;
                break;
            }
        }
        if (!hasWorth) return null;

        List<Component> newLines = this.stripWorthLinesFromList(lines, config);
        com.github.retrooper.packetevents.protocol.item.ItemStack copy = peItem.copy();
        if (newLines.isEmpty()) {
            copy.unsetComponent(ComponentTypes.LORE);
        } else {
            copy.setComponent(ComponentTypes.LORE, new ItemLore(newLines));
        }
        return copy;
    }
}
