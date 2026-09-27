package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.util.Slots;

class SlotsTest {

    @Test
    void indexesRowsInTheSameOrderAsTheClient() {
        assertEquals(0, Slots.index(0, 0));
        assertEquals(8, Slots.index(0, 8));
        assertEquals(9, Slots.index(1, 0));
        assertEquals(10, Slots.index(1, 1));
        assertEquals(80, Slots.index(8, 8));
    }

    @Test
    void roundTripsASlotThroughRowAndColumn() {
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = Slots.index(row, column);
                assertEquals(row, Slots.row(slot));
                assertEquals(column, Slots.column(slot));
            }
        }
    }

    @Test
    void honoursACustomColumnCount() {
        assertEquals(7, Slots.index(1, 3, 4));
        assertEquals(2, Slots.row(10, 4));
        assertEquals(2, Slots.column(10, 4));
    }

    @Test
    void rejectsSlotsOutsideTheRectangle() {
        assertTrue(Slots.isValid(5, 8, 6));
        assertFalse(Slots.isValid(6, 0, 6));
        assertFalse(Slots.isValid(0, 9, 6));
        assertFalse(Slots.isValid(-1, 0, 6));
    }

    @Test
    void marksEveryEdgeSlotOfAFrameAsBorder() {
        assertTrue(Slots.isBorder(0, 6));
        assertTrue(Slots.isBorder(53, 6));
        assertTrue(Slots.isBorder(9, 6));
        assertTrue(Slots.isBorder(45, 6));
        assertFalse(Slots.isBorder(11, 6));
        assertFalse(Slots.isBorder(10, 6));
        assertFalse(Slots.isBorder(40, 6));
    }

    @Test
    void aSingleRowIsAllBorder() {
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(Slots.isBorder(slot, 1));
        }
    }
}
