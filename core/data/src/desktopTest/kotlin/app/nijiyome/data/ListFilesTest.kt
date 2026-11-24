// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.database.open
import app.nijiyome.model.AppException
import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.MediaRef
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.1 (F6): the list files of other apps (fixtures in resources/list-files) and the CSV export. */
class ListFilesTest {
    private val now = 1_800_000_000_000L

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/list-files/$name")) { name }.readText()

    @Test
    fun malXmlExport() {
        val file = parseListFile(fixture("mal-export.xml"), now)
        assertEquals(ListFileFormat.MAL_XML, file.format)
        assertEquals(2, file.skipped) // id 0 and an unknown status
        val byId = file.entries.associateBy { it.ref.malId }
        assertEquals(listOf(21, 1, 5114, 30), file.entries.map { it.ref.malId })
        assertTrue(file.entries.all { it.ref.anilistId == null && it.updatedAtMs == now && it.poster == null })

        val op = byId.getValue(21)
        assertEquals("One Piece", op.title)
        assertEquals(ListStatus.WATCHING, op.status)
        assertEquals(1100, op.progress)
        assertNull(op.totalEpisodes) // 0 = unknown
        assertEquals(90, op.score)
        assertEquals("2020-01-02", op.startedAt)
        assertNull(op.completedAt) // 0000-00-00
        assertNull(op.notes) // empty CDATA

        val bebop = byId.getValue(1)
        assertEquals("Cowboy & Bebop — é", bebop.title)
        assertEquals(ListStatus.COMPLETED, bebop.status)
        assertEquals(26, bebop.totalEpisodes)
        assertEquals(100, bebop.score)
        assertEquals("2019-06-10", bebop.completedAt)
        assertEquals("See you, space cowboy, \"again\"", bebop.notes)

        assertEquals(ListStatus.REPEATING, byId.getValue(5114).status) // watching + my_rewatching 1
        assertNull(byId.getValue(5114).score)
        assertEquals(ListStatus.PLANNING, byId.getValue(30).status) // numeric status of older exports
    }

    @Test
    fun aniListDumpIsDeduplicatedAndScoresAreScaled() {
        val file = parseListFile(fixture("anilist-dump.json"), now)
        assertEquals(ListFileFormat.ANILIST, file.format)
        assertEquals(listOf(16498, 1), file.entries.map { it.ref.anilistId })
        assertEquals(1, file.skipped) // the unknown status
        val aot = file.entries[0]
        assertEquals(MediaRef(anilistId = 16498, malId = 16498), aot.ref)
        assertEquals(ListStatus.WATCHING, aot.status)
        assertEquals(85, aot.score) // POINT_10_DECIMAL 8.5
        assertEquals(12, aot.progress)
        assertEquals(25, aot.totalEpisodes)
        assertEquals(1_700_000_000_000L, aot.updatedAtMs)
        assertEquals("2023-04", aot.startedAt) // the same partial date as the AniList sync (fuzzyDate)
        assertNull(aot.notes)
        assertEquals("https://img.example/aot.jpg", aot.poster)
        assertEquals(90, file.entries[1].score) // POINT_100 kept
        // the same shapes without `data` and as a bare MediaList array
        val bare = """{"MediaListCollection":{"lists":[{"entries":[{"mediaId":5,"status":"PLANNING","media":{"id":5}}]}]}}"""
        assertEquals(listOf(5), parseListFile(bare, now).entries.map { it.ref.anilistId })
        val array = """[{"mediaId":6,"status":"DROPPED","score":3,"media":{"id":6,"title":{"romaji":"Six"}}}]"""
        val six = parseListFile(array, now).entries.single()
        assertEquals(ListStatus.DROPPED, six.status)
        assertEquals(30, six.score)
        assertEquals(now, six.updatedAtMs) // no updatedAt in the dump
    }

    @Test
    fun tatakaiNextListFile() {
        val file = parseListFile(fixture("tatakai-lists.json"), now)
        assertEquals(ListFileFormat.TATAKAI, file.format)
        assertEquals(2, file.skipped) // no id; unknown status
        val (op, fma) = file.entries
        assertEquals(MediaRef(anilistId = 21, malId = 21), op.ref)
        assertEquals(ListStatus.WATCHING, op.status)
        assertEquals(1105, op.progress)
        assertEquals(95, op.score)
        assertTrue(op.favorite)
        assertEquals(1_731_000_000_000L, op.updatedAtMs) // seconds in the file
        assertEquals("https://img.example/op.jpg", op.poster)
        // the id taken from media_id ("mal:5114"), no time = now
        assertEquals(MediaRef(malId = 5114), fma.ref)
        assertEquals(ListStatus.REPEATING, fma.status)
        assertEquals(64, fma.totalEpisodes)
        assertEquals(now, fma.updatedAtMs)
        assertEquals(false, fma.favorite)
    }

    @Test
    fun nijiyomeJsonStillReads() {
        val entries = listOf(
            ListEntry(MediaRef(anilistId = 21), "One Piece", null, ListStatus.WATCHING, progress = 3, updatedAtMs = 5_000),
            ListEntry(MediaRef(malId = 1), "Bebop", "https://p", ListStatus.COMPLETED, score = 80, favorite = true, updatedAtMs = 6_000),
        )
        val file = parseListFile(exportEntries(entries), now)
        assertEquals(ListFileFormat.NIJIYOME, file.format)
        assertEquals(entries, file.entries)
        assertEquals(entries, parseListFile("""{"entries":${exportEntries(entries)}}""", now).entries)
        assertEquals(ListFileFormat.NIJIYOME, parseListFile("[]", now).format)
    }

