package sealmc.swe3tie.sealcore.util

/**
 * Slot arithmetic for inventory-sized screens.
 *
 * Row-major like Minecraft itself: slot = row * columns + column, row 0 is the
 * top row and column 0 is the left-most one.
 */
object Slots {

    const val COLUMNS = 9

    fun index(row: Int, column: Int, columns: Int = COLUMNS): Int = row * columns + column

    fun row(slot: Int, columns: Int = COLUMNS): Int = slot / columns

    fun column(slot: Int, columns: Int = COLUMNS): Int = slot % columns

    fun isValid(row: Int, column: Int, rows: Int, columns: Int = COLUMNS): Boolean =
        row in 0 until rows && column in 0 until columns

    fun isBorder(slot: Int, rows: Int, columns: Int = COLUMNS): Boolean {
        val row = row(slot, columns)
        val column = column(slot, columns)
        return row == 0 || row == rows - 1 || column == 0 || column == columns - 1
    }
}
