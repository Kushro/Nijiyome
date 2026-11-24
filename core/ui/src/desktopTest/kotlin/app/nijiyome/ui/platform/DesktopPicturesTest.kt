// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPicturesTest {
    private val home = Files.createTempDirectory("pictures-home").toFile()

    @Test
    fun theEnvironmentWins() {
        assertEquals(File("/x/shots"), DesktopPictures.dir(env = { if (it == "XDG_PICTURES_DIR") "/x/shots" else null }, home = home))
    }

    @Test
    fun userDirsIsReadWithHomeExpanded() {
        File(home, ".config").mkdirs()
        File(home, ".config/user-dirs.dirs").writeText("# comment\nXDG_DESKTOP_DIR=\"\$HOME/Desk\"\nXDG_PICTURES_DIR=\"\$HOME/Imágenes\"\n")
        assertEquals(File(home, "Imágenes"), DesktopPictures.dir(env = { null }, home = home))
    }

    @Test
    fun withoutUserDirsItIsPicturesInHome() {
        assertEquals(File(home, "Pictures"), DesktopPictures.dir(env = { null }, home = home))
        // a pictures dir disabled as "$HOME" falls back too
        File(home, ".config").mkdirs()
        File(home, ".config/user-dirs.dirs").writeText("XDG_PICTURES_DIR=\"\$HOME/\"\n")
        assertEquals(File(home, "Pictures"), DesktopPictures.dir(env = { null }, home = home))
    }

    @Test
    fun theShownPathAbbreviatesHome() {
        assertEquals("~/Imágenes/Nijiyome/a.png", DesktopPictures.shown(File(home, "Imágenes/Nijiyome/a.png"), home.path))
        assertEquals("/srv/a.png", DesktopPictures.shown(File("/srv/a.png"), home.path))
    }
}
