// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts.kitten

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/** One voice matrix of `voices.npz` (`rows × cols` float32, row-major). */
internal class VoiceMatrix(val rows: Int, val cols: Int, val data: FloatArray) {
    /** Style row for a text of [len] characters (`selectStyleRow`). */
    fun styleFor(len: Int): FloatArray {
        val row = len.coerceAtMost(rows - 1).coerceAtLeast(0)
        return data.copyOfRange(row * cols, (row + 1) * cols)
    }
}

/** The parts of a KittenTTS `config.json` the engine uses. */
internal data class KittenConfig(
    val modelFile: String,
    val voicesFile: String,
    val aliases: Map<String, String>,
    val speedPriors: Map<String, Float>,
    val sampleRate: Int,
)

/** Parsers of the KittenTTS model files (pure; port of Tatakai Next's `kitten.rs`). */
internal object KittenModel {
    const val DEFAULT_VOICE: String = "Luna"
    const val DEFAULT_SAMPLE_RATE: Int = 24_000

    /** Samples the model pads at the end of every chunk (`TRIM_SAMPLES` of the SDK). */
    const val TRIM_SAMPLES: Int = 5_000

    /** The eight voices of the KittenTTS 0.8 models (display names; `voice_aliases` maps them to the matrices). */
    val VOICES: List<String> = listOf("Bella", "Jasper", "Luna", "Bruno", "Rosie", "Hugo", "Kiki", "Leo")

    private val json = Json { ignoreUnknownKeys = true }

    /** A file name a config may name: plain, no traversal. */
    fun safeFile(name: String): Boolean =
        name.isNotEmpty() && !name.contains("..") && !name.startsWith("/") && !name.contains('\\') && !name.contains(':')

    fun config(text: String): KittenConfig {
        val root = json.parseToJsonElement(text) as? JsonObject ?: error("the model config is not a JSON object")
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        if (type != null && type != "ONNX1" && type != "ONNX2") error("unsupported KittenTTS model type '$type'")
        val model = root["model_file"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(::safeFile) ?: error("the model config names no model file")
        val voices = root["voices"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(::safeFile) ?: "voices.npz"
        val aliases = (root["voice_aliases"] as? JsonObject)?.mapNotNull { (k, v) -> runCatching { v.jsonPrimitive.contentOrNull }.getOrNull()?.let { k to it } }?.toMap().orEmpty()
        val priors = (root["speed_priors"] as? JsonObject)?.mapNotNull { (k, v) -> runCatching { v.jsonPrimitive.doubleOrNull }.getOrNull()?.let { k to it.toFloat() } }?.toMap().orEmpty()
        val rate = root["sample_rate"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }?.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
        return KittenConfig(model, voices, aliases, priors, rate)
    }

    /** A `.npy` file (version 1..3, little-endian float32, C order, 1-D or 2-D). */
    fun npy(bytes: ByteArray): VoiceMatrix {
        require(bytes.size >= 10 && String(bytes, 1, 5, Charsets.ISO_8859_1) == "NUMPY") { "invalid npy header" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val major = bytes[6].toInt()
        val (headerLen, offset) = if (major == 1) (b.getShort(8).toInt() and 0xffff) to 10 else {
            require(bytes.size >= 12) { "truncated npy header" }
            b.getInt(8) to 12
        }
        require(offset + headerLen <= bytes.size) { "truncated npy header" }
        val header = String(bytes, offset, headerLen, Charsets.ISO_8859_1)
        require(header.contains("'descr': '<f4'")) { "unsupported npy dtype: $header" }
        require(header.contains("'fortran_order': False")) { "fortran-order npy arrays are not supported" }
        val shape = header.substringAfter("'shape':", "").substringAfter('(', "").substringBefore(')')
        val dims = shape.split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val (rows, cols) = when (dims.size) {
            2 -> dims[0] to dims[1]
            1 -> 1 to dims[0]
            else -> error("expected a 2-D voice matrix, got ($shape)")
        }
        val start = offset + headerLen
        require(rows > 0 && cols > 0 && bytes.size - start >= rows.toLong() * cols * 4) { "npy data is shorter than its shape" }
        val data = FloatArray(rows * cols)
        b.position(start)
        b.asFloatBuffer().get(data)
        return VoiceMatrix(rows, cols, data)
    }

    /** Every `<name>.npy` of a `voices.npz` (a zip). */
    fun npz(bytes: ByteArray): Map<String, VoiceMatrix> {
        val out = HashMap<String, VoiceMatrix>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && e.name.endsWith(".npy")) out[e.name.removeSuffix(".npy").substringAfterLast('/')] = npy(zip.readBytes())
            }
        }
        require(out.isNotEmpty()) { "voices.npz holds no voices" }
        return out
    }

    /** Matrix name of a display voice: its alias (`Luna` → `expr-voice-3-f`), the name itself, the default voice, else any. */
    fun resolveVoice(aliases: Map<String, String>, voices: Map<String, VoiceMatrix>, voice: String?): String? {
        val wanted = voice ?: DEFAULT_VOICE
        val name = aliases[wanted] ?: wanted
        if (name in voices) return name
        val fallback = aliases[DEFAULT_VOICE] ?: DEFAULT_VOICE
        if (fallback in voices) return fallback
        return voices.keys.minOrNull()
    }
}
