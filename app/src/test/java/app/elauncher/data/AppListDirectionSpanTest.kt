package app.elauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spot-check of the direction-aware half of the App List default-span pair (plan 006 Step 2): a
 * horizontal list takes its width from the slot count and its height from the grid, the exact mirror
 * of what [defaultAppListSpanY]/[defaultAppListSpanX] do for a vertical one.
 *
 * Deliberately light - the fuller pass, including the direction-aware call sites, is Step 5.
 */
class AppListDirectionSpanTest {

    @Test
    fun `a horizontal list is one cell wide per slot, and never narrower than one`() {
        assertEquals(4, defaultAppListSpanXForSlots(4))
        assertEquals(8, defaultAppListSpanXForSlots(MAX_DEFAULT_SPAN_X))
        // One slot stays one cell: the renderer's one-cell-per-slot invariant outranks the
        // two-cell readability floor drag-resize applies to the *other* axis.
        assertEquals(1, defaultAppListSpanXForSlots(1))
        assertEquals(1, defaultAppListSpanXForSlots(0))
    }

    @Test
    fun `a horizontal list takes its height from the grid, capped like the vertical one's width`() {
        assertEquals(5, defaultAppListSpanYForGrid(5))
        assertEquals(MAX_DEFAULT_SPAN_X, defaultAppListSpanYForGrid(20))
        assertEquals(1, defaultAppListSpanYForGrid(0))
    }

    @Test
    fun `the two directions are exact mirrors of each other`() {
        assertEquals(defaultAppListSpanY(6), defaultAppListSpanXForSlots(6))
        assertEquals(defaultAppListSpanX(12), defaultAppListSpanYForGrid(12))
    }

    // The mirror-equality test above pins the relationship between the two pairs but never calls
    // defaultAppListSpanX/defaultAppListSpanY directly - only defaultAppListSpanXForSlots/
    // defaultAppListSpanYForGrid are exercised by name elsewhere in this file. Direct coverage of
    // the vertical pair, mirroring the two horizontal cases above, closes that gap (plan 006 Step 5).

    @Test
    fun `a vertical list is one cell tall per slot, and never shorter than one`() {
        assertEquals(4, defaultAppListSpanY(4))
        // No MAX_DEFAULT_SPAN_Y - slot count alone drives height, unlike width which is grid-capped.
        assertEquals(20, defaultAppListSpanY(20))
        assertEquals(1, defaultAppListSpanY(1))
        assertEquals(1, defaultAppListSpanY(0))
    }

    @Test
    fun `a vertical list takes its width from the grid, capped at MAX_DEFAULT_SPAN_X`() {
        assertEquals(5, defaultAppListSpanX(5))
        assertEquals(MAX_DEFAULT_SPAN_X, defaultAppListSpanX(20))
        assertEquals(1, defaultAppListSpanX(0))
    }
}
