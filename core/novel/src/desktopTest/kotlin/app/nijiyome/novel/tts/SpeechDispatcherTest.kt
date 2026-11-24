// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsSettings
import app.nijiyome.novel.TtsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 1.1: the SSIP side of the Linux system voice, against a fake speech-dispatcher on a Unix socket (this machine has no real one).
 * The fake answers like speechd 0.12 and sends BEGIN / END events when told to finish a message.
 */
class SpeechDispatcherTest {
    private val dir: File = Files.createTempDirectory("nijiyome-ssip").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun parsesRepliesVoicesAndEscapesTheText() {
        val r = Ssip.read(BufferedReader(StringReader("225-21\r\n225 OK MESSAGE QUEUED\r\n")))!!
        assertEquals(225, r.code)
        assertEquals(listOf("21", "OK MESSAGE QUEUED"), r.lines)
        val v = Ssip.voices(SsipReply(249, listOf("Spanish (Latin America)\tes-419\tnone", "English\ten\tnone", "OK VOICE LIST SENT")))
        assertEquals(listOf("Spanish (Latin America)", "English"), v.map { it.id })
        assertEquals("es-419", v[0].lang)
        assertEquals("Hola\r\n..punto\r\nfin\r\n.\r\n", Ssip.speakData("Hola\n.punto\r\nfin"))
        assertEquals(File("/run/x/speech-dispatcher/speechd.sock"), Ssip.socketPath(mapOf("XDG_RUNTIME_DIR" to "/run/x")))
        assertEquals(File("/tmp/s.sock"), Ssip.socketPath(mapOf("SPEECHD_ADDRESS" to "unix_socket:/tmp/s.sock", "XDG_RUNTIME_DIR" to "/run/x")))
        assertEquals(null, Ssip.socketPath(emptyMap()))
        assertEquals(-50, ssipOffset(0.5f))
        assertEquals(0, ssipOffset(1f))
        assertEquals(100, ssipOffset(2f))
        assertEquals(100, ssipOffset(7f))
    }

