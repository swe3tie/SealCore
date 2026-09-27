package sealmc.swe3tie.sealcore.gui;

import java.util.function.Consumer;

/** A slot that runs an action when clicked. */
public final class Button extends GuiElement {

    private final Consumer<GuiContext> action;

    public Button(int slot, GuiItem item, Consumer<GuiContext> action) {
        super(slot, item);
        this.action = action == null ? context -> { } : action;
    }

    public Button(int slot, GuiItem item) {
        this(slot, item, null);
    }

    @Override
    public void onClick(GuiContext context, ClickInfo click) {
        action.accept(context);
    }
}
