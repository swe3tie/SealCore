package sealmc.swe3tie.sealcore.gui;

import sealmc.swe3tie.sealcore.util.Slots;

/**
 * Grid geometry of a screen.
 *
 * <p>The filler is painted into every empty slot, which is how menus get a
 * consistent background without the feature modules placing it themselves.
 */
public final class GuiLayout {

    private final String title;
    private final int rows;
    private final int columns;
    private final GuiItem filler;

    public GuiLayout(String title, int rows, int columns, GuiItem filler) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, got " + rows);
        }
        if (columns != Slots.COLUMNS) {
            throw new IllegalArgumentException("columns must be " + Slots.COLUMNS + ", got " + columns);
        }
        this.title = title;
        this.rows = rows;
        this.columns = columns;
        this.filler = filler;
    }

    public GuiLayout(String title) {
        this(title, 6, Slots.COLUMNS, null);
    }

    public GuiLayout(String title, int rows) {
        this(title, rows, Slots.COLUMNS, null);
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public GuiItem filler() {
        return filler;
    }

    public int size() {
        return rows * columns;
    }

    public int slot(int row, int column) {
        return Slots.index(row, column, columns);
    }

    public boolean isInside(int slot) {
        return slot >= 0 && slot < size();
    }
}
