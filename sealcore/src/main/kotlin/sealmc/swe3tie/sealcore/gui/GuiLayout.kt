package sealmc.swe3tie.sealcore.gui

import sealmc.swe3tie.sealcore.util.Slots

/**
 * Grid geometry of a screen.
 *
 * [filler] is painted into every empty slot, which is how menus get a
 * consistent background without the feature modules placing it themselves.
 */
data class GuiLayout(
    val title: String,
    val rows: Int = 6,
    val columns: Int = Slots.COLUMNS,
    val filler: GuiItem? = null,
) {
    val size: Int get() = rows * columns

    init {
        require(rows in 1..6) { "rows must be 1..6, got $rows" }
        require(columns == Slots.COLUMNS) { "columns must be $Slots.COLUMNS, got $columns" }
    }

    fun slot(row: Int, column: Int): Int = Slots.index(row, column, columns)

    fun isInside(slot: Int): Boolean = slot in 0 until size
}
