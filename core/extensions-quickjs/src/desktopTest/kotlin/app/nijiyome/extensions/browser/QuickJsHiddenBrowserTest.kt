// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.JsSession
import app.nijiyome.extensions.ManifestParser
import app.nijiyome.extensions.QuickJsEngine
import app.nijiyome.extensions.HostFetchRequest
import app.nijiyome.extensions.HostFetchResponse
import app.nijiyome.extensions.JsHost
import app.nijiyome.extensions.NetworkPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A `.kai` bundle that asks for the hidden browser, run in the real [QuickJsEngine] on desktop: with a browser it gets
 * `configureBrowserRuntime` and its `newBrowser` opens a real headless page; without one (no Chrome / Edge / Chromium) it gets no
 * browser at all, exactly as before 1.1.
 */
class QuickJsHiddenBrowserTest {
    private val manifest = ManifestParser.parse(
        """{"id":"browser-ext","name":"Browser Ext","version":"1.0.0","type":"onlinestream","description":"",
           "permissions":["network:domain:example.com"],"browserDomains":["example.com"]}""",
    )

    private val bundle = """
        'use strict';
        class BrowserExt {
          configureBrowserRuntime(rt) { this.rt = rt; }
          hasBrowser() { return !!this.rt; }
          async openAndClose() {
            const b = await this.rt.newBrowser({ timeout: 10 });
            let outside;
            try { await b.navigate('https://elsewhere.invalid/'); outside = 'navigated'; } catch (e) { outside = String(e.message); }
            await b.close();
            return outside;
          }
        }
        module.exports = BrowserExt;
    """.trimIndent()

    private class NoNet(override val networkPolicy: NetworkPolicy) : JsHost {
        override suspend fun fetch(request: HostFetchRequest): HostFetchResponse = error("no network in tests")
        override fun log(level: String, message: String) = Unit
    }

    private fun <T> session(runtime: CdpBrowserRuntime, block: suspend (JsSession) -> T): T = runBlocking {
        val s = QuickJsEngine(browserRuntime = runtime).open(manifest, bundle, NoNet(manifest.networkPolicy))
        try {
            block(s)
        } finally {
            s.close()
        }
    }

    @Test
    fun aBundleGetsTheBrowserOnlyWhenOneIsInstalled() {
        val none = CdpBrowserRuntime(ChromiumHost(executable = { null }, proxy = { null }))
        assertEquals("false", session(none) { it.invoke("hasBrowser", emptyList()).result.jsonPrimitive.content })

        val exe = LocalPages.browser() ?: return LocalPages.skip("aBundleGetsTheBrowserOnlyWhenOneIsInstalled (with a browser)")
        val host = ChromiumHost(executable = { exe }, proxy = { null })
        try {
            val real = CdpBrowserRuntime(host)
            session(real) { s ->
                assertEquals("true", s.invoke("hasBrowser", emptyList()).result.jsonPrimitive.content)
                // a real page was opened; the bridge and the runtime both refuse a URL outside browserDomains
                assertEquals("ChromeDP URL is outside the provider website", s.invoke("openAndClose", emptyList()).result.jsonPrimitive.content)
            }
        } finally {
            runBlocking { host.shutdown() }
        }
    }
}
