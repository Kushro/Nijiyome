// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts.kitten

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader

/**
 * ONNX Runtime for Java (`com.microsoft.onnxruntime:onnxruntime`, MIT) loaded from a jar downloaded on request (KittenTTS is optional:
 * the 54 MB jar with the natives of every OS is not bundled with the app). The jar gets its own class loader and the few calls the
 * engine makes go through reflection. The native libraries of this platform are unpacked once next to the jar (`native/`) and ORT
 * loads them from there (`onnxruntime.native.path`) instead of unpacking a copy into the temp directory on every start.
 */
internal class OrtBridge private constructor(private val loader: URLClassLoader) : AutoCloseable {
    private val envClass = loader.loadClass("ai.onnxruntime.OrtEnvironment")
    private val tensorClass = loader.loadClass("ai.onnxruntime.OnnxTensor")
    private val sessionClass = loader.loadClass("ai.onnxruntime.OrtSession")
    private val optionsClass = loader.loadClass("ai.onnxruntime.OrtSession\$SessionOptions")
    private val resultClass = loader.loadClass("ai.onnxruntime.OrtSession\$Result")
    private val valueClass = loader.loadClass("ai.onnxruntime.OnnxValue")
    private val createTensor: Method = tensorClass.getMethod("createTensor", envClass, Any::class.java)
    private val run: Method = sessionClass.getMethod("run", Map::class.java)
    private val resultGet: Method = resultClass.getMethod("get", Int::class.javaPrimitiveType)
    private val getValue: Method = valueClass.getMethod("getValue")
    private val env: Any = call { envClass.getMethod("getEnvironment").invoke(null) }

    val version: String? = runCatching { envClass.getMethod("getVersion").invoke(env) as? String }.getOrNull()

    /** A loaded model; [run] is not thread-safe here (the voice calls it under a lock). */
    inner class Session internal constructor(private val handle: Any) : AutoCloseable {
        /**
         * Runs the model on [inputs] (name → a primitive array, any rank) and returns its first output flattened to floats.
         */
        fun runFirstOutput(inputs: Map<String, Any>): FloatArray {
            val tensors = LinkedHashMap<String, Any>()
            try {
                for ((k, v) in inputs) tensors[k] = call { createTensor.invoke(null, env, v) }
                val result = call { run.invoke(handle, tensors) } as AutoCloseable
                result.use { r -> return flatten(call { getValue.invoke(resultGet.invoke(r, 0)) }) }
            } finally {
                tensors.values.forEach { (it as? AutoCloseable)?.close() }
            }
        }

        override fun close() {
            (handle as? AutoCloseable)?.close()
        }
    }

    fun session(model: File, threads: Int): Session {
        val options = optionsClass.getConstructor().newInstance()
        call { optionsClass.getMethod("setIntraOpNumThreads", Int::class.javaPrimitiveType).invoke(options, threads) }
        val handle = call { envClass.getMethod("createSession", String::class.java, optionsClass).invoke(env, model.absolutePath, options) }
        return Session(handle)
    }

    override fun close() {
        runCatching { loader.close() }
    }

    companion object {
        fun load(jar: File): OrtBridge {
            nativeDir(jar)?.let { System.setProperty("onnxruntime.native.path", it.absolutePath) }
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), OrtBridge::class.java.classLoader)
            return try {
                OrtBridge(loader)
            } catch (t: Throwable) {
                loader.close()
                throw t
            }
        }

        /** `ai/onnxruntime/native/<platform>/` of the ORT jar, as named there. */
        fun platformFolder(os: String = System.getProperty("os.name").orEmpty(), arch: String = System.getProperty("os.arch").orEmpty()): String? {
            val o = os.lowercase()
            val a = arch.lowercase()
            val x64 = a == "amd64" || a == "x86_64"
            val arm = a == "aarch64" || a == "arm64"
            return when {
                o.contains("linux") && x64 -> "linux-x64"
                o.contains("linux") && arm -> "linux-aarch64"
                o.contains("win") && x64 -> "win-x64"
                o.contains("mac") && arm -> "osx-aarch64"
                else -> null
            }
        }

        /** Unpacks the jar's native libraries of this platform to `<jar folder>/native/<platform>` (once); null when there are none. */
        private fun nativeDir(jar: File): File? {
            val folder = platformFolder() ?: return null
            val dir = File(File(jar.parentFile, "native"), folder)
            val done = File(dir, ".unpacked-" + jar.name)
            if (done.isFile) return dir
            val prefix = "ai/onnxruntime/native/$folder/"
            var count = 0
            java.util.zip.ZipFile(jar).use { zip ->
                for (e in zip.entries()) {
                    val rest = e.name.removePrefix(prefix)
                    if (e.isDirectory || rest == e.name || rest.isEmpty() || '/' in rest) continue
                    dir.mkdirs()
                    val part = File(dir, "$rest.part")
                    zip.getInputStream(e).use { input -> part.outputStream().use { input.copyTo(it) } }
                    val target = File(dir, rest)
                    target.delete()
                    if (!part.renameTo(target)) error("could not unpack $rest")
                    count++
                }
            }
            if (count == 0) return null
            done.writeText(count.toString())
            return dir
        }

        private inline fun <T> call(block: () -> T): T = try {
            block()
        } catch (e: InvocationTargetException) {
            throw IllegalStateException(e.targetException.message ?: e.targetException.toString(), e.targetException)
        }

        /** `float[]`, `float[][]`, … → one float array. */
        fun flatten(value: Any?): FloatArray = when (value) {
            is FloatArray -> value
            is Array<*> -> {
                val parts = value.map { flatten(it) }
                val out = FloatArray(parts.sumOf { it.size })
                var i = 0
                for (p in parts) {
                    p.copyInto(out, i)
                    i += p.size
                }
                out
            }
            else -> error("unexpected ONNX output ${value?.javaClass?.name}")
        }
    }
}
