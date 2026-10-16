package sealmc.swe3tie.sealcore.modules.economy.gui.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.gui.GuiConfigHelper;

import java.util.List;

/**
 * Configuration for the 3-row quantity selection menu loaded from {@code shop-amount.yml}.
 */
public record ShopAmountGuiConfig(
    @NotNull String title,
    int rows,
    @NotNull GuiConfigHelper.ItemDefinition buyingItem,
    @NotNull GuiConfigHelper.ItemDefinition resetBtn,
    @NotNull GuiConfigHelper.ItemDefinition sub10Btn,
    @NotNull GuiConfigHelper.ItemDefinition sub1Btn,
    @NotNull GuiConfigHelper.ItemDefinition add1Btn,
    @NotNull GuiConfigHelper.ItemDefinition add10Btn,
    @NotNull GuiConfigHelper.ItemDefinition maxBtn,
    @NotNull GuiConfigHelper.ItemDefinition cancelBtn,
    @NotNull GuiConfigHelper.ItemDefinition confirmBtn
) {

    public int size() {
        return this.rows * 9;
    }

    public int itemSlot() {
        return this.buyingItem.slot();
    }

    public int resetSlot() {
        return this.resetBtn.slot();
    }

    public int sub10Slot() {
        return this.sub10Btn.slot();
    }

    public int sub1Slot() {
        return this.sub1Btn.slot();
    }

    public int add1Slot() {
        return this.add1Btn.slot();
    }

    public int add10Slot() {
        return this.add10Btn.slot();
    }

    public int maxSlot() {
        return this.maxBtn.slot();
    }

    public int cancelSlot() {
        return this.cancelBtn.slot();
    }

    public int confirmSlot() {
        return this.confirmBtn.slot();
    }

    @NotNull
    public static ShopAmountGuiConfig load(@Nullable YamlConfiguration config) {
        String title = GuiConfigHelper.readTitle(config, "<dark_gray>ᴄʜọɴ ѕố ʟượɴɢ");
        int rows = GuiConfigHelper.readRows(config, 3);

        ConfigurationSection buyingSec = config != null ? config.getConfigurationSection("buying-item") : null;
        int defaultItemSlot = 13;
        if (config != null && config.contains("item-slot")) {
            defaultItemSlot = Math.max(0, config.getInt("item-slot") - 1);
        } else if (config != null && config.isConfigurationSection("slots")) {
            defaultItemSlot = Math.max(0, config.getConfigurationSection("slots").getInt("item", 14) - 1);
        }

        GuiConfigHelper.ItemDefinition buyingItem = GuiConfigHelper.readItemDefinition(
            buyingSec, defaultItemSlot, Material.AIR, "", List.of()
        );

        ConfigurationSection btnSec = config != null && config.isConfigurationSection("buttons")
            ? config.getConfigurationSection("buttons")
            : config;

        ConfigurationSection slotsSec = config != null ? config.getConfigurationSection("slots") : null;

        GuiConfigHelper.ItemDefinition reset = GuiConfigHelper.readItemDefinition(
            btnSec, "reset", Math.max(0, readSlot(slotsSec, "reset", 10) - 1),
            Material.RED_STAINED_GLASS_PANE, "<red>đặᴛ ʟạɪ 1", List.of()
        );
        GuiConfigHelper.ItemDefinition sub10 = GuiConfigHelper.readItemDefinition(
            btnSec, "sub10", Math.max(0, readSlot(slotsSec, "sub10", 11) - 1),
            Material.RED_STAINED_GLASS_PANE, "<red>-10", List.of()
        );
        GuiConfigHelper.ItemDefinition sub1 = GuiConfigHelper.readItemDefinition(
            btnSec, "sub1", Math.max(0, readSlot(slotsSec, "sub1", 12) - 1),
            Material.RED_STAINED_GLASS_PANE, "<red>-1", List.of()
        );
        GuiConfigHelper.ItemDefinition add1 = GuiConfigHelper.readItemDefinition(
            btnSec, "add1", Math.max(0, readSlot(slotsSec, "add1", 16) - 1),
            Material.LIME_STAINED_GLASS_PANE, "<green>+1", List.of()
        );
        GuiConfigHelper.ItemDefinition add10 = GuiConfigHelper.readItemDefinition(
            btnSec, "add10", Math.max(0, readSlot(slotsSec, "add10", 17) - 1),
            Material.LIME_STAINED_GLASS_PANE, "<green>+10", List.of()
        );
        GuiConfigHelper.ItemDefinition max = GuiConfigHelper.readItemDefinition(
            btnSec, "max", Math.max(0, readSlot(slotsSec, "max", 18) - 1),
            Material.LIME_STAINED_GLASS_PANE, "<green>ᴛốɪ đᴀ ({MAX_VALUE})", List.of()
        );
        GuiConfigHelper.ItemDefinition cancel = GuiConfigHelper.readItemDefinition(
            btnSec, "cancel", Math.max(0, readSlot(slotsSec, "cancel", 22) - 1),
            Material.RED_STAINED_GLASS_PANE, "<red>ʜᴜỷ ʙỏ", List.of("<white>Nhấp để huỷ bỏ")
        );
        GuiConfigHelper.ItemDefinition confirm = GuiConfigHelper.readItemDefinition(
            btnSec, "confirm", Math.max(0, readSlot(slotsSec, "confirm", 25) - 1),
            Material.LIME_STAINED_GLASS_PANE, "<green>хáᴄ ɴʜậɴ ᴍᴜᴀ",
            List.of("<accent>{ITEM} <white>với giá {CURRENCY_PREFIX}{PRICE_FORMATTED}{CURRENCY_SUFFIX}")
        );

        return new ShopAmountGuiConfig(
            title, rows, buyingItem,
            reset, sub10, sub1, add1, add10, max, cancel, confirm
        );
    }

    private static int readSlot(@Nullable ConfigurationSection sec, @NotNull String key, int def) {
        if (sec == null) return def;
        return sec.getInt(key, def);
    }
}
