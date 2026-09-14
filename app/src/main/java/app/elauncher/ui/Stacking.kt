package app.elauncher.ui

import app.elauncher.data.GridItem

/**
 * The four ways one item's place in its page's stacking order can be changed (plan 005 Step 7).
 *
 * Named after the menu entries they back, and deliberately the same four every desktop
 * document editor offers - "one position" (forward/backward) and "all the way" (front/back) -
 * rather than anything launcher-specific, because that is the model users already have.
 */
internal enum class StackingAction { BRING_TO_FRONT, BRING_FORWARD, SEND_BACKWARD, SEND_TO_BACK }

/**
 * Applies [action] to [target]'s [GridItem.zIndex] within [items] (one page's item list), returning
 * true if anything actually changed.
 *
 * The ordering model is global across the page, not "relative to what this item happens to overlap":
 * sorting [items] by zIndex ascending is the paint/touch order (HomeGridView.rebuildChildren adds
 * children in exactly that order), so:
 * - **Bring to front** puts the item one above the highest other item.
 * - **Send to back** puts it one below the lowest other item.
 * - **Bring forward / send backward** swap zIndex values with the single neighbour one position away
 *   in that sorted order, and do nothing at all at either end of it.
 * Values are never renumbered into a contiguous range for their own sake (nothing requires it) and
 * are allowed to go negative, exactly as [GridItem.zIndex]'s own contract allows.
 *
 * [target] must be an element of [items] *by identity*: the whole point is to place it relative to
 * its own page's other items, and a copy that merely compares equal (GridItem is a data class, so
 * two same-shaped items do) would be placed relative to a list it isn't in.
 *
 * Mutates in place, on the caller's own items, and persists nothing - the caller writes the page
 * back (HomeFragment.updateGridItem). Pure and free of android.*, so every ordering rule above is
 * unit-testable without a device (StackingTest).
 */
internal fun applyStacking(target: GridItem, items: List<GridItem>, action: StackingAction): Boolean {
    if (items.none { it === target }) return false
    val others = items.filter { it !== target }
    // Nothing to be ordered against: on a one-item page every action is meaningless. (The menu these
    // back only opens for an item that overlaps another one, so this is a guard, not a real case.)
    if (others.isEmpty()) return false

    return when (action) {
        // Already strictly above everything else - re-stating it as max + 1 would only inflate the
        // stored number without moving anything. A *tie* with the current maximum is not that case:
        // the tie-break is stored order, so the item may well be painting underneath.
        StackingAction.BRING_TO_FRONT -> {
            val highest = others.maxOf { it.zIndex }
            if (target.zIndex > highest) false else {
                target.zIndex = highest + 1
                true
            }
        }

        StackingAction.SEND_TO_BACK -> {
            val lowest = others.minOf { it.zIndex }
            if (target.zIndex < lowest) false else {
                target.zIndex = lowest - 1
                true
            }
        }

        StackingAction.BRING_FORWARD -> swapWithNeighbour(target, items, step = 1)
        StackingAction.SEND_BACKWARD -> swapWithNeighbour(target, items, step = -1)
    }
}

/**
 * Swaps [target] with the item [step] positions away from it in [items]' zIndex order, which moves
 * it exactly one place up (step = 1) or down (step = -1) the stack. False when there is no such
 * neighbour, i.e. the item is already topmost/bottommost.
 *
 * `sortedBy` is stable, so items sharing a zIndex keep their stored order - the same tie-break
 * HomeGridView.rebuildChildren's add order and cycleSelection's stack both use, so "the neighbour
 * one position away" here is the neighbour the user can actually see one layer away.
 *
 * Ties are the one case where swapping the two values would be a no-op while the user asked for a
 * visible change, so the page's order is first renumbered to 0..n-1 in its current order. That
 * renumbering is deliberately order-preserving (rank order == the order it was read from), so it
 * changes what is stored without changing what is drawn; the swap that follows is then the plain
 * value swap the model describes.
 */
private fun swapWithNeighbour(target: GridItem, items: List<GridItem>, step: Int): Boolean {
    val order = items.sortedBy { it.zIndex }
    val index = order.indexOfFirst { it === target }
    val neighbourIndex = index + step
    if (neighbourIndex !in order.indices) return false

    val neighbour = order[neighbourIndex]
    if (target.zIndex == neighbour.zIndex) {
        order.forEachIndexed { rank, item -> item.zIndex = rank }
    }
    val targetZIndex = target.zIndex
    target.zIndex = neighbour.zIndex
    neighbour.zIndex = targetZIndex
    return true
}
