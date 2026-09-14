package app.elauncher.ui

import app.elauncher.data.GridItem

/**
 * A grid footprint expressed in pixels, relative to the top-left corner of the [HomeGridView] that
 * owns the grid.
 *
 * Deliberately a plain data class rather than [android.graphics.Rect]: this is pure arithmetic with
 * no need for Rect's mutability or its drawing-oriented API, and staying off android.graphics keeps
 * it directly unit-testable on the JVM (the unit-test classpath resolves android.* to stubs whose
 * methods throw).
 */
internal data class GridBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Pixel bounds of a whole-cell footprint: ([col], [row]) x ([spanX], [spanY]) cells of [cellSizePx],
 * shifted by the grid's own origin ([offsetX], [offsetY] - see HomeGridView.gridOffsetX).
 *
 * The single definition of that arithmetic: [HomeGridView] positions every item view, the edit-mode
 * selection overlay and the drag/resize snap preview from this, so an item and the chrome drawn over
 * it can never disagree about where a cell range actually is.
 */
internal fun gridBounds(
    col: Int,
    row: Int,
    spanX: Int,
    spanY: Int,
    cellSizePx: Int,
    offsetX: Int = 0,
    offsetY: Int = 0,
): GridBounds = GridBounds(
    left = offsetX + col * cellSizePx,
    top = offsetY + row * cellSizePx,
    width = spanX * cellSizePx,
    height = spanY * cellSizePx,
)

/** [gridBounds] for this item's own position and span. */
internal fun GridItem.gridBounds(cellSizePx: Int, offsetX: Int = 0, offsetY: Int = 0): GridBounds =
    gridBounds(col, row, spanX, spanY, cellSizePx, offsetX, offsetY)
