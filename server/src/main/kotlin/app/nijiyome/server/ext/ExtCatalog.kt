// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.ext

import app.nijiyome.extensions.Candidates
import app.nijiyome.extensions.ExtError
import app.nijiyome.extensions.ExtensionHost
import app.nijiyome.extensions.ExtensionManifest
import app.nijiyome.extensions.ManifestParser
import app.nijiyome.model.ExtensionCompat
import app.nijiyome.model.ExtensionRepository
import kotlinx.serialization.json.JsonObject
import java.io.File

/** One `.kai` extension installed on this PC, as the server lists it. */
data class CatalogEntry(
    val id: String,
    val name: String,
    val version: String,
    val kind: String,
    val enabled: Boolean,
    /** `full` | `partial` | `unsupported`. */
    val compat: String,
    val compatReason: String?,
)

/** Why an extension call failed: not installed (404), the route does not exist for it (422) or anything else (502). */
class CatalogException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NOT_INSTALLED, UNSUPPORTED_ROUTE, FAILED }
}

/** The PC's extension host, as the integrated extension server needs it. */
interface ExtensionCatalog {
    fun list(): List<CatalogEntry>
    fun manifest(id: String): ExtensionManifest?
    /** `manifest.json` and `bundle.js` of the installed [id], byte for byte; null when not installed or over the `.kai` limits. */
    fun files(id: String): Pair<ByteArray, ByteArray>?
    /** The routes `/resolve` runs for [id] (`stream` / `torrent` / `sources`), in run order. */
    fun candidateRoutes(id: String): List<String>
    suspend fun call(id: String, route: String, params: JsonObject): JsonObject
    suspend fun install(bytes: ByteArray): CatalogEntry
    suspend fun remove(id: String)
}

/** An empty catalog (tests, a PC without extensions). */
object EmptyCatalog : ExtensionCatalog {
    override fun list() = emptyList<CatalogEntry>()
    override fun manifest(id: String): ExtensionManifest? = null
    override fun files(id: String): Pair<ByteArray, ByteArray>? = null
    override fun candidateRoutes(id: String) = emptyList<String>()
    override suspend fun call(id: String, route: String, params: JsonObject): JsonObject = throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
    override suspend fun install(bytes: ByteArray): CatalogEntry = throw CatalogException(CatalogException.Kind.FAILED, "no extension host")
    override suspend fun remove(id: String) = throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
}

/**
 * The desktop app's own extension host: the installed list and installs / removals through [repository], the calls through
 * [host] (the same QuickJS runtime the app uses), the files under [root] (`<root>/<id>/{manifest.json,bundle.js}`). The ids come
 * from the installed list, never from a request path.
 */
class HostCatalog(private val repository: ExtensionRepository, private val host: ExtensionHost, private val root: File) : ExtensionCatalog {
    override fun list(): List<CatalogEntry> = repository.installed.value.map {
        CatalogEntry(it.id, it.name, it.version, it.type, it.enabled, it.compat.name.lowercase(), it.compatReason)
    }

    private fun installedDir(id: String): File? = if (list().any { it.id == id }) File(root, id) else null

    override fun manifest(id: String): ExtensionManifest? =
        installedDir(id)?.let { runCatching { ManifestParser.parse(File(it, "manifest.json").readText()) }.getOrNull() }

    override fun files(id: String): Pair<ByteArray, ByteArray>? {
        val dir = installedDir(id) ?: return null
        val m = File(dir, "manifest.json").takeIf { it.isFile && it.length() <= 1024 * 1024 } ?: return null
        val b = File(dir, "bundle.js").takeIf { it.isFile && it.length() <= MAX_BUNDLE_BYTES } ?: return null
        return m.readBytes() to b.readBytes()
    }

    override fun candidateRoutes(id: String): List<String> {
        val m = manifest(id) ?: return emptyList()
        val bundle = installedDir(id)?.let { runCatching { File(it, "bundle.js").readText() }.getOrNull() }.orEmpty()
        return Candidates.runOrder(Candidates.candidateRoutes(m, Candidates.exposesTorrentAll(bundle)))
    }

    override suspend fun call(id: String, route: String, params: JsonObject): JsonObject {
        val e = repository.installed.value.firstOrNull { it.id == id && it.enabled && it.compat != ExtensionCompat.UNSUPPORTED }
            ?: throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
        return try {
            host.call(e.id, route, params)
        } catch (x: ExtError.NotFound) {
            throw CatalogException(CatalogException.Kind.UNSUPPORTED_ROUTE, route)
        } catch (x: ExtError.NotImplemented) {
            throw CatalogException(CatalogException.Kind.UNSUPPORTED_ROUTE, route)
        } catch (x: ExtError) {
            throw CatalogException(CatalogException.Kind.FAILED, x.message ?: "extension failed")
        }
    }

    override suspend fun install(bytes: ByteArray): CatalogEntry {
        val e = repository.installFromBytes(bytes).getOrElse { throw CatalogException(CatalogException.Kind.FAILED, it.message ?: "invalid extension") }
        return CatalogEntry(e.id, e.name, e.version, e.type, e.enabled, e.compat.name.lowercase(), e.compatReason)
    }

    override suspend fun remove(id: String) {
        if (list().none { it.id == id }) throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
        repository.remove(id)
    }

    companion object {
        /** `KaiReader`'s bundle cap. */
        const val MAX_BUNDLE_BYTES = 16L * 1024 * 1024
    }
}
