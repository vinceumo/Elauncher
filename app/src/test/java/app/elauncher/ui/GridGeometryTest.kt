package app.elauncher.ui

import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import app.elauncher.data.coversCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pixel arithmetic HomeGridView positions every child by (plan 005 Step 3), and the
 * point-in-rect test that replaced its cell-keyed occupancy map.
 *
 * Worth a test despite being four multiplications: it is the single definition shared by an item's
 * own view, the edit-mode selection overlay and the drag/resize snap preview, so an off-by-one
 * between position and size here shows up as chrome that doesn't sit on the thing it is editing -
 * the kind of thing that is obvious on a device and invisible in review.
 */
class GridGeometryTest {

    private fun item(col: Int, row: Int, spanX: Int = 1, spanY: Int = 1) = GridItem(
        type = GridItemType.APP_LIST,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
    )

    @Test
    fun `bounds are origin times cell size, size is span times cell size`() {
        val bounds = gridBounds(col = 2, row = 3, spanX = 4, spanY = 2, cellSizePx = 48)
        assertEquals(2 * 48, bounds.left)
        assertEquals(3 * 48, bounds.top)
        assertEquals(4 * 48, bounds.width)
        assertEquals(2 * 48, bounds.height)
    }

    @Test
    fun `grid offset shifts position but never size`() {
        val bounds = gridBounds(
            col = 1, row = 1, spanX = 2, spanY = 2, cellSizePx = 48, offsetX = 7, offsetY = 11,
        )
        assertEquals(7 + 48, bounds.left)
        assertEquals(11 + 48, bounds.top)
        assertEquals(96, bounds.width)
        assertEquals(96, bounds.height)
    }

    @Test
    fun `item at the origin starts at the grid offset`() {
        val bounds = item(col = 0, row = 0, spanX = 3, spanY = 1)
            .gridBounds(cellSizePx = 48, offsetX = 5, offsetY = 6)
        assertEquals(5, bounds.left)
        assertEquals(6, bounds.top)
        assertEquals(144, bounds.width)
        assertEquals(48, bounds.height)
    }

    /**
     * Two side-by-side items - the common, non-overlapping case - get adjacent, non-overlapping
     * pixel boxes: the first one's right edge is exactly the second one's left edge.
     */
    @Test
    fun `neighbouring items abut without overlapping`() {
        val left = item(col = 0, row = 0, spanX = 2, spanY = 2).gridBounds(cellSizePx = 48)
        val right = item(col = 2, row = 0, spanX = 2, spanY = 2).gridBounds(cellSizePx = 48)
        assertEquals(left.left + left.width, right.left)
        assertEquals(left.top, right.top)
    }

    /** Overlapping items get overlapping boxes rather than one of them being dropped. */
    @Test
    fun `overlapping items get overlapping boxes`() {
        val under = item(col = 0, row = 0, spanX = 4, spanY = 2).gridBounds(cellSizePx = 48)
        val over = item(col = 2, row = 1, spanX = 4, spanY = 2).gridBounds(cellSizePx = 48)
        assertTrue(over.left < under.left + under.width)
        assertTrue(over.top < under.top + under.height)
    }

    @Test
    fun `coversCell is true for every cell inside the span and false outside it`() {
        val spanning = item(col = 1, row = 2, spanX = 3, spanY = 2)
        for (col in 1..3) {
            for (row in 2..3) {
                assertTrue("($col, $row) is inside the span", spanning.coversCell(col, row))
            }
        }
        assertFalse(spanning.coversCell(0, 2)) // one column before
        assertFalse(spanning.coversCell(4, 2)) // one column past
        assertFalse(spanning.coversCell(1, 1)) // one row above
        assertFalse(spanning.coversCell(1, 4)) // one row below
    }
}
