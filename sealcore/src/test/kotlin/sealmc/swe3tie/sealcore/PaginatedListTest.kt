package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.gui.ClickInfo
import sealmc.swe3tie.sealcore.gui.GuiItem
import sealmc.swe3tie.sealcore.gui.PaginatedList
import sealmc.swe3tie.sealcore.gui.ProgressBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaginatedListTest {

    private val context = fakeContext()

    private fun items(count: Int): MutableList<GuiItem> =
        MutableList(count) { GuiItem("stone", amount = it + 1) }

    @Test
    fun `derives page count from the region size`() {
        val list = PaginatedList(items(10), regionTopLeft = 9, regionWidth = 7, regionHeight = 2)
        assertEquals(14, list.perPage)
        assertEquals(1, list.pageCount)
    }

    @Test
    fun `rounds a partial last page up`() {
        val list = PaginatedList(items(15), regionTopLeft = 0, regionWidth = 7, regionHeight = 2)
        assertEquals(2, list.pageCount)
    }

    @Test
    fun `an empty list still has one page`() {
        val list = PaginatedList(items(0), regionTopLeft = 0, regionWidth = 7, regionHeight = 2)
        assertEquals(1, list.pageCount)
    }

    @Test
    fun `paging stops at both ends`() {
        val list = PaginatedList(items(20), regionTopLeft = 0, regionWidth = 7, regionHeight = 2)
        list.previousPage()
        assertEquals(0, list.page)

        list.nextPage()
        list.nextPage()
        assertEquals(1, list.page)
        list.nextPage()
        assertEquals(1, list.page)
    }

    @Test
    fun `renders only the entries of the current page`() {
        val list = PaginatedList(items(16), regionTopLeft = 10, regionWidth = 7, regionHeight = 2)
        val buffer = mutableMapOf<Int, GuiItem>()

        list.renderInto(buffer, context)
        assertEquals(14, buffer.size)
        assertEquals(1, buffer[10]?.amount)
        assertNull(buffer[24])

        list.nextPage()
        list.renderInto(buffer, context)
        // Only the region is touched, so the first page's slots below it stay absent.
        assertEquals(2, buffer.size)
        assertEquals(15, buffer[10]?.amount)
        assertEquals(16, buffer[11]?.amount)
        assertNull(buffer[12])
    }

    @Test
    fun `clears leftovers when a later page is shorter`() {
        val list = PaginatedList(items(16), regionTopLeft = 0, regionWidth = 7, regionHeight = 2)
        val buffer = mutableMapOf<Int, GuiItem>()
        list.renderInto(buffer, context)
        list.nextPage()
        list.renderInto(buffer, context)
        assertNull(buffer[4])
        assertEquals(2, buffer.size)
    }

    @Test
    fun `a click reports the absolute entry index`() {
        val seen = mutableListOf<Int>()
        val list = PaginatedList(items(30), regionTopLeft = 18, regionWidth = 7, regionHeight = 2) { _, index ->
            seen += index
        }
        list.nextPage()
        list.onClick(context, ClickInfo(slot = 19, button = 0, mode = ClickInfo.ClickMode.NORMAL))
        list.onClick(context, ClickInfo(slot = 10, button = 0, mode = ClickInfo.ClickMode.NORMAL))
        assertEquals(listOf(15), seen)
    }

    @Test
    fun `a progress bar fills left to right and clamps out of range values`() {
        val filled = GuiItem("lime_stained_glass_pane")
        val empty = GuiItem("gray_stained_glass_pane")
        val buffer = mutableMapOf<Int, GuiItem>()
        val bar = ProgressBar(startSlot = 5, length = 4, filled = filled, empty = empty) { 0.5 }

        bar.renderInto(buffer, context)
        assertEquals(0.5, bar.currentFill)
        assertEquals(2, buffer.count { it.value === filled })

        ProgressBar(5, 4, filled, empty) { 4.0 }.renderInto(buffer, context)
        assertEquals(4, buffer.count { it.value === filled })

        ProgressBar(5, 4, filled, empty) { -1.0 }.renderInto(buffer, context)
        assertEquals(0, buffer.count { it.value === filled })
    }
}
