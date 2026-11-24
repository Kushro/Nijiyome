// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The packed `synthetic-full.kai` running in the real [QuickJsEngine] (desktop JVM natives of quickjs-kt), checked against
 * `synthetic-full/expected.stream.json`. Same assertions as `ExtensionHostTest` in :core:extensions, which replays the bundle by hand.
 * Desktop only: the Android host test resolves the Android variant of quickjs-kt, whose natives do not load on the build machine.
 */
class QuickJsPackedExtensionTest {
    private val expected = Json.parseToJsonElement(File(extensionFixtures, "synthetic-full/expected.stream.json").readText()).jsonObject

    private fun pkg(name: String): KaiPackage = File(extensionFixtures, "packed/$name.kai").inputStream().use { KaiReader.read(it) }

    private fun host(pkg: KaiPackage): ExtensionHost =
        ExtensionHost(QuickJsEngine(), { manifest -> OfflineJsHost(manifest.networkPolicy) })
            .also { it.register(LoadedExtension(pkg.manifest, pkg.bundle, pkg.compat.level, enabled = true)) }

    @Test
    fun streamRouteMatchesTheFixture() = runBlocking {
        val pkg = pkg("synthetic-full")
        assertEquals(CompatLevel.FULL, pkg.compat.level)
        val request = expected["request"]!!.jsonObject
        val body = host(pkg).call("synthetic-full", request["route"]!!.jsonPrimitive.content, request["params"]!!.jsonObject)

        val sources = body["sources"]!!.jsonArray
        val want = expected["sources"]!!.jsonArray
        assertEquals(expected["count"]!!.jsonPrimitive.int, body["count"]!!.jsonPrimitive.int)
        assertEquals(want.size, sources.size)
        want.forEachIndexed { i, w ->
            val wo = w.jsonObject
            val g = sources[i].jsonObject
            for (key in listOf("providerName", "type", "quality", "url", "audioLanguage")) {
                assertEquals(wo[key]!!.jsonPrimitive.content, g[key]!!.jsonPrimitive.content, "sources[$i].$key")
            }
            assertEquals(wo["providerPriority"]!!.jsonPrimitive.int, g["providerPriority"]!!.jsonPrimitive.int, "sources[$i].providerPriority")
            assertEquals(wo["subtitleCount"]!!.jsonPrimitive.int, g["subtitles"]!!.jsonArray.size, "sources[$i].subtitleCount")
            val headers = g["headers"]!!.jsonObject
            for ((k, v) in wo["headers"]!!.jsonObject) assertEquals(v.jsonPrimitive.content, headers[k]?.jsonPrimitive?.content, "sources[$i].headers.$k")
        }
        val statuses = body["providerStatus"]!!.jsonArray.associate {
            it.jsonObject["provider"]!!.jsonPrimitive.content to it.jsonObject["status"]!!.jsonPrimitive.content
        }
        val wantStatuses = expected["providerStatus"]!!.jsonArray.associate {
            it.jsonObject["provider"]!!.jsonPrimitive.content to it.jsonObject["status"]!!.jsonPrimitive.content
        }
        assertEquals(wantStatuses, statuses)
    }

    @Test
    fun providersAndCustomHomeRoutes() = runBlocking {
        val h = host(pkg("synthetic-full"))
        val providers = expected["providers"]!!.jsonObject
        val listed = h.call("synthetic-full", providers["route"]!!.jsonPrimitive.content)
        assertTrue(listed.toString().let { text -> providers["names"]!!.jsonArray.all { "\"${it.jsonPrimitive.content}\"" in text } }, "$listed")

        val custom = expected["customHome"]!!.jsonObject
        val home = h.call("synthetic-full", custom["route"]!!.jsonPrimitive.content, custom["params"]!!.jsonObject)
        assertTrue("\"${custom["firstItemId"]!!.jsonPrimitive.content}\"" in home.toString(), "$home")
    }

    @Test
    fun fetchOutsideThePermissionsIsRejectedInsideTheBundle() = runBlocking {
        val pkg = pkg("synthetic-full")
        val gate = expected["networkGate"]!!.jsonObject
        val session = QuickJsEngine().open(pkg.manifest, pkg.bundle, OfflineJsHost(pkg.manifest.networkPolicy))
        try {
            val result = session.invoke(gate["bundleMethod"]!!.jsonPrimitive.content, emptyList())
            assertEquals(gate["expectedMessage"]!!.jsonPrimitive.content, result.result.jsonPrimitive.content)
        } finally {
            session.close()
        }
    }

    @Test
    fun theStreamParamsReachTheBundle() = runBlocking {
        // A different episode gives a different token (sha1 of "1:4" through require('crypto') and the host hash).
        val body = host(pkg("synthetic-full")).call("synthetic-full", "stream", buildJsonObject {
            put("anilistId", 1); put("episode", 4); put("titles", JsonArray(listOf(JsonPrimitive("Synthetic")))); put("stream", "0")
        })
        val url = body["sources"]!!.jsonArray.first().jsonObject["url"]!!.jsonPrimitive.content
        assertTrue(url.startsWith("https://cdn.alpha.example.invalid/hls/1/4/master.m3u8?t="), url)
        assertTrue(!url.endsWith("a28b1e3e"), url)
    }
}
