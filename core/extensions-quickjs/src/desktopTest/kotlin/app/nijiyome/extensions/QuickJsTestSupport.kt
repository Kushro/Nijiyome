// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import java.io.File

/** `contracts/fixtures/extensions`, found by walking up from the working directory (or `NIJIYOME_CONTRACTS_DIR`). */
internal val extensionFixtures: File by lazy {
    System.getenv("NIJIYOME_CONTRACTS_DIR")?.let { File(it, "fixtures/extensions") }?.takeIf { it.isDirectory }
        ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "contracts/fixtures/extensions") }
            .firstOrNull { it.isDirectory }
        ?: error("contracts/fixtures/extensions not found from ${System.getProperty("user.dir")}")
}

/**
 * A host that never leaves the machine: it applies the manifest gate like [OkHttpJsHost] (`Network permission denied` outside the
 * permissions) and answers every allowed request with `body:<url>`. Requests are recorded in [fetched].
 */
internal class OfflineJsHost(override val networkPolicy: NetworkPolicy) : JsHost {
    val fetched: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

    override suspend fun fetch(request: HostFetchRequest): HostFetchResponse {
        networkPolicy.requireAllowed(request.url)
        fetched += request.url
        return HostFetchResponse(request.url, 200, "OK", mapOf("content-type" to "text/plain"), "body:${request.url}".toByteArray())
    }

    override fun log(level: String, message: String) = Unit
}
