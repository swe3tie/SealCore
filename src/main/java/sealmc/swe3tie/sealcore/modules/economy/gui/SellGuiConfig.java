package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.lang.LangService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, validated configuration for the {@code /sell} inventory. */
public final class SellGuiConfig {

    private final String title;
    private final int rows;
    private final Set<Integer> sellSlots;
    private final List<ItemDefinition> items;

    private SellGuiConfig(String title, int rows, Set<Integer> sellSlots, List<ItemDefinition> items) {
        this.title = title;
        this.rows = rows;
        this.sellSlots = sellSlots;
        this.items = items;
    }

    @NotNull
    public static SellGuiConfig load(@NotNull YamlConfiguration config) throws ConfigException {
        String title = config.getString("title", "<green>Sell items</green>");
        if (title == null || title.isBlank()) {
            throw new ConfigException("GUI sell title cannot be empty");
        }

        int rows = config.getInt("rows", 5);
        if (rows < 1 || rows > 6) {
            throw new ConfigException("GUI sell rows must be between 1 and 6");
        }
        int size = rows * 9;

        Set<Integer> sellSlots = readSlots(config.getIntegerList("sell-slots"), size, "sell-slots");
        List<ItemDefinition> items = new ArrayList<>();
        Set<Integer> occupied = new LinkedHashSet<>();
        ConfigurationSection itemSection = config.getConfigurationSection("items");
        if (itemSection != null) {
            for (String id : itemSection.getKeys(false)) {
                ConfigurationSection section = itemSection.getConfigurationSection(id);
                if (section == null) {
                    throw new ConfigException("GUI sell item '" + id + "' must be a section");
                }

                String materialName = section.getString("material");
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null || material.isAir()) {
                    throw new ConfigException("GUI sell item '" + id + "' has unknown material: " + materialName);
                }

                Set<Integer> slots = readSlots(section.getIntegerList("slots"), size, "items." + id + ".slots");
                for (int slot : slots) {
                    if (sellSlots.contains(slot)) {
                        throw new ConfigException("GUI sell item '" + id + "' overlaps sell slot " + slot);
                    }
                    if (!occupied.add(slot)) {
                        throw new ConfigException("GUI sell items overlap at slot " + slot);
                    }
                }

                String name = section.getString("name", "");
                List<String> lore = List.copyOf(section.getStringList("lore"));
                items.add(new ItemDefinition(id, material, name == null ? "" : name, lore, slots));
            }
        }

        return new SellGuiConfig(title, rows, Set.copyOf(sellSlots), List.copyOf(items));
    }

    @NotNull
    public String title() {
        return this.title;
    }

    public int rows() {
        return this.rows;
    }

    public int size() {
        return this.rows * 9;
    }

    @NotNull
    public Set<Integer> sellSlots() {
        return this.sellSlots;
    }

    @NotNull
    public List<ItemDefinition> items() {
        return this.items;
    }

    private static Set<Integer> readSlots(List<Integer> values, int size, String path) throws ConfigException {
        Set<Integer> slots = new LinkedHashSet<>();
        for (Integer slot : values) {
            if (slot == null || slot < 0 || slot >= size) {
                throw new ConfigException("GUI sell slot out of bounds in " + path + ": " + slot);
            }
            if (!slots.add(slot)) {
                throw new ConfigException("GUI sell slot is listed more than once in " + path + ": " + slot);
            }
        }
        return Collections.unmodifiableSet(slots);
    }

    public record ItemDefinition(
        @NotNull String id,
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore,
        @NotNull Set<Integer> slots
    ) {
        public ItemDefinition {
            lore = List.copyOf(lore);
            slots = Set.copyOf(slots);
        }

        /** Builds one configured display item for a viewer. */
        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang) {
            ItemStack item = new ItemStack(this.material);
            ItemMeta meta = item.getItemMeta();
            if (!this.name.isEmpty()) meta.displayName(lang.parse(this.name, player));
            if (!this.lore.isEmpty()) {
                List<Component> rendered = new ArrayList<>(this.lore.size());
                for (String line : this.lore) rendered.add(lang.parse(line, player));
                meta.lore(rendered);
            }
            item.setItemMeta(meta);
            return item;
        }
    }
}
