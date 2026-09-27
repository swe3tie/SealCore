package sealmc.swe3tie.sealcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A filled progress bar drawn as a row of segments. */
public final class ProgressBar extends GuiElement {

    private final int length;
    private final GuiItem filled;
    private final GuiItem empty;
    private final Function<GuiContext, Double> valueProvider;
    private final List<Integer> segments = new ArrayList<>();

    private double currentFill;

    public ProgressBar(int startSlot, int length, GuiItem filled, GuiItem empty, Function<GuiContext, Double> valueProvider) {
        super(startSlot, empty);
        this.length = length;
        this.filled = filled;
        this.empty = empty;
        this.valueProvider = valueProvider == null ? context -> 0.0 : valueProvider;
        for (int offset = 0; offset < length; offset++) {
            segments.add(startSlot + offset);
        }
    }

    public int length() {
        return length;
    }

    public double currentFill() {
        return currentFill;
    }

    @Override
    public GuiItem render(GuiContext context) {
        return null;
    }

    /** Renders into the shared buffer rather than a single slot. */
    public void renderInto(Map<Integer, GuiItem> buffer, GuiContext context) {
        double value = valueProvider.apply(context);
        double clamped = Math.max(0.0, Math.min(1.0, value));
        currentFill = clamped;
        int fillCount = (int) Math.round(clamped * length);
        for (int index = 0; index < segments.size(); index++) {
            buffer.put(segments.get(index), index < fillCount ? filled : empty);
        }
    }
}
