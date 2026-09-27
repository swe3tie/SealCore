package sealmc.swe3tie.sealcore.gui;

/**
 * One rendered slot.
 *
 * <p>The render call happens on every refresh, so an element can show live data;
 * the {@link GuiSession} diffs the result against what the client already has
 * and only sends the slots that actually changed.
 */
public abstract class GuiElement {

    private int slot;
    private GuiItem item;
    private boolean visible = true;

    protected GuiElement(int slot, GuiItem item) {
        this.slot = slot;
        this.item = item;
    }

    public int slot() {
        return slot;
    }

    public void slot(int slot) {
        this.slot = slot;
    }

    protected GuiItem item() {
        return item;
    }

    public boolean visible() {
        return visible;
    }

    public void visible(boolean visible) {
        this.visible = visible;
    }

    public GuiElement update(GuiItem item) {
        this.item = item;
        return this;
    }

    public GuiItem render(GuiContext context) {
        return visible ? item : null;
    }

    public void onClick(GuiContext context, ClickInfo click) {
        // A plain element does nothing on a click.
    }
}
