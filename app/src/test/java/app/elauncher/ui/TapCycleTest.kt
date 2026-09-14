package app.elauncher.ui

import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins [cycleSelection]: which of several overlapping items a tap picks, and how repeated taps at
 * the same place walk down the stack (plan 005 Step 4).
 *
 * The behaviour it protects can't be seen from the code that calls it - HomeGridView only stores
 * the index and item this hands back - and can't be exercised on a device until overlapping layouts
 * exist at all (Step 5 relaxes collision). So the order, the wraparound and the reset are asserted
 * here, on plain data, rather than being left to a manual pass.
 */
class TapCycleTest {

    private fun item(zIndex: Int, col: Int = 0, row: Int = 0, spanX: Int = 2, spanY: Int = 2) =
        GridItem(
            type = GridItemType.APP_LIST,
            col = col,
            row = row,
            spanX = spanX,
            spanY = spanY,
            zIndex = zIndex,
        )

    private val slop = 8

    /** Three items stacked on the same cells, handed over in an order that is not the stack order. */
    private val bottom = item(zIndex = 0)
    private val middle = item(zIndex = 5)
    private val top = item(zIndex = 9)
    private val stack = listOf(middle, top, bottom)

    private val point = TapPoint(100, 100)

    /** One tap, then [taps] - 1 more at the same point; returns every item selected along the way. */
    private fun tapRepeatedly(candidates: List<GridItem>, taps: Int): List<GridItem?> {
        var previous: TapPoint? = null
        var index = 0
        return (1..taps).map {
            val result = cycleSelection(candidates, point, previous, index, slop)
            previous = point
            index = result.index
            result.selected
        }
    }

    @Test
    fun `first tap selects the topmost item regardless of list order`() {
        val result = cycleSelection(stack, point, previousTapPoint = null, previousCycleIndex = 0, touchSlopPx = slop)
        assertEquals(0, result.index)
        assertSame(top, result.selected)
    }

    @Test
    fun `repeated taps at the same point walk down the stack and wrap around`() {
        val selected = tapRepeatedly(stack, taps = 5)
        assertEquals(listOf(top, middle, bottom, top, middle), selected)
    }

    @Test
    fun `a tap at a different point resets to the topmost item`() {
        val first = cycleSelection(stack, point, null, 0, slop)
        val second = cycleSelection(stack, point, point, first.index, slop)
        assertSame("second tap should have moved down the stack", middle, second.selected)

        val elsewhere = TapPoint(point.x + slop * 10, point.y)
        val third = cycleSelection(stack, elsewhere, point, second.index, slop)
        assertEquals(0, third.index)
        assertSame(top, third.selected)
    }

    /**
     * A finger never lands twice on exactly the same pixel, so "the same place" has to mean "within
     * the drag-vs-tap slop" or cycling would be unreachable in practice.
     */
    @Test
    fun `a tap within touch slop counts as the same place`() {
        val nudged = TapPoint(point.x + slop - 1, point.y)
        val result = cycleSelection(stack, nudged, point, previousCycleIndex = 0, touchSlopPx = slop)
        assertEquals(1, result.index)
        assertSame(middle, result.selected)
    }

    /** Slop is a radius, not a per-axis budget: a diagonal nudge past it is a different place. */
    @Test
    fun `a diagonal nudge past touch slop is a different place`() {
        val nudged = TapPoint(point.x + slop, point.y + slop)
        val result = cycleSelection(stack, nudged, point, previousCycleIndex = 0, touchSlopPx = slop)
        assertEquals(0, result.index)
        assertSame(top, result.selected)
    }

    @Test
    fun `a single item under the finger stays selected however often it is tapped`() {
        assertEquals(listOf(top, top, top), tapRepeatedly(listOf(top), taps = 3))
    }

    @Test
    fun `a tap that hits nothing selects nothing`() {
        val result = cycleSelection(emptyList(), point, point, previousCycleIndex = 2, touchSlopPx = slop)
        assertEquals(0, result.index)
        assertNull(result.selected)
    }

    /**
     * Ties keep the stacking order rendering gives them: HomeGridView adds items `sortedBy { zIndex }`
     * (a stable sort), so of two items tied on zIndex the *later* stored one is added last and paints
     * on top - and must therefore be offered first here. This is the case a plain
     * `sortedByDescending` gets backwards.
     */
    @Test
    fun `items tied on zIndex are offered in reverse stored order`() {
        val first = item(zIndex = 3)
        val second = item(zIndex = 3)
        val selected = tapRepeatedly(listOf(first, second), taps = 2)
        assertEquals(listOf(second, first), selected)
    }

    /**
     * The previous index is advisory - the page can be rebound or an item deleted between two taps -
     * so an out-of-range one must wrap rather than throw.
     */
    @Test
    fun `a stale previous index is wrapped instead of indexed with`() {
        val stale = cycleSelection(stack, point, point, previousCycleIndex = 97, touchSlopPx = slop)
        assertEquals((97 + 1) % 3, stale.index)
        assertSame(bottom, stale.selected)

        val negative = cycleSelection(stack, point, point, previousCycleIndex = -4, touchSlopPx = slop)
        assertEquals(0, negative.index)
        assertSame(top, negative.selected)
    }
}
