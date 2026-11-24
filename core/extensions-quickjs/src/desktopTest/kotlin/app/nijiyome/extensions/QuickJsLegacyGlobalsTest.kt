// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The legacy host names (contracts/extension-host/globals.v1.md, section 1, "Legacy names") executed in the real [QuickJsEngine]:
 * a bundle built for the previous host calls them as a bare identifier (the extra bundle parameters) or through `globalThis`, and
 * gets the same behaviour as with the `__nijiyome_*__` names. Desktop only, like [QuickJsPackedExtensionTest].
 */
class QuickJsLegacyGlobalsTest {
    private val manifest = ManifestParser.parse(
        """{"id":"legacy-globals","name":"Legacy Globals","version":"1.0.0","type":"onlinestream",
           "description":"","permissions":["network:domain:api.example.invalid"]}""",
    )

    private val bundle = """
        'use strict';
        async function read(f, url) {
          try { const r = await f(url); return r.status + ' ' + (await r.text()); } catch (e) { return 'error ' + String(e && e.message); }
        }
        function scrape(parse, html) {
          const ${'$'} = parse(html);
          const items = [];
          ${'$'}.find('li').each(function (i, el) { items.push(el.text()); });
          return ${'$'}.find('a').attr('href') + '|' + items.join(',');
        }
        class LegacyGlobals {
          bareFetch(url) { return read(__tatakai_fetch__, url); }
          globalFetch(url) { return read(globalThis.__tatakai_fetch__, url); }
          currentFetch(url) { return read(__nijiyome_fetch__, url); }
          bareParse(html) { return scrape(__tatakai_parse_html__, html); }
          globalParse(html) { return scrape(globalThis.__tatakai_parse_html__, html); }
          currentParse(html) { return scrape(__nijiyome_parse_html__, html); }
          sameFunctions() {
            return __tatakai_fetch__ === __nijiyome_fetch__ && globalThis.__tatakai_fetch__ === globalThis.fetch &&
              __tatakai_parse_html__ === __nijiyome_parse_html__ && globalThis.__tatakai_parse_html__ === globalThis.__nijiyome_parse_html__ &&
              __tatakai_anitomy__ === __nijiyome_anitomy__ && globalThis.__tatakai_anitomy__ === globalThis.__nijiyome_anitomy__ &&
              typeof __tatakai_anitomy__('x.mkv') === 'object';
          }
        }
        module.exports = LegacyGlobals;
    """.trimIndent()

    private fun <T> withSession(block: suspend (JsSession, OfflineJsHost) -> T): T = runBlocking {
        val host = OfflineJsHost(manifest.networkPolicy)
        val session = QuickJsEngine().open(manifest, bundle, host)
        try {
            block(session, host)
        } finally {
            session.close()
        }
    }

    private suspend fun JsSession.text(method: String, arg: String): String =
        invoke(method, listOf(JsonPrimitive(arg))).result.jsonPrimitive.content

    @Test
    fun legacyFetchBehavesLikeTheCurrentName() = withSession { session, host ->
        val allowed = "https://api.example.invalid/list"
        val want = "200 body:$allowed"
        assertEquals(want, session.text("currentFetch", allowed))
        assertEquals(want, session.text("bareFetch", allowed))
        assertEquals(want, session.text("globalFetch", allowed))
        assertEquals(listOf(allowed, allowed, allowed), host.fetched.toList())
    }

    @Test
    fun legacyFetchKeepsThePermissionGate() = withSession { session, host ->
        val blocked = "https://blocked.example.invalid/x"
        val want = "error ${ExtError.PermissionDenied.MESSAGE}"
        assertEquals(want, session.text("currentFetch", blocked))
        assertEquals(want, session.text("bareFetch", blocked))
        assertEquals(want, session.text("globalFetch", blocked))
        assertEquals(emptyList(), host.fetched.toList())
    }

    @Test
    fun legacyParseHtmlBehavesLikeTheCurrentName() = withSession { session, _ ->
        val html = """<ul><li>One</li><li>Two</li></ul><a href="/watch/1">Watch</a>"""
        val want = "/watch/1|One,Two"
        assertEquals(want, session.text("currentParse", html))
        assertEquals(want, session.text("bareParse", html))
        assertEquals(want, session.text("globalParse", html))
    }

    @Test
    fun legacyNamesAreTheSameFunctionObjects() = withSession { session, _ ->
        assertEquals(JsonPrimitive(true), session.invoke("sameFunctions", emptyList()).result)
    }
}
