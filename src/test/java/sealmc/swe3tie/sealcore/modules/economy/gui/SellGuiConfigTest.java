package sealmc.swe3tie.sealcore.modules.economy.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SellGuiConfigTest {

    @Test
    void loadsBundledSplitConfigurations() throws Exception {
        YamlConfiguration sellGui = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/gui/sell-gui.yml"))) {
            sellGui.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        YamlConfiguration multiplierGui = new YamlConfiguration();
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/modules/economy/gui/sell-multiplier.yml"))) {
            multiplierGui.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        SellGuiConfig config = SellGuiConfig.load(sellGui, multiplierGui);
        assertEquals(5, config.rows());
        assertEquals(45, config.size());
        assertEquals(36, config.sellSlots().size());
        assertEquals(9, config.categoryButtons().size());

        for (SellCategory category : SellCategory.values()) {
            assertNotNull(config.categoryButton(category));
        }

        SellGuiConfig.ProgressConfig progress = config.progress();
        assertEquals(6, progress.rows());
        assertEquals(54, progress.size());
        assertEquals(2, progress.pageCount());
        assertEquals(20, progress.slotsForPage(1).size());
        assertEquals(20, progress.slotsForPage(2).size());
        assertEquals(9, progress.slotsForPage(2).getFirst()); // 1-based slot 10 -> 0-based index 9
        assertEquals(21, progress.allProgressSlots().size());
        assertNotNull(progress.inProgress());
        assertNotNull(progress.completed());
        assertNotNull(progress.incomplete());
    }
}
