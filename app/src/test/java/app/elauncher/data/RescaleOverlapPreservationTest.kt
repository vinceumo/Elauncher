package app.elauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the half of `List<Page>.rescaleForCellSize()` that tells an *intentional* overlap (the user
 * dropped one widget on top of another) from an *accidental* one (whole-cell rounding pushed two
 * previously-adjacent items into each other). Only the latter may be moved.
 *
 * Kept separate from [GridItemRescaleTest], which pins the rescale ratio itself.
 */
class RescaleOverlapPreservationTest {

    private val old = Constants.Grid.LEGACY_CELL_SIZE_DP // 80
    private val new = Constants.Grid.CELL_SIZE_DP // 48

    private fun item(col: Int, row: Int, spanX: Int, spanY: Int) = GridItem(
        type = GridItemType.APP,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        appName = "Camera",
        appPackage = "com.android.camera",
    )

    /** Local restatement of GridItem.overlaps, so the test doesn't assert via the code under test. */
    private fun overlap(a: GridItem, b: GridItem): Boolean =
        a.col < b.col + b.spanX && b.col < a.col + a.spanX &&
            a.row < b.row + b.spanY && b.row < a.row + a.spanY

    @Test
    fun `a pre-existing overlap survives while a rounding-introduced one is resolved`() {
        // A and B overlap at (1, 1) before the rescale: intentional, must still overlap after.
        // C and D are merely adjacent at cols 1 and 2, the case the 80dp->48dp ratio turns into an
        // overlap (col 1 -> cols 2-3, col 2 -> cols 3-4): accidental, D must be moved off C.
        val page = Page(
            "p1",
            "Home",
            mutableListOf(
                item(col = 0, row = 0, spanX = 2, spanY = 2),
                item(col = 1, row = 1, spanX = 2, spanY = 2),
                item(col = 1, row = 5, spanX = 1, spanY = 1),
                item(col = 2, row = 5, spanX = 1, spanY = 1),
            ),
        )

        val items = listOf(page)
            .rescaleForCellSize(old, new, columnCount = 10, rowCount = 12)
            .single()
            .items
        val (a, b, c, d) = items

        assertTrue("intentional A/B overlap must survive the rescale", overlap(a, b))
        // C is the earlier of the accidental pair: it is never the one that moves.
        assertEquals(2, c.col)
        assertEquals(8, c.row)
        assertFalse("rounding-introduced C/D overlap must be resolved", overlap(c, d))
        items.filter { it !== d }.forEach { assertFalse(overlap(it, d)) }
        assertTrue(d.col >= 0 && d.col + d.spanX <= 10)
        assertTrue(d.row >= 0 && d.row + d.spanY <= 12)
    }

    @Test
    fun `an item can keep one overlap and shed another, because the set is keyed by pair`() {
        // B intentionally overlaps A and is merely adjacent to E. After rounding B also lands on E.
        // B is therefore in the "leave alone" set for (A, B) and the "resolve" set for (B, E) at the
        // same time: the A/B overlap stays, and E - the later item of the new pair - is moved.
        val page = Page(
            "p1",
            "Home",
            mutableListOf(
                item(col = 0, row = 0, spanX = 2, spanY = 2),
                item(col = 1, row = 0, spanX = 1, spanY = 1),
                item(col = 2, row = 0, spanX = 1, spanY = 1),
            ),
        )

        val items = listOf(page)
            .rescaleForCellSize(old, new, columnCount = 10, rowCount = 12)
            .single()
            .items
        val (a, b, e) = items

        assertTrue("A/B overlapped before the rescale, so it must still overlap", overlap(a, b))
        // B kept its rescaled position rather than being pushed away by the new E collision.
        assertEquals(2, b.col)
        assertEquals(0, b.row)
        assertFalse("the new B/E overlap must be resolved", overlap(b, e))
        assertFalse(overlap(a, e))
    }
}
