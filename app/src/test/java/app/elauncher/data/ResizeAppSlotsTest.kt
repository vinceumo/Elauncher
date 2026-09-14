package app.elauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GridItem.resizeAppSlots] (plan 006 Step 6) - the single implementation of "make this App List
 * have exactly N slots", shared by the settings dialog's stepper (HomeFragment.applyAppListSettings)
 * and by drag-resizing the item's content axis (HomeGridView.commitGeometry). Pure list surgery, no
 * Android dependency, so it is testable here directly rather than only through the UI.
 */
class ResizeAppSlotsTest {

    private fun appList(vararg slots: AppSlot) = GridItem(
        type = GridItemType.APP_LIST,
        col = 0,
        row = 0,
        spanX = 2,
        spanY = slots.size,
        appSlots = slots.toMutableList(),
    )

    private fun filled(name: String) = AppSlot(appName = name, appPackage = "pkg.$name")

    @Test
    fun `growing appends empty slots and leaves the existing ones alone`() {
        val item = appList(filled("a"), filled("b"))
        item.resizeAppSlots(5)

        assertEquals(5, item.appSlots.size)
        assertEquals("a", item.appSlots[0].appName)
        assertEquals("b", item.appSlots[1].appName)
        // Appended slots are the unfilled ones the renderer shows as the "App" placeholder.
        for (i in 2 until 5) {
            assertNull(item.appSlots[i].appPackage)
            assertNull(item.appSlots[i].appName)
        }
    }

    @Test
    fun `shrinking drops trailing slots even when they hold apps`() {
        val item = appList(filled("a"), filled("b"), filled("c"), filled("d"))
        item.resizeAppSlots(2)

        // No confirmation, no preservation: slots 3-4 are gone, filled or not - the same rule the
        // rest of this launcher follows for removals (removeGridItem, clearAppSlot).
        assertEquals(2, item.appSlots.size)
        assertEquals(listOf("a", "b"), item.appSlots.map { it.appName })
    }

    @Test
    fun `an already-correct count is left exactly as it is`() {
        val slots = arrayOf(filled("a"), AppSlot(), filled("c"))
        val item = appList(*slots)
        val before = item.appSlots.toList()
        item.resizeAppSlots(3)

        assertEquals(3, item.appSlots.size)
        assertEquals(before, item.appSlots)
        // Same instances, not just equal ones: a no-op must not churn the list the item exposes.
        before.indices.forEach { assertSame(before[it], item.appSlots[it]) }
    }

    @Test
    fun `mutates in place rather than replacing the list`() {
        val item = appList(filled("a"))
        val list = item.appSlots
        item.resizeAppSlots(3)

        assertSame(list, item.appSlots)
    }

    @Test
    fun `a count outside the supported range is clamped, never applied raw`() {
        val tooFew = appList(filled("a"), filled("b"))
        tooFew.resizeAppSlots(0)
        assertEquals(MIN_APP_LIST_SLOT_COUNT, tooFew.appSlots.size)
        // Never empty: a zero-slot list would render as nothing the user could get back.
        assertTrue(tooFew.appSlots.isNotEmpty())

        val tooMany = appList(filled("a"))
        tooMany.resizeAppSlots(MAX_APP_LIST_SLOT_COUNT + 4)
        assertEquals(MAX_APP_LIST_SLOT_COUNT, tooMany.appSlots.size)
    }

    @Test
    fun `the stepper's range is the one the drag is bounded by`() {
        assertEquals(1, MIN_APP_LIST_SLOT_COUNT)
        assertEquals(8, MAX_APP_LIST_SLOT_COUNT)
        assertTrue(DEFAULT_APP_LIST_SLOT_COUNT in MIN_APP_LIST_SLOT_COUNT..MAX_APP_LIST_SLOT_COUNT)
    }
}
