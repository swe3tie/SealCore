package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.gui.ClickInfo;
import sealmc.swe3tie.sealcore.gui.GuiContext;
import sealmc.swe3tie.sealcore.gui.GuiItem;
import sealmc.swe3tie.sealcore.gui.PaginatedList;
import sealmc.swe3tie.sealcore.gui.ProgressBar;

class PaginatedListTest {

    private final GuiContext context = TestFixtures.fakeContext();

    private static List<GuiItem> items(int count) {
        List<GuiItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new GuiItem("stone", i + 1));
        }
        return items;
    }

    @Test
    void derivesPageCountFromTheRegionSize() {
        var list = new PaginatedList(items(10), 9, 7, 2);
        assertEquals(14, list.perPage());
        assertEquals(1, list.pageCount());
    }

    @Test
    void roundsAPartialLastPageUp() {
        assertEquals(2, new PaginatedList(items(15), 0, 7, 2).pageCount());
    }

    @Test
    void anEmptyListStillHasOnePage() {
        assertEquals(1, new PaginatedList(items(0), 0, 7, 2).pageCount());
    }

    @Test
    void pagingStopsAtBothEnds() {
        var list = new PaginatedList(items(20), 0, 7, 2);
        list.previousPage();
        assertEquals(0, list.page());

        list.nextPage();
        list.nextPage();
        assertEquals(1, list.page());
        list.nextPage();
        assertEquals(1, list.page());
    }

    @Test
    void rendersOnlyTheEntriesOfTheCurrentPage() {
        var list = new PaginatedList(items(16), 10, 7, 2);
        Map<Integer, GuiItem> buffer = new LinkedHashMap<>();

        list.renderInto(buffer, context);
        assertEquals(14, buffer.size());
        assertEquals(1, buffer.get(10).amount());
        assertNull(buffer.get(24));

        list.nextPage();
        list.renderInto(buffer, context);
        // Only the region is touched, so the first page's slots below it stay absent.
        assertEquals(2, buffer.size());
        assertEquals(15, buffer.get(10).amount());
        assertEquals(16, buffer.get(11).amount());
        assertNull(buffer.get(12));
    }

    @Test
    void clearsLeftoversWhenALaterPageIsShorter() {
        var list = new PaginatedList(items(16), 0, 7, 2);
        Map<Integer, GuiItem> buffer = new LinkedHashMap<>();
        list.renderInto(buffer, context);
        list.nextPage();
        list.renderInto(buffer, context);
        assertNull(buffer.get(4));
        assertEquals(2, buffer.size());
    }

    @Test
    void aClickReportsTheAbsoluteEntryIndex() {
        List<Integer> seen = new ArrayList<>();
        var list = new PaginatedList(items(30), 18, 7, 2, (ctx, index) -> seen.add(index));
        list.nextPage();
        list.onClick(context, new ClickInfo(19, 0, ClickInfo.ClickMode.NORMAL));
        list.onClick(context, new ClickInfo(10, 0, ClickInfo.ClickMode.NORMAL));
        assertEquals(List.of(15), seen);
    }

    @Test
    void aProgressBarFillsLeftToRightAndClampsOutOfRangeValues() {
        var filled = new GuiItem("lime_stained_glass_pane");
        var empty = new GuiItem("gray_stained_glass_pane");
        Map<Integer, GuiItem> buffer = new LinkedHashMap<>();
        var bar = new ProgressBar(5, 4, filled, empty, ctx -> 0.5);

        bar.renderInto(buffer, context);
        assertEquals(0.5, bar.currentFill());
        assertEquals(2, countIdentity(buffer, filled));

        new ProgressBar(5, 4, filled, empty, ctx -> 4.0).renderInto(buffer, context);
        assertEquals(4, countIdentity(buffer, filled));

        new ProgressBar(5, 4, filled, empty, ctx -> -1.0).renderInto(buffer, context);
        assertEquals(0, countIdentity(buffer, filled));
    }

    private static long countIdentity(Map<Integer, GuiItem> buffer, GuiItem item) {
        return buffer.values().stream().filter(value -> value == item).count();
    }
}
