// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

/**
 * The JavaScript both hidden browsers run in a page: Android's WebViews (`WebViewBrowserRuntime`, `WebViewEmbedSniffer`) and the
 * desktop's Chromium driven over CDP. Moved here unchanged from those Android classes (1.1, F4) so both platforms run the same text.
 */
object BrowserScripts {
    /**
     * Wraps an expression (a trailing `;` is tolerated) so it returns `{ ok, v | e }` as a JSON string: an exception surfaces as an
     * error and `undefined` becomes `null`. Promises are not awaited (same as desktop).
     */
    fun wrapExpression(js: String): String {
        val expr = js.trim().trimEnd { it == ';' || it.isWhitespace() }
        return "(function(){try{var v=(\n$expr\n);return JSON.stringify({ok:true,v:v===undefined?null:v});}" +
            "catch(e){return JSON.stringify({ok:false,e:String(e&&e.message||e)});}})()"
    }

    /** Starts the first `<video>` and clicks the first thing that looks like a play button (also inside same-origin iframes). */
    const val NUDGE_JS: String = """(function(){try{
var docs=[document];document.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument)docs.push(f.contentDocument);}catch(e){}});
docs.forEach(function(d){
 d.querySelectorAll('video').forEach(function(v){try{v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}});
 var b=d.querySelector('.jw-icon-display,.vjs-big-play-button,.plyr__control--overlaid,[aria-label*="Play" i],button[class*="play" i],div[class*="play" i]');
 if(b){try{b.click();}catch(e){}}
});}catch(e){}})();"""
}
