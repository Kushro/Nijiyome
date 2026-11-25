// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.LoadState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hito 6 (D1): the bundled licenses and the "Guardar como" export of the desktop Settings. */
class DesktopSettingsPlatformTest {
    @Test
    fun licensesComeFromTheIndexNoticesFirstThenTheFonts() {
        val files = mapOf(
            "licenses/index.txt" to "OFL-Outfit.txt\nTHIRD_PARTY_NOTICES.md\nOFL-JetBrainsMono.txt\n",
            "licenses/THIRD_PARTY_NOTICES.md" to "# Notices\n\nlibmpv: GPL\n\n\nOkHttp: Apache-2.0\n",
            "licenses/OFL-Outfit.txt" to "Outfit OFL\n",
            "licenses/OFL-JetBrainsMono.txt" to "Mono OFL\n",
        )
        val loaded = loadBundledLicenses(files::get) as LoadState.Ok
        assertEquals(
            listOf(
                "── THIRD_PARTY_NOTICES.md ──", "# Notices", "libmpv: GPL", "OkHttp: Apache-2.0",
                "── OFL-JetBrainsMono.txt ──", "Mono OFL",
                "── OFL-Outfit.txt ──", "Outfit OFL",
            ),
            loaded.value,
        )
        assertEquals(LoadState.Ok(emptyList()), loadBundledLicenses { null }, "nothing bundled: the sheet says so")
    }

    /** The real build output: desktopApp's generated `licenses/` holds the notices and the font licenses (3 of the base type scale + 8 of the Sparkles directions) (when built). */
    @Test
    fun theRepositoryNoticesAreWhatGetsBundled() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "THIRD_PARTY_NOTICES.md").isFile }
        val fonts = File(root, "core/designsystem/src/androidMain/assets/licenses").listFiles().orEmpty().filter { it.name.startsWith("OFL-") }
        assertEquals(11, fonts.size, "the OFL texts the build packages")
        val read = { path: String ->
            val name = path.removePrefix("licenses/")
            when (name) {
                "index.txt" -> (listOf("THIRD_PARTY_NOTICES.md") + fonts.map { it.name }).joinToString("\n")
                "THIRD_PARTY_NOTICES.md" -> File(root, name).readText()
                else -> fonts.firstOrNull { it.name == name }?.readText()
            }
        }
        val paragraphs = (loadBundledLicenses(read) as LoadState.Ok).value
        assertTrue(paragraphs.first() == "── THIRD_PARTY_NOTICES.md ──")
        assertTrue(paragraphs.any { it.contains("SIL OPEN FONT LICENSE", ignoreCase = true) })
    }

    @Test
    fun anExportIsWrittenWholeWithAJsonName() {
        val dir = Files.createTempDirectory("export").toFile()
        try {
            val target = withJsonExtension(File(dir, "nijiyome-lists"))
            assertEquals("nijiyome-lists.json", target.name)
            assertEquals("keep.txt", withJsonExtension(File(dir, "keep.txt")).name)
            assertTrue(writeExport(target, "[1]"))
            assertEquals("[1]", target.readText())
            assertTrue(writeExport(target, "[2]"), "an existing file is replaced")
            assertEquals("[2]", target.readText())
            assertEquals(listOf("nijiyome-lists.json"), dir.list()!!.toList(), "no temporary file left")
            assertFalse(writeExport(File(dir, "missing/sub/x.json"), "[]"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
