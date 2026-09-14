package app.elauncher.ui

import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [fitsOnGrid]: which move/resize targets HomeGridView accepts, and exactly what the
 * `allowOverlap` flag does and doesn't lift (plan 005 Step 5).
 *
 * The distinction matters and isn't visible from either call site: a drag that overlaps must commit
 * (that is the whole feature), while a drag off the grid must still snap back - and the strict form
 * has to keep meaning what it did, since automatic placement still refuses to drop a new widget on
 * top of an existing one.
 */
class GridFitTest {

    private val columnCount = 6
    private val rowCount = 8

    private fun item(col: Int, row: Int, spanX: Int = 2, spanY: Int = 2) = GridItem(
        type = GridItemType.APP_LIST,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
    )

    /** The item being moved, plus one other sitting in the middle of the page. */
    private val moving = item(col = 0, row = 0)
    private val occupant = item(col = 2, row = 2)
    private val items = listOf(moving, occupant)

    private fun fits(col: Int, row: Int, spanX: Int = 2, spanY: Int = 2, allowOverlap: Boolean) =
        fitsOnGrid(
            item = moving,
            col = col,
            row = row,
            spanX = spanX,
            spanY = spanY,
            items = items,
            columnCount = columnCount,
            rowCount = rowCount,
            allowOverlap = allowOverlap,
        )

    @Test
    fun `free space fits either way`() {
        assertTrue(fits(col = 4, row = 6, allowOverlap = false))
        assertTrue(fits(col = 4, row = 6, allowOverlap = true))
    }

    @Test
    fun `landing on another item is refused strictly and allowed when overlap is allowed`() {
        assertFalse(fits(col = 2, row = 2, allowOverlap = false))
        assertTrue(fits(col = 2, row = 2, allowOverlap = true))
    }

    /** A one-cell clip of the corner is still an overlap - not just an exact origin match. */
    @Test
    fun `clipping a corner of another item counts as overlap`() {
        assertFalse(fits(col = 1, row = 1, allowOverlap = false))
        assertTrue(fits(col = 1, row = 1, allowOverlap = true))
    }

    /** Abutting is not overlapping: cols 0-1 end exactly where the occupant's cols 2-3 begin. */
    @Test
    fun `abutting an item is not overlapping it`() {
        assertTrue(fits(col = 0, row = 2, allowOverlap = false))
    }

    @Test
    fun `running off the right or bottom edge is refused even when overlap is allowed`() {
        assertFalse(fits(col = columnCount - 1, row = 0, allowOverlap = true))
        assertFalse(fits(col = 0, row = rowCount - 1, allowOverlap = true))
    }

    @Test
    fun `a negative origin is refused even when overlap is allowed`() {
        assertFalse(fits(col = -1, row = 0, allowOverlap = true))
        assertFalse(fits(col = 0, row = -1, allowOverlap = true))
    }

    @Test
    fun `a span below one cell is refused even when overlap is allowed`() {
        assertFalse(fits(col = 0, row = 0, spanX = 0, allowOverlap = true))
        assertFalse(fits(col = 0, row = 0, spanY = 0, allowOverlap = true))
    }

    /** The item's own current footprint never blocks it: a resize in place is not a self-collision. */
    @Test
    fun `an item is not tested against itself`() {
        assertTrue(fits(col = moving.col, row = moving.row, spanX = 1, spanY = 1, allowOverlap = false))
    }

    /** Exactly filling the grid's last row/column is legal - the bounds check is inclusive. */
    @Test
    fun `a footprint ending on the last cell fits`() {
        assertTrue(
            fitsOnGrid(
                item = moving,
                col = columnCount - 2,
                row = rowCount - 2,
                spanX = 2,
                spanY = 2,
                items = listOf(moving),
                columnCount = columnCount,
                rowCount = rowCount,
            )
        )
    }
}

/**
 * Pins [clampSpanYToGrid] and [clampSpanXToGrid] (plan 006 Step 5): hoisted out of HomeFragment
 * alongside [fitsOnGrid] once it was clear they were already pure Int-in/Int-out logic with nothing
 * Fragment-specific about them - see the kdoc on [clampSpanYToGrid] for the full reasoning.
 *
 * Both clamp to the grid edge only, never to a neighbour - HomeGridView renders overlapping items
 * stacked by zIndex, so growing over whatever else is on the page is allowed by design.
 */
class ClampSpanToGridTest {

    private fun item(col: Int, row: Int, spanX: Int = 2, spanY: Int = 2) = GridItem(
        type = GridItemType.APP_LIST,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
    )

    @Test
    fun `desired span is kept as-is when it fits within the grid`() {
        val target = item(col = 0, row = 0)
        assertEquals(4, clampSpanYToGrid(target, desiredSpanY = 4, rowCount = 8))
        assertEquals(4, clampSpanXToGrid(target, desiredSpanX = 4, columnCount = 6))
    }

    @Test
    fun `desired span is reduced to whatever is left between the item's origin and the grid edge`() {
        val target = item(col = 4, row = 6)
        // rowCount=8, row=6 -> 2 rows left; columnCount=6, col=4 -> 2 columns left.
        assertEquals(2, clampSpanYToGrid(target, desiredSpanY = 5, rowCount = 8))
        assertEquals(2, clampSpanXToGrid(target, desiredSpanX = 5, columnCount = 6))
    }

    @Test
    fun `an item sitting exactly on the edge is still clamped to one cell, never zero`() {
        val target = item(col = 6, row = 8)
        assertEquals(1, clampSpanYToGrid(target, desiredSpanY = 5, rowCount = 8))
        assertEquals(1, clampSpanXToGrid(target, desiredSpanX = 5, columnCount = 6))
    }

    @Test
    fun `a desired span below one is still floored to one cell`() {
        val target = item(col = 0, row = 0)
        assertEquals(1, clampSpanYToGrid(target, desiredSpanY = 0, rowCount = 8))
        assertEquals(1, clampSpanXToGrid(target, desiredSpanX = 0, columnCount = 6))
    }

    @Test
    fun `neighbours are not a limit - only the grid edge is`() {
        // An occupant sitting well inside the row/column budget must not shrink the clamp: only the
        // grid edge is consulted, items is not even a parameter.
        val target = item(col = 0, row = 0)
        assertEquals(8, clampSpanYToGrid(target, desiredSpanY = 8, rowCount = 8))
        assertEquals(6, clampSpanXToGrid(target, desiredSpanX = 6, columnCount = 6))
    }
}