    @Test
    fun otherFilesAreRefused() {
        assertFailsWith<IllegalArgumentException> { parseListFile("<html><body>nope</body></html>", now) }
        assertFailsWith<IllegalArgumentException> { parseListFile("title,status\nx,y", now) }
        assertFailsWith<IllegalArgumentException> { parseListFile("""{"hello":"world"}""", now) }
    }

    @Test
    fun csvExportQuotesLikeTatakaiNext() {
        val csv = exportEntriesCsv(
            listOf(
                ListEntry(MediaRef(anilistId = 1, malId = 1), "Cowboy Bebop, \"Session\"", null, ListStatus.COMPLETED, progress = 26, totalEpisodes = 26, score = 100, updatedAtMs = 1_690_000_000_000, notes = "line 1\nline 2", startedAt = "2019-05-01"),
                ListEntry(MediaRef(malId = 5114), "FMA", null, ListStatus.PLANNING, updatedAtMs = 2_000),
            ),
        )
        val lines = csv.split("\r\n")
        assertEquals(LIST_CSV_HEADER, lines[0])
        assertEquals("1,1,1,\"Cowboy Bebop, \"\"Session\"\"\",completed,26,26,100,2019-05-01,,0,\"line 1\nline 2\",1690000000", lines[1])
        assertEquals("mal:5114,,5114,FMA,planning,0,,,,,0,,2", lines[2])
        assertEquals("", lines[3])
        assertEquals(4, lines.size)
    }

    @Test
    fun csvCellsThatLookLikeFormulasAreNeutralized() {
        // v1.1 review #18: a title or note from someone else's list must not become a live formula in a spreadsheet
        assertEquals("'=HYPERLINK(\"http://x\",\"y\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, csvField("=HYPERLINK(\"http://x\",\"y\")"))
        assertEquals("'+1", csvField("+1"))
        assertEquals("'-2+3", csvField("-2+3"))
        assertEquals("'@SUM(A1)", csvField("@SUM(A1)"))
        assertEquals("'\tx", csvField("\tx"))
        assertEquals("One Piece", csvField("One Piece"))
        assertEquals("2019-05-01", csvField("2019-05-01"))
        assertEquals("", csvField(""))
        val csv = exportEntriesCsv(listOf(ListEntry(MediaRef(anilistId = 9), "=cmd|' /C calc'!A0", null, ListStatus.PLANNING, updatedAtMs = 1_000, notes = "@notes")))
        assertEquals("9,9,,'=cmd|' /C calc'!A0,planning,0,,,,,0,'@notes,1", csv.split("\r\n")[1])
    }

    @Test
    fun aBigMalExportParsesInLinearTime() {
        val n = 40_000
        val xml = buildString {
            append("<myanimelist>")
            for (i in 1..n) append("<anime><series_animedb_id>$i</series_animedb_id><series_title>T$i</series_title><my_status>Completed</my_status></anime>")
            append("</myanimelist>")
        }
        val t0 = System.nanoTime()
        val file = parseListFile(xml, now)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(n, file.entries.size)
        assertEquals("T$n", file.entries.last().title)
        // copying the rest of a ~4 MB text per entry took tens of seconds; walking an index takes well under one
        assertTrue(ms < 5_000, "parse took $ms ms")
    }

    // ---------------------------------------------------------------- through the repository

    private class NoSecrets : SecretStore {
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String?) = Unit
        override suspend fun clear() = Unit
    }

    @Test
    fun importFileMergesAMalExportAndImportJsonStaysJsonOnly() = runBlocking {
        val dir = Files.createTempDirectory("list-files").toFile()
        val db = NijiyomeDatabase.open(File(dir, "test.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = ListsRepositoryImpl(
                dao = db.lists(),
                secrets = NoSecrets(),
                anilist = AniListListClient(OkHttpClient(), "http://127.0.0.1:9/anilist"),
                mal = MalListClient(OkHttpClient(), api = "http://127.0.0.1:9/mal", tokenUrl = "http://127.0.0.1:9/mal/token"),
                clientIds = { null },
                scope = scope,
                syncOnStart = false,
                clock = { now },
            )
            // Android's path: a MAL export is not its JSON
            val refused = repo.importJson(fixture("mal-export.xml")).exceptionOrNull()
            assertTrue(refused is AppException, "importJson refused the XML: $refused")
            assertEquals(4, repo.importFile(fixture("mal-export.xml")).getOrThrow())
            // FMA has no time in either file (both "now"): the local copy from the MAL export is not replaced.
            assertEquals(1, repo.importFile(fixture("tatakai-lists.json")).getOrThrow())
            val csv = repo.exportCsv().split("\r\n")
            assertEquals(LIST_CSV_HEADER, csv[0])
            // One Piece is "21" from Tatakai Next and "mal:21" from MAL: two local keys, as with the JSON import.
            assertTrue(csv.any { it.startsWith("21,21,21,One Piece,watching,1105,") }, csv.joinToString("\n"))
            assertTrue(csv.any { it.startsWith("mal:5114,,5114,Fullmetal Alchemist: Brotherhood,repeating,10,64,") }, csv.joinToString("\n"))
            assertEquals(5, csv.size - 2) // header and the final empty line
        } finally {
            scope.cancel()
            db.close()
            dir.deleteRecursively()
        }
    }
}
