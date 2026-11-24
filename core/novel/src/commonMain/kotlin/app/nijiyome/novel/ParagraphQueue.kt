// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

/**
 * The paragraphs of the chapter being narrated and the cursor (pure, JVM-tested): blank paragraphs are skipped, pause keeps the
 * cursor, the end of the chapter empties it.
 */
class ParagraphQueue {
    private var paragraphs: List<String> = emptyList()
    private var index: Int = 0

    val isEmpty: Boolean get() = current() == null

    fun load(paragraphs: List<String>, from: Int) {
        this.paragraphs = paragraphs
        index = from.coerceIn(0, paragraphs.size)
        skipBlank()
    }

    /** (index, text) of the paragraph to read now; null at the end. */
    fun current(): Pair<Int, String>? = paragraphs.getOrNull(index)?.let { index to it }

    fun peekNext(): Pair<Int, String>? {
        var i = index + 1
        while (i < paragraphs.size && paragraphs[i].isBlank()) i++
        return paragraphs.getOrNull(i)?.let { i to it }
    }

    fun advance() {
        index++
        skipBlank()
    }

    fun clear() {
        paragraphs = emptyList()
        index = 0
    }

    private fun skipBlank() {
        while (index < paragraphs.size && paragraphs[index].isBlank()) index++
    }
}
