package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.util.Slots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotsTest {

    @Test
    fun `indexes rows in the same order as the client`() {
        assertEquals(0, Slots.index(0, 0))
        assertEquals(8, Slots.index(0, 8))
        assertEquals(9, Slots.index(1, 0))
        assertEquals(10, Slots.index(1, 1))
        assertEquals(80, Slots.index(8, 8))
    }

    @Test
    fun `round trips a slot through row and column`() {
        for (row in 0 until 6) {
            for (column in 0 until 9) {
                val slot = Slots.index(row, column)
                assertEquals(row, Slots.row(slot))
                assertEquals(column, Slots.column(slot))
            }
        }
    }

    @Test
    fun `honours a custom column count`() {
        assertEquals(7, Slots.index(1, 3, columns = 4))
        assertEquals(2, Slots.row(10, columns = 4))
        assertEquals(2, Slots.column(10, columns = 4))
    }

    @Test
    fun `rejects slots outside the rectangle`() {
        assertTrue(Slots.isValid(5, 8, rows = 6))
        assertFalse(Slots.isValid(6, 0, rows = 6))
        assertFalse(Slots.isValid(0, 9, rows = 6))
        assertFalse(Slots.isValid(-1, 0, rows = 6))
    }

    @Test
    fun `marks every edge slot of a frame as border`() {
        assertTrue(Slots.isBorder(0, rows = 6))
        assertTrue(Slots.isBorder(53, rows = 6))
        assertTrue(Slots.isBorder(9, rows = 6))
        assertTrue(Slots.isBorder(45, rows = 6))
        assertFalse(Slots.isBorder(11, rows = 6))
        assertFalse(Slots.isBorder(10, rows = 6))
        assertFalse(Slots.isBorder(40, rows = 6))
    }

    @Test
    fun `a single row is all border`() {
        (0 until 9).forEach { assertTrue(Slots.isBorder(it, rows = 1)) }
    }
}
