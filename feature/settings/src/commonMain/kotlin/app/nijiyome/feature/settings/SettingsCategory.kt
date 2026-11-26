// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.SettingsSection
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The 14 categories of Settings (artifact `data.js` `settingsCategories`, in that order). [SettingsSection] of `:core:model` stays the
 * deep-link type of `Route.SettingsSection`; [SettingsSection.toCategory] maps every value to its new home.
 */
enum class SettingsCategory(val id: String) {
    APPEARANCE("apariencia"),
    PLAYBACK("reproduccion"),
    ENHANCEMENTS("mejoras"),
    SUBTITLES("subtitulos"),
    SOURCES("fuentes"),
    ACCOUNTS("cuentas"),
    METADATA("metadatos"),
    LINK("link"),
    TRANSFERS("transferencias"),
    READERS("lectores"),
    NOTIFICATIONS("notificaciones"),

    /** Desktop only: tray, autostart, Discord, title bar, outbound proxy and the local media proxy. */
    SYSTEM("sistema"),
    DATA("datos"),
    ABOUT("acerca"),
}

/** Where an old section now lives. NETWORK goes to Sistema (FlareSolverr inside it moved to Fuentes de episodios). */
fun SettingsSection.toCategory(): SettingsCategory = when (this) {
    SettingsSection.APPEARANCE -> SettingsCategory.APPEARANCE
    SettingsSection.PLAYER -> SettingsCategory.PLAYBACK
    SettingsSection.METADATA -> SettingsCategory.METADATA
    SettingsSection.ACCOUNTS -> SettingsCategory.ACCOUNTS
    SettingsSection.LINK -> SettingsCategory.LINK
    SettingsSection.NETWORK -> SettingsCategory.SYSTEM
    SettingsSection.TORRENTS -> SettingsCategory.TRANSFERS
    SettingsSection.DOWNLOADS -> SettingsCategory.TRANSFERS
    SettingsSection.READER -> SettingsCategory.READERS
    SettingsSection.DATA -> SettingsCategory.DATA
    SettingsSection.ABOUT -> SettingsCategory.ABOUT
}

/** The categories shown on this device: Listas y cuentas needs the lists service, Sistema exists on desktop only. Pure, unit tested. */
internal fun settingsCategories(hasLists: Boolean, systemOffered: Boolean): List<SettingsCategory> =
    SettingsCategory.entries.filter { c ->
        (hasLists || c != SettingsCategory.ACCOUNTS) && (systemOffered || c != SettingsCategory.SYSTEM)
    }

internal fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.APPEARANCE -> Icons.Outlined.Palette
    SettingsCategory.PLAYBACK -> Icons.Outlined.PlayCircle
    SettingsCategory.ENHANCEMENTS -> Icons.Outlined.Hd
    SettingsCategory.SUBTITLES -> Icons.Outlined.Subtitles
    SettingsCategory.SOURCES -> Icons.Outlined.Extension
    SettingsCategory.ACCOUNTS -> Icons.Outlined.AccountCircle
    SettingsCategory.METADATA -> Icons.Outlined.Dataset
    SettingsCategory.LINK -> Icons.Outlined.Devices
    SettingsCategory.TRANSFERS -> Icons.Outlined.SwapVert
    SettingsCategory.READERS -> Icons.Outlined.MenuBook
    SettingsCategory.NOTIFICATIONS -> Icons.Outlined.Notifications
    SettingsCategory.SYSTEM -> Icons.Outlined.Computer
    SettingsCategory.DATA -> Icons.Outlined.Storage
    SettingsCategory.ABOUT -> Icons.Outlined.Info
}

internal fun SettingsCategory.titleRes(): StringResource = when (this) {
    SettingsCategory.APPEARANCE -> Res.string.rd_s10_cat_appearance
    SettingsCategory.PLAYBACK -> Res.string.rd_s10_cat_playback
    SettingsCategory.ENHANCEMENTS -> Res.string.rd_s10_cat_enhancements
    SettingsCategory.SUBTITLES -> Res.string.rd_s10_cat_subtitles
    SettingsCategory.SOURCES -> Res.string.rd_s10_cat_sources
    SettingsCategory.ACCOUNTS -> Res.string.rd_s10_cat_accounts
    SettingsCategory.METADATA -> Res.string.rd_s10_cat_metadata
    SettingsCategory.LINK -> Res.string.rd_s10_cat_link
    SettingsCategory.TRANSFERS -> Res.string.rd_s10_cat_transfers
    SettingsCategory.READERS -> Res.string.rd_s10_cat_readers
    SettingsCategory.NOTIFICATIONS -> Res.string.rd_s10_cat_notifications
    SettingsCategory.SYSTEM -> Res.string.rd_s10_cat_system
    SettingsCategory.DATA -> Res.string.rd_s10_cat_data
    SettingsCategory.ABOUT -> Res.string.rd_s10_cat_about
}

internal fun SettingsCategory.descRes(): StringResource = when (this) {
    SettingsCategory.APPEARANCE -> Res.string.rd_s10_cat_appearance_desc
    SettingsCategory.PLAYBACK -> Res.string.rd_s10_cat_playback_desc
    SettingsCategory.ENHANCEMENTS -> Res.string.rd_s10_cat_enhancements_desc
    SettingsCategory.SUBTITLES -> Res.string.rd_s10_cat_subtitles_desc
    SettingsCategory.SOURCES -> Res.string.rd_s10_cat_sources_desc
    SettingsCategory.ACCOUNTS -> Res.string.rd_s10_cat_accounts_desc
    SettingsCategory.METADATA -> Res.string.rd_s10_cat_metadata_desc
    SettingsCategory.LINK -> Res.string.rd_s10_cat_link_desc
    SettingsCategory.TRANSFERS -> Res.string.rd_s10_cat_transfers_desc
    SettingsCategory.READERS -> Res.string.rd_s10_cat_readers_desc
    SettingsCategory.NOTIFICATIONS -> Res.string.rd_s10_cat_notifications_desc
    SettingsCategory.SYSTEM -> Res.string.rd_s10_cat_system_desc
    SettingsCategory.DATA -> Res.string.rd_s10_cat_data_desc
    SettingsCategory.ABOUT -> Res.string.rd_s10_cat_about_desc
}

@Composable
internal fun SettingsCategory.title(): String = stringResource(titleRes())
