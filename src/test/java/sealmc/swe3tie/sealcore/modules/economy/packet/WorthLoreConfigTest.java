package sealmc.swe3tie.sealcore.modules.economy.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorthLoreConfigTest {

    @Test
    void loadsDefaultsWhenNull() {
        WorthLoreConfig config = WorthLoreConfig.load(null);
        assertTrue(config.enabled());
        assertEquals(1, config.lines().size());
        assertEquals("<gray>Giá trị: <green>${WORTH_FORMATTED}", config.lines().getFirst());

        Component comp = MiniMessage.miniMessage().deserialize("<gray>Giá trị: <green>100k");
        assertTrue(config.matchesWorthLore(comp));
        assertTrue(config.isWorthValueLine(comp));

        Component other = MiniMessage.miniMessage().deserialize("<white>Sharpness V");
        assertFalse(config.matchesWorthLore(other));
        assertFalse(config.isWorthValueLine(other));
    }

    @Test
    void loadsCustomConfigFromYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worth-lore.enabled", false);
        yaml.set("worth-lore.lines", List.of(
            "<dark_gray>----------------",
            "<gold>Giá bán: <yellow>{WORTH}",
            "<dark_gray>----------------"
        ));

        WorthLoreConfig config = WorthLoreConfig.load(yaml.getConfigurationSection("worth-lore"));
        assertFalse(config.enabled());
        assertEquals(3, config.lines().size());

        Component priceLine = MiniMessage.miniMessage().deserialize("<gold>Giá bán: <yellow>500,000");
        assertTrue(config.matchesWorthLore(priceLine));
        assertTrue(config.isWorthValueLine(priceLine));

        Component dividerLine = MiniMessage.miniMessage().deserialize("<dark_gray>----------------");
        assertTrue(config.matchesWorthLore(dividerLine));
        assertFalse(config.isWorthValueLine(dividerLine));

        Component itemLore = MiniMessage.miniMessage().deserialize("<gray>Độ bền: 100/100");
        assertFalse(config.matchesWorthLore(itemLore));
        assertFalse(config.isWorthValueLine(itemLore));
    }

    @Test
    void supportsSingleFormatString() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worth-lore.enabled", true);
        yaml.set("worth-lore.format", "<yellow>Price: <green>${worth_formatted}");

        WorthLoreConfig config = WorthLoreConfig.load(yaml.getConfigurationSection("worth-lore"));
        assertTrue(config.enabled());
        assertEquals(1, config.lines().size());

        Component comp = MiniMessage.miniMessage().deserialize("<yellow>Price: <green>$1.5M");
        assertTrue(config.matchesWorthLore(comp));
        assertTrue(config.isWorthValueLine(comp));
    }

    @Test
    void stripsTrailingWorthLoreFromEndOfList() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worth-lore.enabled", true);
        yaml.set("worth-lore.lines", List.of(
            "<dark_gray>----------------",
            "<gold>Giá bán: <yellow>{WORTH}",
            "<dark_gray>----------------"
        ));
        WorthLoreConfig config = WorthLoreConfig.load(yaml.getConfigurationSection("worth-lore"));

        Component customLore1 = MiniMessage.miniMessage().deserialize("<gray>Sharpness V");
        Component customLore2 = MiniMessage.miniMessage().deserialize("<gray>Unbreaking III");
        Component div1 = MiniMessage.miniMessage().deserialize("<dark_gray>----------------");
        Component price = MiniMessage.miniMessage().deserialize("<gold>Giá bán: <yellow>500,000");
        Component div2 = MiniMessage.miniMessage().deserialize("<dark_gray>----------------");

        List<Component> original = List.of(customLore1, customLore2, div1, price, div2);
        List<Component> stripped = config.stripWorthLines(original);

        assertEquals(2, stripped.size());
        assertEquals(customLore1, stripped.get(0));
        assertEquals(customLore2, stripped.get(1));
    }

    @Test
    void stopsStrippingImmediatelyWhenEncounteringNonWorthLine() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worth-lore.enabled", true);
        yaml.set("worth-lore.lines", List.of(
            "<dark_gray>----------------",
            "<gold>Giá bán: <yellow>{WORTH}",
            "<dark_gray>----------------"
        ));
        WorthLoreConfig config = WorthLoreConfig.load(yaml.getConfigurationSection("worth-lore"));

        Component customLore1 = MiniMessage.miniMessage().deserialize("<gray>Custom Item");
        Component price = MiniMessage.miniMessage().deserialize("<gold>Giá bán: <yellow>500,000");
        Component durability = MiniMessage.miniMessage().deserialize("<gray>Độ bền: 100/100");

        List<Component> original = List.of(customLore1, price, durability);
        List<Component> stripped = config.stripWorthLines(original);

        assertEquals(3, stripped.size());
        assertEquals(durability, stripped.get(2));
    }

    @Test
    void stripsEntireListWhenAllLinesAreWorthLore() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worth-lore.enabled", true);
        yaml.set("worth-lore.lines", List.of(
            "<dark_gray>----------------",
            "<gold>Giá bán: <yellow>{WORTH}",
            "<dark_gray>----------------"
        ));
        WorthLoreConfig config = WorthLoreConfig.load(yaml.getConfigurationSection("worth-lore"));

        Component div1 = MiniMessage.miniMessage().deserialize("<dark_gray>----------------");
        Component price = MiniMessage.miniMessage().deserialize("<gold>Giá bán: <yellow>500,000");
        Component div2 = MiniMessage.miniMessage().deserialize("<dark_gray>----------------");

        List<Component> original = List.of(div1, price, div2);
        List<Component> stripped = config.stripWorthLines(original);

        assertTrue(stripped.isEmpty());
    }

    @Test
    void preservesLoreWhenNoWorthLinesPresent() {
        WorthLoreConfig config = WorthLoreConfig.load(null);

        Component customLore1 = MiniMessage.miniMessage().deserialize("<gray>Sharpness V");
        Component customLore2 = MiniMessage.miniMessage().deserialize("<gray>Unbreaking III");

        List<Component> original = List.of(customLore1, customLore2);
        List<Component> stripped = config.stripWorthLines(original);

        assertEquals(2, stripped.size());
        assertEquals(customLore1, stripped.get(0));
        assertEquals(customLore2, stripped.get(1));
    }
}
