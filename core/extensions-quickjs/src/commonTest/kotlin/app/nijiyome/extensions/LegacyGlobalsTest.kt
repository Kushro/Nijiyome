// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Existing .kai bundles call the host through the names of the previous project (contracts/extension-host/globals.v1.md,
 * section 1, "Legacy names"). This only pins the text of the prelude: the behaviour is covered once the host runs on a
 * desktop JVM, where QuickJS can be executed in a unit test.
 */
class LegacyGlobalsTest {
    private val prelude: String = assertNotNull(
        LegacyGlobalsTest::class.java.getResourceAsStream("/nijiyome/host-prelude.js"),
        "host-prelude.js is not on the test classpath",
    ).bufferedReader().use { it.readText() }

    @Test
    fun legacyNamesAreBoundToTheCurrentFunctions() {
        for (name in listOf("fetch", "parse_html", "anitomy")) {
            assertTrue("g.__tatakai_${name}__ = g.__nijiyome_${name}__;" in prelude, "missing legacy alias for $name")
        }
    }

    @Test
    fun legacyNamesArePassedToTheBundleAfterFetch() {
        assertTrue("g.fetch,\n      g.__tatakai_fetch__, g.__tatakai_anitomy__, g.__tatakai_parse_html__);" in prelude)
    }
}
