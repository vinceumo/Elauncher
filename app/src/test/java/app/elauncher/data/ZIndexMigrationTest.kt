package app.elauncher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Guards the one-time zIndex backfill (plan 005 Step 2): every item on every page is assigned
 * zIndex equal to its 0-based position in [Page.items], matching today's incidental insertion-
 * order stacking, so existing installs see no visual change until they explicitly reorder.
 *
 * Same reasoning as AppListMigrationTest/ClockDateSplitTest: this runs once, in place, against
 * the only copy of the user's real page layout, guarded by a single boolean that must never let
 * it re-run and clobber a reorder the user has since made.
 */
class ZIndexMigrationTest {

    private fun buildPrefs(
        fakePrefs: FakeSharedPreferences,
        screenWidthDp: Int = 411,
        screenHeightDp: Int = 892,
    ): Prefs {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        configuration.screenWidthDp = screenWidthDp
        configuration.screenHeightDp = screenHeightDp
        `when`(resources.configuration).thenReturn(configuration)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(fakePrefs)
        return Prefs(context)
    }

    /**
     * A GridItem json object with no "zIndex" key at all - exactly what real pre-Step-1 saved data
     * looks like, and what [toGridItemOrNull]'s `optInt(KEY_Z_INDEX, 0)` fallback exists to parse.
     * Written directly against the raw SharedPreferences string rather than through `prefs.pages =`,
     * since that setter always serializes the (now-always-present) zIndex field.
     */
    private fun preMigrationItemJson(type: String, row: Int) = JSONObject().apply {
        put("type", type)
        put("col", 0)
        put("row", row)
        put("spanX", 1)
        put("spanY", 1)
    }

    private fun preMigrationPagesJson(itemCount: Int): String {
        val items = JSONArray().apply {
            repeat(itemCount) { i -> put(preMigrationItemJson("APP_LIST", row = i)) }
        }
        val page = JSONObject().apply {
            put("id", "p1")
            put("name", "Home")
            put("items", items)
        }
        return JSONArray().apply { put(page) }.toString()
    }

    @Test
    fun `pre-migration items are backfilled to zIndex equal to their list index`() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.edit().putString("PAGES", preMigrationPagesJson(itemCount = 4)).apply()
        val prefs = buildPrefs(fakePrefs)
        // Isolate from the other migrations `pages` reads through: pretend they already ran, so
        // only the zIndex backfill under test actually does anything.
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
        prefs.appListMigrationDone = true
        prefs.clockDateSplitDone = true
        assertTrue(prefs.zIndexMigrationDone.not())

        val page = prefs.pages.single()

        assertEquals(4, page.items.size)
        page.items.forEachIndexed { index, item -> assertEquals(index, item.zIndex) }
        assertTrue(prefs.zIndexMigrationDone)
    }

    @Test
    fun `the backfill runs once - it never re-derives zIndex from list position again`() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.edit().putString("PAGES", preMigrationPagesJson(itemCount = 3)).apply()
        val prefs = buildPrefs(fakePrefs)
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
        prefs.appListMigrationDone = true
        prefs.clockDateSplitDone = true

        val firstRead = prefs.pages.single()
        assertEquals(listOf(0, 1, 2), firstRead.items.map { it.zIndex })

        // Simulate a user reorder after the migration already ran: the stored item order no longer
        // matches each item's own zIndex.
        val reordered = firstRead.copy(items = firstRead.items.reversed().toMutableList())
        prefs.pages = listOf(reordered)

        val secondRead = prefs.pages.single()
        // Still [2, 1, 0], not re-backfilled to [0, 1, 2] - the guard flag stops a second run from
        // silently undoing the user's reorder.
        assertEquals(listOf(2, 1, 0), secondRead.items.map { it.zIndex })
    }
}
