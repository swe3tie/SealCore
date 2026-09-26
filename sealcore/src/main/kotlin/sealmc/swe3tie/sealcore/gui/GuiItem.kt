package sealmc.swe3tie.sealcore.gui

import net.kyori.adventure.text.Component

/**
 * Description of an item as rendered in a packet driven screen.
 *
 * Deliberately not a Bukkit `ItemStack`: packet rendering has to work for a
 * client that never saw the item, so only the fields a menu can express are
 * kept here and the encoding is left to PacketEvents.
 */
data class GuiItem(
    val material: String,
    val amount: Int = 1,
    val name: Component? = null,
    val lore: List<Component> = emptyList(),
    val glow: Boolean = false,
    val hideTooltip: Boolean = false,
    val profile: String? = null,
) {
    val hasLore: Boolean get() = lore.isNotEmpty()

    companion object {
        fun of(material: String, name: Component? = null, vararg lore: Component): GuiItem =
            GuiItem(material = material, name = name, lore = lore.toList())
    }
}
