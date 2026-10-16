package sealmc.swe3tie.sealcore.modules.economy.gui.toolbar;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedToolbarTest {

    @Test
    void pagedToolbarConfigDefaults() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();

        assertEquals(Material.ARROW, config.previousButton().material());
        assertEquals(Material.ARROW, config.nextButton().material());
        assertEquals(Material.CAULDRON, config.sortButton().material());
        assertEquals(Material.HOPPER, config.filterButton().material());
        assertEquals(Material.ANVIL, config.refreshButton().material());
        assertEquals("<#9CC0D9>ɢɪá ᴛʀị ᴠậᴛ ᴘʜẩᴍ", config.refreshButton().name());
        assertEquals(List.of("<white>Nhấp để làm mới"), config.refreshButton().lore());
    }

    @Test
    void pagedToolbarConfigCustomRefreshButton() {
        PagedToolbarConfig config = PagedToolbarConfig.builder()
            .refreshButton(new PagedToolbarConfig.ItemDefinition(
                "refresh",
                Material.NETHER_STAR,
                "<gold>LÀM MỚI",
                List.of("<gray>Click to refresh list")
            ))
            .build();

        assertEquals(Material.NETHER_STAR, config.refreshButton().material());
        assertEquals("<gold>LÀM MỚI", config.refreshButton().name());
        assertEquals(List.of("<gray>Click to refresh list"), config.refreshButton().lore());
    }

    @Test
    void pagedToolbarConfigLoadsFromYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("toolbar.refresh.material", "COMPASS");
        yaml.set("toolbar.refresh.name", "<yellow>Refresh");
        yaml.set("toolbar.refresh.lore", List.of("<white>Reload entries"));

        PagedToolbarConfig config = PagedToolbarConfig.load(yaml.getConfigurationSection("toolbar"));

        assertEquals(Material.COMPASS, config.refreshButton().material());
        assertEquals("<yellow>Refresh", config.refreshButton().name());
        assertEquals(List.of("<white>Reload entries"), config.refreshButton().lore());
        // Defaults preserved for unspecified buttons
        assertEquals(Material.ARROW, config.previousButton().material());
        assertEquals(Material.CAULDRON, config.sortButton().material());
    }

    @Test
    void paginationBoundsAndClamping() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        List<Integer> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) items.add(i);

        PagedGuiToolbar<Integer> toolbar = new PagedGuiToolbar<>(
            config, items, List.of(), List.of(), (Runnable) null
        );

        // 100 items / 45 per page = 3 pages (45, 45, 10)
        assertEquals(3, toolbar.maxPage());
        assertEquals(1, toolbar.page());
        assertEquals(45, toolbar.pageItems().size());
        assertEquals(1, toolbar.pageItems().getFirst());
        assertEquals(45, toolbar.pageItems().getLast());

        toolbar.setPage(2);
        assertEquals(2, toolbar.page());
        assertEquals(45, toolbar.pageItems().size());
        assertEquals(46, toolbar.pageItems().getFirst());
        assertEquals(90, toolbar.pageItems().getLast());

        toolbar.setPage(3);
        assertEquals(3, toolbar.page());
        assertEquals(10, toolbar.pageItems().size());
        assertEquals(91, toolbar.pageItems().getFirst());
        assertEquals(100, toolbar.pageItems().getLast());

        // Clamp beyond max
        toolbar.setPage(99);
        assertEquals(3, toolbar.page());

        // Clamp below min
        toolbar.setPage(-5);
        assertEquals(1, toolbar.page());
    }

    @Test
    void cyclesSortOptions() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        List<SortOption<Integer>> sortOptions = List.of(
            new SortOption<>("asc", "Tăng dần", Integer::compareTo),
            new SortOption<>("desc", "Giảm dần", (a, b) -> Integer.compare(b, a))
        );

        PagedGuiToolbar<Integer> toolbar = new PagedGuiToolbar<>(
            config, List.of(3, 1, 4, 2), sortOptions, List.of(), (Runnable) null
        );

        assertEquals("asc", toolbar.currentSort().id());
        assertEquals(List.of(1, 2, 3, 4), toolbar.pageItems());

        toolbar.cycleSort();
        assertEquals("desc", toolbar.currentSort().id());
        assertEquals(List.of(4, 3, 2, 1), toolbar.pageItems());

        // Cycles back to beginning
        toolbar.cycleSort();
        assertEquals("asc", toolbar.currentSort().id());
        assertEquals(List.of(1, 2, 3, 4), toolbar.pageItems());
    }

    @Test
    void cyclesFilterOptions() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        List<FilterOption<Integer>> filterOptions = List.of(
            new FilterOption<>("all", "Tất cả", x -> true),
            new FilterOption<>("even", "Chẵn", x -> x % 2 == 0),
            new FilterOption<>("odd", "Lẻ", x -> x % 2 != 0)
        );

        PagedGuiToolbar<Integer> toolbar = new PagedGuiToolbar<>(
            config, List.of(1, 2, 3, 4, 5, 6), List.of(), filterOptions, (Runnable) null
        );

        assertEquals("all", toolbar.currentFilter().id());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), toolbar.pageItems());

        toolbar.cycleFilter();
        assertEquals("even", toolbar.currentFilter().id());
        assertEquals(List.of(2, 4, 6), toolbar.pageItems());

        toolbar.cycleFilter();
        assertEquals("odd", toolbar.currentFilter().id());
        assertEquals(List.of(1, 3, 5), toolbar.pageItems());

        toolbar.cycleFilter();
        assertEquals("all", toolbar.currentFilter().id());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), toolbar.pageItems());
    }

    @Test
    void refreshCallbackExecution() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        AtomicBoolean refreshed = new AtomicBoolean(false);

        PagedGuiToolbar<String> toolbar = new PagedGuiToolbar<>(
            config, List.of("item1"), List.of(), List.of(), (Runnable) () -> refreshed.set(true)
        );

        assertFalse(refreshed.get());
        toolbar.triggerRefresh(null);
        assertTrue(refreshed.get());
    }

    @Test
    void handleClickNavigationSlots() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        List<Integer> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) items.add(i);

        PagedGuiToolbar<Integer> toolbar = new PagedGuiToolbar<>(
            config, items, List.of(), List.of(), (Runnable) null
        );

        assertEquals(1, toolbar.page());

        // Slot 53 is next
        assertTrue(toolbar.handleClick(PagedGuiToolbar.SLOT_NEXT, null));
        assertEquals(2, toolbar.page());

        // Slot 45 is previous
        assertTrue(toolbar.handleClick(PagedGuiToolbar.SLOT_PREVIOUS, null));
        assertEquals(1, toolbar.page());

        // Non-toolbar slot returns false
        assertFalse(toolbar.handleClick(10, null));
    }

    @Test
    void disabledPaginationClampsToOnePageAndPreventsNavigation() {
        PagedToolbarConfig config = PagedToolbarConfig.builder().build();
        List<Integer> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) items.add(i);

        PagedGuiToolbar<Integer> toolbar = new PagedGuiToolbar<>(
            config, items, List.of(), List.of(), (Runnable) null, false
        );

        assertFalse(toolbar.paginationEnabled());
        assertEquals(1, toolbar.maxPage());
        assertEquals(1, toolbar.page());
        assertEquals(45, toolbar.pageItems().size());

        // Attempting to change page keeps page clamped at 1
        toolbar.setPage(2);
        assertEquals(1, toolbar.page());

        // Clicking next slot does not advance page
        assertTrue(toolbar.handleClick(PagedGuiToolbar.SLOT_NEXT, null));
        assertEquals(1, toolbar.page());
    }
}
