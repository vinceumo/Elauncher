package app.elauncher.ui

import app.elauncher.data.GridItem
import app.elauncher.data.overlaps

/**
 * Whether [item] may occupy the footprint ([col], [row]) x ([spanX], [spanY]) on a
 * [columnCount] x [rowCount] grid already holding [items].
 *
 * Two rules, and only one of them is negotiable:
 * - **On the grid.** Always enforced. A footprint with a negative origin, a span below one, or an
 *   edge past the last column/row is never legal, whatever asked for it - there is nowhere to draw
 *   it and no gesture that should be able to produce it.
 * - **Clear of every other item.** Enforced unless [allowOverlap]. Overlap is a legal layout now
 *   (HomeGridView renders one view per item, stacked by [GridItem.zIndex]), but only as something
 *   the user asks for by dragging one item onto another - never as a surprise. So the drag paths
 *   pass true and anything that places an item *for* the user leaves it false.
 *
 * [item] is excluded from the overlap test by identity: a move or resize is checked against the
 * rest of the page, not against where the item currently is.
 *
 * Pure and free of android.* so it can be unit-tested without a [HomeGridView] (GridFitTest);
 * overlap itself is still defined exactly once, by [GridItem.overlaps] on the data side.
 */
internal fun fitsOnGrid(
    item: GridItem,
    col: Int,
    row: Int,
    spanX: Int,
    spanY: Int,
    items: List<GridItem>,
    columnCount: Int,
    rowCount: Int,
    allowOverlap: Boolean = false,
): Boolean {
    if (col < 0 || row < 0 || spanX < 1 || spanY < 1) return false
    if (col + spanX > columnCount || row + spanY > rowCount) return false
    if (allowOverlap) return true
    return items.none { other -> other !== item && other.overlaps(col, row, spanX, spanY) }
}

/**
 * [desiredSpanY], reduced only as far as [target]'s own row and the grid's [rowCount] force: the
 * tallest it can be without its bottom edge running off the grid, and never below one row.
 *
 * Hoisted out of HomeFragment (plan 006 Step 5, mirroring the [fitsOnGrid] extraction from
 * HomeGridView in plan 005 Step 5): the logic only ever touched [target]'s plain Int fields and two
 * Int parameters, so there was nothing Fragment- or Android-specific stopping it from being a pure,
 * JVM-testable top-level function like its neighbour above - the same "clamped to the grid, not to
 * neighbours" rule applies to both, so keeping them next to each other in this file also keeps that
 * shared rule visible in one place instead of split across ui/GridFit.kt and ui/HomeFragment.kt.
 *
 * Neighbours are not a limit, the grid is: an item can grow over whatever sits below it (drag-resize
 * already allows this - see [fitsOnGrid]'s kdoc), so the only thing clamped against here is the edge
 * of the grid itself.
 */
internal fun clampSpanYToGrid(target: GridItem, desiredSpanY: Int, rowCount: Int): Int =
    desiredSpanY.coerceIn(1, (rowCount - target.row).coerceAtLeast(1))

/**
 * [clampSpanYToGrid] mirrored onto the X axis, for a horizontal App List whose width is the span
 * that follows the slot count: the widest it can be without its right edge running off the grid, and
 * never below one column. Same rule, same reasoning - neighbours are not a limit, the grid is.
 */
internal fun clampSpanXToGrid(target: GridItem, desiredSpanX: Int, columnCount: Int): Int =
    desiredSpanX.coerceIn(1, (columnCount - target.col).coerceAtLeast(1))
