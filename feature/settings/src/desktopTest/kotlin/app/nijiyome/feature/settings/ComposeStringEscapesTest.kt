// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compose Resources does not understand Android's escapes (docs/patron-modulo-kmp.md §6): `\'`, `\"` and `%%` would be shown as they
 * are. 1.1 review #11 found `vendor\'s` in `settings_hbrowser_sandboxed`; this keeps every string of this module free of them.
 */
class ComposeStringEscapesTest {
    @Test
    fun noAndroidEscapesInComposeStrings() {
        val dir = File("src/commonMain/composeResources")
        val files = dir.walkTopDown().filter { it.isFile && it.name == "strings.xml" }.toList()
        assertTrue(files.size >= 2, "found the strings of ${dir.absolutePath}")
        val bad = files.flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, line) -> "\\'" in line || "\\\"" in line || "%%" in line }
                .map { (i, line) -> "${f.parentFile.name}/${f.name}:${i + 1}: ${line.trim()}" }
        }
        assertTrue(bad.isEmpty(), "Android escapes in Compose Resources:\n" + bad.joinToString("\n"))
    }
}
