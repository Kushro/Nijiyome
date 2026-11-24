// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import java.util.Properties

/**
 * What the build put into `nijiyome-build.properties` (desktopApp/build.gradle.kts, from `gradle.properties`, `local.properties`
 * or the environment, never committed): the version and the public client ids of the desktop OAuth clients and of the Discord
 * application. An environment variable of the same name overrides each one at run time (development). Blank = not configured:
 * the feature that needs it stays disabled and says so.
 */
data class BuildInfo(
    val version: String,
    val anilistClientId: String?,
    val malClientId: String?,
    val discordAppId: String?,
) {
    companion object {
        const val RESOURCE = "/nijiyome-build.properties"
        const val VERSION = "NIJIYOME_VERSION"
        const val ANILIST = "NIJIYOME_ANILIST_CLIENT_ID_DESKTOP"
        const val MAL = "NIJIYOME_MAL_CLIENT_ID_DESKTOP"
        const val DISCORD = "NIJIYOME_DISCORD_APP_ID"

        fun load(env: (String) -> String? = System::getenv): BuildInfo {
            val p = Properties()
            BuildInfo::class.java.getResourceAsStream(RESOURCE)?.use(p::load)
            return of(p, env)
        }

        fun of(p: Properties, env: (String) -> String?): BuildInfo {
            fun value(key: String): String? = (env(key)?.takeIf { it.isNotBlank() } ?: p.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }
            return BuildInfo(
                version = value(VERSION).orEmpty(),
                anilistClientId = value(ANILIST),
                malClientId = value(MAL),
                discordAppId = value(DISCORD)?.takeIf { id -> id.all(Char::isDigit) },
            )
        }
    }
}
