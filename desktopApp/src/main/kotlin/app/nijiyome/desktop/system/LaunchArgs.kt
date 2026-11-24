// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.data.OAuthRedirect
import app.nijiyome.extensionserver.DeepLink
import app.nijiyome.torrent.PendingMagnetLink
import java.io.File
import java.net.URLDecoder

/** What a command line (this launch's, or one a second launch forwarded) asks the app to do. */
sealed interface LaunchTarget {
    /** A `magnet:` link or an existing `.torrent` file: the torrent library with it ready to add. */
    data class Torrent(val raw: String) : LaunchTarget

    /** `nijiyome://oauth/<provider>?…`: an OAuth redirect (the lists repository checks it). */
    data class OAuth(val uri: String) : LaunchTarget

    /** `nijiyome://user/<name>`: an AniList profile (Android's `WaveBLinks`); null name = the signed-in user's. */
    data class Profile(val name: String?) : LaunchTarget

    /** A local video file: the player on it. */
    data class VideoFile(val path: String) : LaunchTarget

    /**
     * `nijiyome://extensionserver/connect?…`: another PC's extension server pairing link. Only recognised here; the screen validates
     * it ([app.nijiyome.extensionserver.DeepLink]) and asks the user before pairing. [toString] hides the one-time code.
     */
    data class ExtensionServer(val uri: String) : LaunchTarget {
        override fun toString(): String = "ExtensionServer(…)"
    }
}

/**
 * The arguments of `nijiyome [--minimized] [link | file]`. Links and files are the first argument that is one (a desktop entry
 * passes `%u`, a file manager the file path). `nijiyome://extensionserver/connect` links open the extension servers screen, which
 * asks before pairing.
 */
data class LaunchArgs(val minimized: Boolean, val target: LaunchTarget?) {
    companion object {
        const val MINIMIZED = "--minimized"

        /** Longest link accepted from a command line or another launch. */
        private const val MAX_LINK = 8 * 1024

        fun parse(args: List<String>, isFile: (String) -> Boolean = { File(it).isFile }): LaunchArgs {
            val minimized = args.any { it == MINIMIZED }
            val target = args.asSequence().filter { !it.startsWith("--") }.mapNotNull { targetOf(it, isFile) }.firstOrNull()
            return LaunchArgs(minimized, target)
        }

        /** The target of one argument, or null when it is none of ours. */
        fun targetOf(raw: String, isFile: (String) -> Boolean = { File(it).isFile }): LaunchTarget? {
            val text = raw.trim()
            if (text.isEmpty() || text.length > MAX_LINK) return null
            if (text.startsWith(PendingMagnetLink.MAGNET_SCHEME + ":", ignoreCase = true)) return LaunchTarget.Torrent(text)
            if (text.startsWith(OAuthRedirect.SCHEME + "://", ignoreCase = true)) return deepLink(text)
            if (text.startsWith("file:", ignoreCase = true)) {
                val path = runCatching { File(java.net.URI(text)).path }.getOrNull() ?: return null
                return targetOf(path, isFile)
            }
            if (!isFile(text)) return null
            return if (text.endsWith(".torrent", ignoreCase = true)) LaunchTarget.Torrent(text) else LaunchTarget.VideoFile(text)
        }

        private fun deepLink(uri: String): LaunchTarget? {
            if (OAuthRedirect.providerOf(uri) != null) return LaunchTarget.OAuth(uri)
            val rest = uri.substring(OAuthRedirect.SCHEME.length + 3)
            val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            if (host.equals(DeepLink.HOST, ignoreCase = true)) return LaunchTarget.ExtensionServer(uri)
            if (host.equals(USER_HOST, ignoreCase = true)) {
                val name = rest.substringAfter('/', "").substringBefore('/').substringBefore('?').substringBefore('#')
                    .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                    .takeIf { it.isNotBlank() }
                return LaunchTarget.Profile(name)
            }
            return null
        }

        private const val USER_HOST = "user"
    }
}
