package app.elauncher.ui

import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [applyStacking]: what each of the four stacking menu actions does to a page's zIndex values
 * (plan 005 Step 7).
 *
 * The rules are only visible on a device as "which of two overlapping items is on top", which takes
 * a hand-built overlapping layout to see at all - so the ordering model itself is asserted here, on
 * plain data, exactly as [cycleSelection]'s is in TapCycleTest.
 */
class StackingTest {

    private fun item(zIndex: Int) = GridItem(
        type = GridItemType.APP_LIST,
        col = 0,
        row = 0,
        spanX = 2,
        spanY = 2,
        zIndex = zIndex,
    )

    /** The page's items in paint order, bottom first - the order HomeGridView adds its children in. */
    private fun paintOrder(items: List<GridItem>): List<GridItem> = items.sortedBy { it.zIndex }

    private fun assertPaintOrder(expected: List<GridItem>, items: List<GridItem>) {
        val actual = paintOrder(items)
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, expectedItem ->
            assertTrue(
                "position $index: expected ${expected.map { it.zIndex }}, was ${actual.map { it.zIndex }}",
                actual[index] === expectedItem,
            )
        }
    }

    @Test
    fun `bring to front puts the item above every other`() {
        val bottom = item(0)
        val middle = item(5)
        val top = item(9)
        val items = listOf(bottom, middle, top)

        assertTrue(applyStacking(bottom, items, StackingAction.BRING_TO_FRONT))

        assertEquals(10, bottom.zIndex)
        assertPaintOrder(listOf(middle, top, bottom), items)
    }

    @Test
    fun `bring to front is a no-op for the item already on top`() {
        val bottom = item(0)
        val top = item(9)
        val items = listOf(bottom, top)

        assertFalse(applyStacking(top, items, StackingAction.BRING_TO_FRONT))
        assertEquals(9, top.zIndex)
    }

    @Test
    fun `bring to front still applies when the item merely ties for the highest zIndex`() {
        val other = item(3)
        val tied = item(3)
        val items = listOf(other, tied)

        assertTrue(applyStacking(tied, items, StackingAction.BRING_TO_FRONT))
        assertPaintOrder(listOf(other, tied), items)
        assertTrue(tied.zIndex > other.zIndex)
    }

    @Test
    fun `send to back puts the item below every other`() {
        val bottom = item(0)
        val middle = item(5)
        val top = item(9)
        val items = listOf(bottom, middle, top)

        assertTrue(applyStacking(top, items, StackingAction.SEND_TO_BACK))

        assertEquals(-1, top.zIndex)
        assertPaintOrder(listOf(top, bottom, middle), items)
    }

    @Test
    fun `send to back is a no-op for the item already at the bottom`() {
        val bottom = item(0)
        val top = item(9)
        val items = listOf(bottom, top)

        assertFalse(applyStacking(bottom, items, StackingAction.SEND_TO_BACK))
        assertEquals(0, bottom.zIndex)
    }

    @Test
    fun `bring forward swaps with the next item up, leaving everything else alone`() {
        val bottom = item(0)
        val middle = item(5)
        val top = item(9)
        val items = listOf(bottom, middle, top)

        assertTrue(applyStacking(bottom, items, StackingAction.BRING_FORWARD))

        assertEquals(5, bottom.zIndex)
        assertEquals(0, middle.zIndex)
        assertEquals(9, top.zIndex)
        assertPaintOrder(listOf(middle, bottom, top), items)
    }

    @Test
    fun `send backward swaps with the next item down`() {
        val bottom = item(0)
        val middle = item(5)
        val top = item(9)
        val items = listOf(bottom, middle, top)

        assertTrue(applyStacking(top, items, StackingAction.SEND_BACKWARD))

        assertEquals(5, top.zIndex)
        assertEquals(9, middle.zIndex)
        assertPaintOrder(listOf(bottom, top, middle), items)
    }

    @Test
    fun `bring forward is a no-op at the top and send backward a no-op at the bottom`() {
        val bottom = item(0)
        val top = item(9)
        val items = listOf(bottom, top)

        assertFalse(applyStacking(top, items, StackingAction.BRING_FORWARD))
        assertFalse(applyStacking(bottom, items, StackingAction.SEND_BACKWARD))
        assertEquals(0, bottom.zIndex)
        assertEquals(9, top.zIndex)
    }

    /**
     * Two items can legitimately share a zIndex (the clock/date split hands both halves the original
     * item's), and there swapping the two values would change nothing at all - so the page is
     * renumbered in its current paint order first and the step still happens.
     */
    @Test
    fun `bring forward still moves the item when it ties with its neighbour`() {
        val first = item(4)
        val second = item(4)
        val items = listOf(first, second)

        assertTrue(applyStacking(first, items, StackingAction.BRING_FORWARD))

        assertPaintOrder(listOf(second, first), items)
        assertTrue(first.zIndex > second.zIndex)
    }

    @Test
    fun `repeated bring forward walks the item up one position at a time`() {
        val a = item(0)
        val b = item(1)
        val c = item(2)
        val items = listOf(a, b, c)

        applyStacking(a, items, StackingAction.BRING_FORWARD)
        assertPaintOrder(listOf(b, a, c), items)
        applyStacking(a, items, StackingAction.BRING_FORWARD)
        assertPaintOrder(listOf(b, c, a), items)
        assertFalse(applyStacking(a, items, StackingAction.BRING_FORWARD))
        assertPaintOrder(listOf(b, c, a), items)
    }

    @Test
    fun `an item that is not on the page is left alone`() {
        val onPage = item(0)
        val elsewhere = item(7)

        assertFalse(applyStacking(elsewhere, listOf(onPage), StackingAction.BRING_TO_FRONT))
        assertEquals(7, elsewhere.zIndex)
        assertEquals(0, onPage.zIndex)
    }

    /**
     * Identity, not equality: [GridItem] is a data class, so two same-shaped items compare equal and
     * a lookup by value would place the item relative to a list containing "it" twice.
     */
    @Test
    fun `an equal-but-distinct item is not mistaken for the one on the page`() {
        val onPage = item(0)
        val copy = onPage.copy()

        assertFalse(applyStacking(copy, listOf(onPage), StackingAction.BRING_TO_FRONT))
    }

    @Test
    fun `a single-item page has nothing to stack against`() {
        val only = item(3)

        assertFalse(applyStacking(only, listOf(only), StackingAction.BRING_TO_FRONT))
        assertFalse(applyStacking(only, listOf(only), StackingAction.SEND_TO_BACK))
        assertFalse(applyStacking(only, listOf(only), StackingAction.BRING_FORWARD))
        assertFalse(applyStacking(only, listOf(only), StackingAction.SEND_BACKWARD))
        assertEquals(3, only.zIndex)
    }
}
