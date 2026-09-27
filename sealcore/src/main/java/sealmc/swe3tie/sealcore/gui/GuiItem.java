package sealmc.swe3tie.sealcore.gui;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Description of an item as rendered in a packet driven screen.
 *
 * <p>Deliberately not a Bukkit {@code ItemStack}: packet rendering has to work
 * for a client that never saw the item, so only the fields a menu can express
 * are kept here and the encoding is left to PacketEvents.
 */
public record GuiItem(
    String material,
    int amount,
    Component name,
    List<Component> lore,
    boolean glow,
    boolean hideTooltip,
    String profile
) {

    public GuiItem(String material) {
        this(material, 1, null, List.of(), false, false, null);
    }

    public GuiItem(String material, int amount) {
        this(material, amount, null, List.of(), false, false, null);
    }

    public GuiItem(String material, Component name) {
        this(material, 1, name, List.of(), false, false, null);
    }

    public GuiItem(String material, int amount, Component name, List<Component> lore) {
        this(material, amount, name, lore, false, false, null);
    }

    public boolean hasLore() {
        return !lore.isEmpty();
    }

    public static GuiItem of(String material, Component name, Component... lore) {
        return new GuiItem(material, 1, name, Arrays.asList(lore), false, false, null);
    }
}
