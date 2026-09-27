package sealmc.swe3tie.sealcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A packet driven menu.
 *
 * <p>Implementations declare their layout, fill the elements and return content
 * from {@link #render}. The manager takes care of opening, refreshing,
 * navigating and closing, and only the slots whose content actually changed are
 * sent.
 */
public abstract class GuiScreen {

    private final List<GuiElement> elements = new ArrayList<>();

    public abstract GuiLayout layout();

    public List<GuiElement> elements() {
        return elements;
    }

    /** Fills the slot buffer that will be sent to the client. */
    public void render(GuiContext context, Map<Integer, GuiItem> buffer) {
        for (GuiElement element : elements) {
            if (element instanceof ProgressBar bar) {
                bar.renderInto(buffer, context);
            } else if (element instanceof PaginatedList list) {
                list.renderInto(buffer, context);
            } else {
                GuiItem item = element.render(context);
                if (item != null) {
                    buffer.put(element.slot(), item);
                }
            }
        }
    }

    public void onOpen(GuiContext context) {
        // Nothing by default.
    }

    public void onClose(GuiContext context) {
        // Nothing by default.
    }

    public GuiElement elementAt(int slot) {
        for (GuiElement element : elements) {
            if (element.slot() == slot && element.visible()) {
                return element;
            }
        }
        return null;
    }

    // --- convenience builders -------------------------------------------------

    protected Button button(int slot, GuiItem item, Consumer<GuiContext> action) {
        Button button = new Button(slot, item, action);
        elements.add(button);
        return button;
    }

    protected Button button(int slot, GuiItem item) {
        return button(slot, item, null);
    }

    protected Icon icon(int slot, GuiItem item) {
        Icon icon = new Icon(slot, item);
        elements.add(icon);
        return icon;
    }

    protected Label label(int slot, GuiItem item) {
        Label label = new Label(slot, item);
        elements.add(label);
        return label;
    }

    protected ProgressBar progressBar(
        int startSlot,
        int length,
        GuiItem filled,
        GuiItem empty,
        Function<GuiContext, Double> value
    ) {
        ProgressBar bar = new ProgressBar(startSlot, length, filled, empty, value);
        elements.add(bar);
        return bar;
    }

    protected PaginatedList paginatedList(
        List<GuiItem> entries,
        int topLeft,
        int width,
        int height,
        BiConsumer<GuiContext, Integer> onSelect
    ) {
        PaginatedList list = new PaginatedList(entries, topLeft, width, height, onSelect);
        elements.add(list);
        return list;
    }

    protected PaginatedList paginatedList(List<GuiItem> entries, int topLeft, int width, int height) {
        return paginatedList(entries, topLeft, width, height, null);
    }
}
