// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The local test site of the hidden browser (no internet), the same one the F4 proof of concept used:
 *
 * * `/` sets an HttpOnly `sess` cookie and runs a JS challenge (after 300 ms writes `js_ok=45` from JS, then goes to `/watch`).
 * * `/watch` is 403 without both cookies; else builds from JS an iframe to `/embed` on the OTHER host (`127.0.0.1` <-> `localhost`, a
 *   different site: Chromium runs it out of process) and loads an "ad" script (`/ads/pop.js`).
 * * `/player` is `/watch` without the challenge; `/player-ad` also loads an ad from the other host.
 * * `/embed` wants a Referer of the page's site; it builds a `<video>` and a `.vjs-big-play-button`. Only a click (the nudge) makes it
 *   ask for `/hls/master.m3u8?token=222` by XHR with an `X-Player` header, as hls.js does.
 * * `/slow` never finishes; `/gone` is a 404; `/still` is a page with no media at all; `/auth` asks for a password (401).
 */
class LocalPages : AutoCloseable {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val server = MockWebServer()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    val port: Int get() = server.port
    val base: String get() = "http://127.0.0.1:$port"
    val other: String get() = "http://localhost:$port"

    fun paths(host: String? = null): List<String> =
        requests.filter { host == null || it.headers["Host"].orEmpty().startsWith(host) }.map { it.url.encodedPath }

    override fun close() = server.close()

    private fun html(body: String, vararg headers: Pair<String, String>): MockResponse {
        val b = MockResponse.Builder().code(200).addHeader("Content-Type", "text/html; charset=utf-8").addHeader("Cache-Control", "no-store")
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        return b.body("<!doctype html><html><head><meta charset=utf-8><title>t</title></head><body>$body</body></html>").build()
    }

    private fun respond(req: RecordedRequest): MockResponse {
        val hostHeader = req.headers["Host"].orEmpty()
        val otherHost = (if (hostHeader.startsWith("127.0.0.1")) "localhost" else "127.0.0.1") + ":" + port
        val cookies = req.headers["Cookie"].orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val iframe = "var f=document.createElement('iframe');f.src='http://$otherHost/embed?id=42';document.body.appendChild(f);"
        return when (req.url.encodedPath) {
            "/" -> html(
                """<div id=n data-n="6">checking your browser...</div><script>
setTimeout(function(){var n=+document.getElementById('n').dataset.n;document.cookie='js_ok='+(n*7+3)+'; path=/';location.replace('/watch');},300);
</script>""",
                "Set-Cookie" to "sess=abc123; Path=/; HttpOnly",
            )
            "/watch" -> if (cookies["sess"] == null || cookies["js_ok"] != "45") {
                MockResponse.Builder().code(403).body("challenge failed").build()
            } else {
                html("<h1 id=title>Episode 1</h1><script>$iframe var a=document.createElement('script');a.src='/ads/pop.js';document.body.appendChild(a);window.__ready=true;</script>")
            }
            "/player" -> html("<h1>Player</h1><script>$iframe</script>")
            "/player-ad" -> html("<h1>Player</h1><script>$iframe var a=document.createElement('script');a.src='http://$otherHost/ads/pop.js';document.body.appendChild(a);</script>")
            "/embed" -> if (!req.headers["Referer"].orEmpty().startsWith("http://$otherHost/")) {
                MockResponse.Builder().code(403).body("no referer").build()
            } else {
                html(
                    """<video id=v muted></video><button class="vjs-big-play-button">Play</button><script>
document.querySelector('.vjs-big-play-button').addEventListener('click',function(){var t=(42*13).toString(16);
var x=new XMLHttpRequest();x.open('GET','/hls/master.m3u8?token='+t);x.setRequestHeader('X-Player','poc');x.send();});
</script>""",
                )
            }
            "/hls/master.m3u8" -> if (req.url.queryParameter("token") == "222") {
                MockResponse.Builder().code(200).addHeader("Content-Type", "application/vnd.apple.mpegurl")
                    .body("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n").build()
            } else {
                MockResponse.Builder().code(403).body("bad token").build()
            }
            "/ads/pop.js" -> MockResponse.Builder().code(200).addHeader("Content-Type", "application/javascript").body("window.__ad=true;").build()
            "/slow" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<p>never</p>").headersDelay(60, TimeUnit.SECONDS).build()
            "/gone" -> MockResponse.Builder().code(404).body("gone").build()
            "/auth" -> MockResponse.Builder().code(401).addHeader("WWW-Authenticate", "Basic realm=\"test\"")
                .addHeader("Content-Type", "text/html").body("<!doctype html><body>auth required</body>").build()
            "/still" -> html("<p>nothing to play here</p>")
            else -> MockResponse.Builder().code(404).body("not found").build()
        }
    }

    companion object {
        /** A browser to run the browser tests with, or null (they are skipped then, saying so). */
        fun browser(): ChromiumExecutable? = ChromiumLocator().find(System.getenv("NIJIYOME_TEST_BROWSER"))

        /** Policy for the local site: plain http on 127.0.0.1 / localhost with a port (BrowserPolicy wants https and no port). */
        val localPolicy: (String, List<String>) -> Boolean = { url, domains ->
            val m = Regex("^http://([a-z0-9.]+):\\d+(/.*)?$").find(url)
            m != null && m.groupValues[1] in domains
        }

        /**
         * A host for the local site: the gateway lets this machine through (the test pages are on 127.0.0.1 / localhost), which the real
         * rules refuse ([BrowserGatewayTest], [HiddenBrowserSsrfTest]).
         */
        fun host(exe: ChromiumExecutable, idleMs: Long = ChromiumHost.DEFAULT_IDLE_MS, transport: ChromiumHost.Transport = ChromiumHost.Transport.PIPE) =
            ChromiumHost(
                executable = { exe },
                proxy = { null },
                idleMs = idleMs,
                transport = transport,
                destinationAllowed = { _, _ -> true },
                refuseLocalNames = false,
            )

        fun skip(test: String) = println("SKIPPED $test: no Chrome / Edge / Chromium on this machine")
    }
}
