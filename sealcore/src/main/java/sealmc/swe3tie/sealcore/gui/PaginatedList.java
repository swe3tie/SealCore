package sealmc.swe3tie.sealcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A list that spans a rectangle of slots and pages through its entries.
 *
 * <p>Owns its navigation, so a feature module only supplies the entries and the
 * region it draws into.
 */
public final class PaginatedList extends GuiElement {

    private final List<GuiItem> entries;
    private final int regionTopLeft;
    private final int regionWidth;
    private final int regionHeight;
    private final BiConsumer<GuiContext, Integer> onSelect;

    private int page;

    public PaginatedList(
        List<GuiItem> entries,
        int regionTopLeft,
        int regionWidth,
        int regionHeight,
        BiConsumer<GuiContext, Integer> onSelect
    ) {
        super(regionTopLeft, entries.isEmpty() ? new GuiItem("stone") : entries.get(0));
        this.entries = new ArrayList<>(entries);
        this.regionTopLeft = regionTopLeft;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.onSelect = onSelect == null ? (context, index) -> { } : onSelect;
    }

    public PaginatedList(List<GuiItem> entries, int regionTopLeft, int regionWidth, int regionHeight) {
        this(entries, regionTopLeft, regionWidth, regionHeight, null);
    }

    public List<GuiItem> entries() {
        return entries;
    }

    public int page() {
        return page;
    }

    public int perPage() {
        return regionWidth * regionHeight;
    }

    public int pageCount() {
        int perPage = perPage();
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (entries.size() + perPage - 1) / perPage);
    }

    public int slotOf(int index) {
        return regionTopLeft + index;
    }

    public GuiItem itemAt(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public void renderInto(Map<Integer, GuiItem> buffer, GuiContext context) {
        int start = page * perPage();
        for (int offset = 0; offset < perPage(); offset++) {
            int target = slotOf(offset);
            GuiItem entry = itemAt(start + offset);
            if (entry == null) {
                buffer.remove(target);
            } else {
                buffer.put(target, entry);
            }
        }
    }

    public void nextPage() {
        if (page < pageCount() - 1) {
            page++;
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
        }
    }

    @Override
    public void onClick(GuiContext context, ClickInfo click) {
        int offset = click.slot() - regionTopLeft;
        if (offset < 0 || offset >= perPage()) {
            return;
        }
        onSelect.accept(context, page * perPage() + offset);
    }
}
