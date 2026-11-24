// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.model.NovelReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NovelFontsTest {
    private val georgia = FontFamily.Cursive // a stand-in: any family the lookup returns

    @Test
    fun withoutInstalledFontsThePresetsKeepTheirFallbacks() {
        val none: (String) -> FontFamily? = { null }
        assertSame(FontFamily.Serif, novelFontFamily("georgia", null, none))
        assertSame(FontFamily.Serif, novelFontFamily("lora", null, none))
        assertSame(FontFamily.SansSerif, novelFontFamily("dyslexic", null, none))
        assertSame(FontFamily.Serif, novelFontFamily("serif", null, none))
        assertSame(FontFamily.SansSerif, novelFontFamily("sans", null, none))
        assertSame(FontFamily.Default, novelFontFamily("system", null, none))
        assertSame(FontFamily.Default, novelFontFamily("family:Nope", null, none))
    }

    @Test
    fun installedFamiliesAreFoundByName() {
        val asked = mutableListOf<String>()
        val lookup: (String) -> FontFamily? = { name -> asked += name; georgia }
        assertSame(georgia, novelFontFamily("georgia", null, lookup))
        assertSame(georgia, novelFontFamily("lora", null, lookup))
        assertSame(georgia, novelFontFamily("dyslexic", null, lookup))
        assertSame(georgia, novelFontFamily("family:Noto Serif", null, lookup))
        assertEquals(listOf("Georgia", "Lora", "OpenDyslexic", "Noto Serif"), asked)
        assertSame(FontFamily.Monospace, novelFontFamily("mono", null, lookup))
    }

    @Test
    fun desktopOffersMonoAndKeepsInstalledFamiliesInTheSettings() {
        assertEquals(NOVEL_FONTS, novelFonts(desktop = false))
        assertEquals(listOf("serif", "sans", "georgia", "lora", "dyslexic", "mono", "system", "custom"), novelFonts(desktop = true))
        assertEquals("family:Noto Serif", NovelReaderSettings(font = "family:Noto Serif").sanitized().font)
        assertEquals("mono", NovelReaderSettings(font = "mono").sanitized().font)
        assertEquals("serif", NovelReaderSettings(font = "family:  ").sanitized().font)
        assertEquals("Noto Serif", NovelReaderSettings.installedFamily("family:Noto Serif"))
    }

    @Test
    fun familyFilterPutsPrefixMatchesFirst() {
        val all = listOf("DejaVu Sans", "Noto Sans", "Sans Mono", "Serif")
        assertEquals(listOf("Sans Mono", "DejaVu Sans", "Noto Sans"), filterFamilies(all, "sans"))
        assertEquals(all, filterFamilies(all, " "))
    }
}
