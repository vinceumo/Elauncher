package app.elauncher.ui

import app.elauncher.data.GridItem
import kotlin.math.hypot

/**
 * A touch position in [HomeGridView]'s own pixel coordinates.
 *
 * Plain Ints rather than [android.graphics.Point] for the same reason [GridBounds] avoids
 * [android.graphics.Rect]: this file is pure arithmetic and has to stay directly unit-testable on
 * the JVM, where android.* resolves to stubs whose methods throw.
 */
internal data class TapPoint(val x: Int, val y: Int)

/**
 * The outcome of one tap: which position in the overlapping stack is now selected, and the item
 * sitting there ([selected] is null only when the tap hit nothing at all).
 */
internal data class TapCycleResult(val index: Int, val selected: GridItem?)

/**
 * Resolves which of several overlapping items a tap selects, cycling top-to-bottom through the
 * stack when the user taps the same spot repeatedly.
 *
 * The problem this exists for: once two items may overlap (plan 005), a tap on the shared region
 * reaches only the topmost one - Android dispatches to the last-added child whose bounds contain
 * the point and [app.elauncher.listener.ViewSwipeTouchListener] claims the gesture at ACTION_DOWN,
 * so nothing underneath is ever offered the touch and there is no natural fall-through to lean on.
 * Tapping the same place again therefore has to *mean* "not that one, the next one down".
 *
 * The rule:
 * - A tap at a **new** place resets to the topmost item (index 0). "New" is measured against
 *   [touchSlopPx] - the same [android.view.ViewConfiguration.scaledTouchSlop] the codebase already
 *   uses to tell a drag from a tap - so a finger that lands a pixel or two off the last tap still
 *   counts as the same spot.
 * - A tap at the **same** place advances one step down the stack, wrapping back to the top after
 *   the bottommost item.
 *
 * [candidates] is every item whose footprint contains the tap point, in the page's stored order;
 * the sort is done here rather than being the caller's job, so a caller cannot silently invert the
 * stack. It is `sortedBy { zIndex }.reversed()` and deliberately not `sortedByDescending`: both
 * sorts are stable, but on a zIndex tie descending would keep the stored order and hand back the
 * item that paints *underneath* first. Reversing the ascending sort is the exact inverse of
 * HomeGridView.rebuildChildren()'s add order, so "index 0" here is always the item actually on top.
 *
 * [previousCycleIndex] is taken as advisory rather than trusted: the item list can change (a page
 * rebind, an item deleted) between two taps, so it is wrapped into range instead of indexed with.
 *
 * Pure by design - no view, no state, no Context - so the cycle order and its wraparound are
 * unit-testable without a device (TapCycleTest); [HomeGridView] owns the state this reads and
 * writes, and the reset-on-page-change/edit-mode rules around it.
 */
internal fun cycleSelection(
    candidates: List<GridItem>,
    tapPoint: TapPoint,
    previousTapPoint: TapPoint?,
    previousCycleIndex: Int,
    touchSlopPx: Int,
): TapCycleResult {
    val stack = candidates.sortedBy { it.zIndex }.reversed()
    if (stack.isEmpty()) return TapCycleResult(index = 0, selected = null)
    val sameSpot = previousTapPoint != null && tapPoint.isWithin(touchSlopPx, previousTapPoint)
    // floorMod, not %: previousCycleIndex is advisory (see kdoc) and a negative one would otherwise
    // produce a negative index and blow up at the indexing below.
    val index = if (sameSpot) {
        ((previousCycleIndex + 1) % stack.size + stack.size) % stack.size
    } else {
        0
    }
    return TapCycleResult(index = index, selected = stack[index])
}

/** Whether this point is close enough to [other] to count as the same spot on screen. */
internal fun TapPoint.isWithin(slopPx: Int, other: TapPoint): Boolean =
    hypot((x - other.x).toFloat(), (y - other.y).toFloat()) <= slopPx