    /** A fake speechd: logs every command; a SPEAK is queued as message 21, 22…; [finish] ends the last one with END. */
    private inner class FakeSpeechd {
        val socket = File(dir, "speechd.sock")
        val commands: MutableList<String> = CopyOnWriteArrayList()
        private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(socket.toPath())) }
        private val nextId = AtomicInteger(21)

        @Volatile
        private var out: java.io.OutputStream? = null

        @Volatile
        var lastId: Int = 0

        init {
            // One client at a time, one after the other (the voice reconnects after a disconnect).
            Thread({
                while (true) {
                    val ch = runCatching { server.accept() }.getOrNull() ?: break
                    connections.incrementAndGet()
                    runCatching { serve(ch) }
                    runCatching { ch.close() }
                }
            }, "fake-speechd").apply { isDaemon = true; start() }
        }

        val connections = AtomicInteger(0)

        private fun serve(ch: java.nio.channels.SocketChannel) {
                val o = Channels.newOutputStream(ch)
                out = o
                val input = BufferedReader(InputStreamReader(Channels.newInputStream(ch), Charsets.UTF_8))
                var inData = false
                val data = StringBuilder()
                while (true) {
                    val line = input.readLine() ?: break
                    if (inData) {
                        if (line == ".") {
                            inData = false
                            commands += "DATA ${data.toString().trimEnd()}"
                            data.clear()
                            lastId = nextId.getAndIncrement()
                            send("225-$lastId\r\n225 OK MESSAGE QUEUED\r\n")
                            send("701-$lastId\r\n701-1\r\n701 BEGIN\r\n")
                        } else {
                            data.append(line).append('\n')
                        }
                        continue
                    }
                    commands += line
                    when {
                        line == "SPEAK" -> {
                            inData = true
                            send("230 OK RECEIVING DATA\r\n")
                        }
                        line == "LIST SYNTHESIS_VOICES" -> send("249-Paulina\tes-MX\tnone\r\n249-Daniel\ten-GB\tnone\r\n249 OK VOICE LIST SENT\r\n")
                        line == "GET OUTPUT_MODULE" -> send("251-espeak-ng\r\n251 OK GET RETURNED\r\n")
                        line == "CANCEL self" -> {
                            send("213 OK CANCELED\r\n")
                            send("703-$lastId\r\n703-1\r\n703 CANCELED\r\n")
                        }
                        line == "QUIT" -> send("231 HAPPY HACKING\r\n")
                        else -> send("200 OK\r\n")
                    }
                }
        }

        @Synchronized
        fun send(text: String) {
            // A client that already closed (disconnect) gets no answer; keep reading what it sent.
            runCatching {
                out?.write(text.toByteArray())
                out?.flush()
            }
        }

        fun finish(id: Int = lastId) = send("702-$id\r\n702-1\r\n702 END\r\n")

        fun close() = server.close()
    }

    @Test
    fun speaksWithRateAndPitchWaitsForTheEndAndPausesThroughSsip(): Unit = runBlocking {
        val fake = FakeSpeechd()
        val voice = SpeechDispatcherVoice(socket = { fake.socket }, spawn = { false })
        try {
            val status = voice.status()
            assertTrue(status.available)
            assertEquals("speech-dispatcher · espeak-ng", status.backend)
            // The voices of the app language when there are any (as on Android), else all of them.
            val ids = voice.voices(TtsSettings()).map { it.id }
            assertTrue(ids.isNotEmpty() && ids.all { it in setOf("Paulina", "Daniel") }, "$ids")

            val speaking = async(Dispatchers.Default) { voice.speak("Primera frase.\n.Segunda", TtsSettings(voice = "Paulina", pitch = 1.5f), speed = 0.5f) }
            withTimeout(5_000) { while (fake.commands.none { it.startsWith("DATA") }) delay(10) }
            assertTrue(voice.pause())
            withTimeout(5_000) { while ("PAUSE self" !in fake.commands) delay(10) }
            assertTrue(voice.resume())
            withTimeout(5_000) { while ("RESUME self" !in fake.commands) delay(10) }
            assertTrue(speaking.isActive, "speak waits for END")
            fake.finish()
            withTimeout(5_000) { speaking.await() }

            val sent = fake.commands.toList()
            assertEquals("SET self CLIENT_NAME user:nijiyome:narrator", sent[0])
            assertEquals("SET self NOTIFICATION all on", sent[1])
            assertTrue("SET self RATE -50" in sent)
            assertTrue("SET self PITCH 50" in sent)
            assertTrue("SET self SYNTHESIS_VOICE Paulina" in sent)
            assertTrue("DATA Primera frase.\n..Segunda" in sent)

            // Cancelling (pause of a run, stop) cancels the message in speechd.
            val second = async(Dispatchers.Default) { voice.speak("Otra.", TtsSettings(), speed = 1f) }
            withTimeout(5_000) { while (fake.commands.count { it.startsWith("DATA") } < 2) delay(10) }
            assertTrue(fake.commands.any { it.startsWith("SET self LANGUAGE ") })
            second.cancel()
            withTimeout(5_000) { while ("CANCEL self" !in fake.commands) delay(10) }
        } finally {
            voice.close()
            fake.close()
        }
    }

    @Test
    fun disconnectCancelsQuitsAndTheNextMessageReconnects(): Unit = runBlocking {
        val fake = FakeSpeechd()
        val voice = SpeechDispatcherVoice(socket = { fake.socket }, spawn = { false })
        try {
            // A voice name with a line break must not inject a second SSIP command.
            val first = async(Dispatchers.Default) { voice.speak("Uno.", TtsSettings(voice = "Paulina\r\nQUIT"), 1f) }
            withTimeout(5_000) { while (fake.commands.none { it.startsWith("DATA") }) delay(10) }
            assertTrue("SET self SYNTHESIS_VOICE PaulinaQUIT" in fake.commands, "${fake.commands}")
            assertTrue("QUIT" !in fake.commands)
            fake.finish()
            withTimeout(5_000) { first.await() }
            assertEquals(1, fake.connections.get())

            voice.disconnect()
            withTimeout(5_000) { while ("QUIT" !in fake.commands) delay(10) }
            assertTrue("CANCEL self" in fake.commands)
            assertEquals(false, voice.pause(), "nothing to pause once disconnected")
            voice.disconnect() // twice is fine

            val second = async(Dispatchers.Default) { voice.speak("Dos.", TtsSettings(), 1f) }
            withTimeout(5_000) { while (fake.commands.count { it.startsWith("DATA") } < 2) delay(10) }
            assertEquals(2, fake.connections.get())
            assertEquals(2, fake.commands.count { it == "SET self CLIENT_NAME user:nijiyome:narrator" })
            fake.finish()
            withTimeout(5_000) { second.await() }
            assertEquals("es", Ssip.argument(" e\ts\n"))
        } finally {
            voice.close()
            fake.close()
        }
    }

    @Test
    fun withoutADaemonItIsUnavailable(): Unit = runBlocking {
        val voice = SpeechDispatcherVoice(socket = { File(dir, "missing.sock") }, spawn = { false })
        assertEquals(false, voice.status().available)
        assertFailsWith<TtsException> { voice.speak("x", TtsSettings(), 1f) }
        assertTrue(voice.voices(TtsSettings()).isEmpty())
        assertEquals(false, voice.pause())
    }
}
