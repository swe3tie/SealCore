package sealmc.swe3tie.sealcore.util;

/**
 * Slot arithmetic for inventory-sized screens.
 *
 * <p>Row-major like Minecraft itself: slot = row * columns + column, row 0 is
 * the top row and column 0 is the left-most one.
 */
public final class Slots {

    public static final int COLUMNS = 9;

    private Slots() {
    }

    public static int index(int row, int column) {
        return index(row, column, COLUMNS);
    }

    public static int index(int row, int column, int columns) {
        return row * columns + column;
    }

    public static int row(int slot) {
        return row(slot, COLUMNS);
    }

    public static int row(int slot, int columns) {
        return slot / columns;
    }

    public static int column(int slot) {
        return column(slot, COLUMNS);
    }

    public static int column(int slot, int columns) {
        return slot % columns;
    }

    public static boolean isValid(int row, int column, int rows) {
        return isValid(row, column, rows, COLUMNS);
    }

    public static boolean isValid(int row, int column, int rows, int columns) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    public static boolean isBorder(int slot, int rows) {
        return isBorder(slot, rows, COLUMNS);
    }

    public static boolean isBorder(int slot, int rows, int columns) {
        int row = row(slot, columns);
        int column = column(slot, columns);
        return row == 0 || row == rows - 1 || column == 0 || column == columns - 1;
    }
}
