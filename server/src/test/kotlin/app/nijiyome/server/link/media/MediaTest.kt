// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of the unit tests of Tatakai Next tk-link/src/media/{profiles,probe,anime4k,vaapi,capabilities,args,lsfg,engine}.rs
// (AGPL-3.0, same author).
package app.nijiyome.server.link.media

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun tearDown() = dirs.forEach { it.deleteRecursively() }

    private fun tmp(): File = Files.createTempDirectory("media").toFile().also { dirs += it }

    // ---- profiles.rs ----

    @Test
    fun catalogIdsAreUniqueStableAndLinkStyle() {
        val ids = Profiles.CATALOG.map { it.id }
        assertEquals(listOf("p-none", "p-anime4k-1080", "p-anime4k-1440", "p-anime4k-2160", "p-anime4k-1080-fg2x"), ids.take(5))
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("p-") })
        val lsfg = Profiles.CATALOG.filter { it.fg.lsfg() != null }
        assertEquals(10, lsfg.size)
        lsfg.forEach { assertEquals(1920 to 1080, it.width to it.height, it.id); assertTrue("LSFG" in it.label) }
        assertEquals(Fg.Lsfg(LsfgMode.Fixed(3)), Profiles.find("p-lsfg-1080-x3")!!.fg)
        assertEquals(Upscale.Anime4k(Anime4kMode.A, Anime4kVariant.M), Profiles.find("p-anime4k-lsfg-1080-auto120")!!.upscale)
        assertEquals(Fg.Lsfg(LsfgMode.Auto(120)), Profiles.find("p-lsfg-1080-auto120")!!.fg)
        assertEquals(12_000, Profiles.find("p-lsfg-1080-x5")!!.videoKbps)
        assertNull(Profiles.find("l-lsfg-1080x2"), "Temple ids are not Link ids")
    }

    @Test
    fun autoMultiplierAimsAtTheTarget() {
        fun m(fps: Double) = autoLsfgMultiplier(120, fps)
        assertEquals(listOf(5, 5, 5, 4, 4, 2, 2, 2, 2), listOf(23.976, 24.0, 25.0, 29.97, 30.0, 50.0, 59.94, 60.0, 120.0).map(::m))
        assertEquals(2, m(48.0))
        assertEquals(3, m(40.0))
        assertEquals(2, autoLsfgMultiplier(60, 24.0))
        for (bad in listOf(null, 0.0, Double.POSITIVE_INFINITY, Double.NaN, -3.0)) assertEquals(Profiles.LSFG_AUTO_FALLBACK_MULTIPLIER, autoLsfgMultiplier(120, bad))
        assertEquals(3, LsfgMode.Fixed(3).multiplier(60.0))
        assertEquals(4, LsfgMode.Auto(120).multiplier(30.0))
    }

    @Test
    fun platformPreferences() {
        assertEquals(listOf(Encoder.H264_AMF, Encoder.H264_NVENC, Encoder.H264_QSV, Encoder.LIBOPENH264), Encoder.preference(Platform.WINDOWS))
        assertEquals(Encoder.H264_VAAPI, Encoder.preference(Platform.LINUX)[0])
        assertEquals("lsfg", Fg.Lsfg(LsfgMode.Fixed(2)).wire)
        assertEquals("frc_amf", Fg.FrcAmf.wire)
        assertEquals("none", Fg.None.wire)
    }

    // ---- probe.rs ----

    @Test
    fun ratesParseAndMultiply() {
        assertEquals(FrameRate(24_000, 1001), FrameRate.parse("24000/1001"))
        assertEquals(FrameRate(30, 1), FrameRate.parse(" 30 "))
        for (bad in listOf("0/0", "1000/1", "abc", "", "-1/2", "1/0")) assertNull(FrameRate.parse(bad), bad)
        val r = FrameRate.parse("24000/1001")!!
        assertEquals("120000/1001", r.times(5)!!.ffmpeg())
        assertTrue(kotlin.math.abs(r.fps() - 23.976) < 0.001)
        assertNull(FrameRate(UInt.MAX_VALUE.toLong(), 1).times(2))
    }

    @Test
    fun ffprobeJsonPrefersRFrameRateAndFallsBack() {
        assertEquals(FrameRate(24_000, 1001), Probe.parseRate("""{"programs":[],"streams":[{"r_frame_rate":"24000/1001","avg_frame_rate":"0/0"}]}"""))
        assertEquals(FrameRate(30, 1), Probe.parseRate("""{"streams":[{"r_frame_rate":"0/0","avg_frame_rate":"30/1"}]}"""))
        assertNull(Probe.parseRate("""{"streams":[]}"""))
        assertNull(Probe.parseRate("not json"))
        val a = Probe.ffprobeArgs("http://127.0.0.1:1/u/t")
        assertEquals("http://127.0.0.1:1/u/t", a.last())
        assertTrue(a.zipWithNext().any { it == "-protocol_whitelist" to "http,tcp,crypto" })
        assertTrue(a.zipWithNext().any { it.first == "-show_entries" && it.second.endsWith(":format=duration") })
    }

    @Test
    fun ffprobeJsonGivesTheContainerDuration() {
        val info = Probe.parse("""{"streams":[{"r_frame_rate":"24000/1001"}],"format":{"duration":"1421.312000"}}""")
        assertEquals(1421.312, info.durationSecs)
        assertEquals(FrameRate(24_000, 1001), info.rate)
        for (bad in listOf("""{"format":{"duration":"N/A"}}""", """{"format":{}}""", """{"streams":[]}""", """{"format":{"duration":"0.4"}}""", """{"format":{"duration":"999999"}}""", "x")) {
            assertNull(Probe.parseDuration(bad), bad)
        }
    }

    // ---- anime4k.rs ----

    private fun writeFakeShaders(dir: File) = Anime4k.requiredFiles().forEach {
        File(dir, "$it.glsl").writeText("//!DESC $it\r\n//!HOOK MAIN\r\nvec4 hook() { return HOOKED_tex(HOOKED_pos); }\r\n")
    }

    @Test
    fun namesAreFilterSafe() {
        for (m in Anime4kMode.entries) for (v in Anime4kVariant.entries) {
            val n = Anime4k.shaderFileName(m, v)
            assertTrue(n.all { it.isLetterOrDigit() || it == '-' || it == '.' }, n)
        }
        assertEquals("anime4k-v4.0-a-m.hook", Anime4k.shaderFileName(Anime4kMode.A, Anime4kVariant.M))
        assertEquals(14, Anime4k.requiredFiles().size)
    }

    @Test
    fun composesEachModeFromItsPassesAndWritesOnce() {
        val shaders = tmp()
        val work = tmp()
        assertFalse(Anime4k.shadersPresent(shaders))
        writeFakeShaders(shaders)
        assertTrue(Anime4k.shadersPresent(shaders))
        val a = Anime4k.hookSource(shaders, Anime4kMode.A, Anime4kVariant.M)
        val b = Anime4k.hookSource(shaders, Anime4kMode.B, Anime4kVariant.M)
        val c = Anime4k.hookSource(shaders, Anime4kMode.C, Anime4kVariant.S)
        assertFalse('\r' in a)
        assertTrue("Anime4K_Restore_CNN_M\n" in a && "Restore_CNN_Soft" !in a)
        assertTrue("Anime4K_Restore_CNN_Soft_M" in b)
        assertTrue("Anime4K_Upscale_Denoise_CNN_x2_S" in c && "Restore_CNN_S" !in c)
        assertEquals(2, Regex("Anime4K_Upscale_CNN_x2_M").findAll(a).count(), "mode A upscales twice around AutoDownscale")
        assertEquals(1, Regex("Anime4K_AutoDownscalePre_x2").findAll(a).count())
        val name = Anime4k.writeShader(shaders, work, Anime4kMode.A, Anime4kVariant.M)
        assertEquals(a, File(work, name).readText())
        Anime4k.writeShader(shaders, work, Anime4kMode.A, Anime4kVariant.M)
        assertEquals(1, work.listFiles()!!.size)
    }

    @Test
    fun lockVerificationDetectsTamperingAndTraversal() {
        val dir = tmp()
        File(dir, "a.glsl").writeText("abc")
        val good = """{"files":[{"path":"a.glsl","sha256":"${sha256Hex("abc".toByteArray())}"}]}"""
        assertNull(Anime4k.verifyAgainstLock(dir, good))
        File(dir, "a.glsl").writeText("abd")
        assertTrue(Anime4k.verifyAgainstLock(dir, good)!!.contains("does not match"))
        assertTrue(Anime4k.verifyAgainstLock(dir, """{"files":[{"path":"../x","sha256":"00"}]}""")!!.contains("escapes"))
        assertTrue(Anime4k.verifyAgainstLock(dir, """{"files":[{"path":"missing.glsl","sha256":"00"}]}""") != null)
    }

    // ---- vaapi.rs ----

    private fun node(sys: File, name: String, vendor: String, boot: String?) {
        val d = File(sys, "class/drm/$name/device").apply { mkdirs() }
        File(d, "vendor").writeText("$vendor\n")
        boot?.let { File(d, "boot_vga").writeText("$it\n") }
    }

    @Test
    fun bootVgaNodeWinsThenTheLowestNumber() {
        val sys = tmp()
        assertNull(Vaapi.discover(sys, File("/dev/dri")))
        node(sys, "card0", "0x1002", "1")
        node(sys, "renderD129", "0x1002", "1")
        node(sys, "renderD128", "0x8086", "0")
        val n = Vaapi.discover(sys, File("/dev/dri"))!!
        assertEquals(RenderNode("/dev/dri/renderD129", Vendor.AMD), n)
        assertTrue(n.usable())
        val sys2 = tmp()
        node(sys2, "renderD130", "0x10de", null)
        node(sys2, "renderD128", "0x10de", null)
        val nv = Vaapi.discover(sys2, File("/dev/dri"))!!
        assertEquals("/dev/dri/renderD128", nv.device)
        assertFalse(nv.usable(), "NVIDIA nodes do not encode with VAAPI")
    }

    @Test
    fun pciVendors() {
        assertEquals(Vendor.AMD, Vaapi.vendorFromPci("0x1002\n"))
        assertEquals(Vendor.NVIDIA, Vaapi.vendorFromPci("0x10DE"))
        assertEquals(Vendor.INTEL, Vaapi.vendorFromPci("0x8086"))
        assertEquals(Vendor.OTHER, Vaapi.vendorFromPci("0x1af4"))
        assertEquals(Vendor.UNKNOWN, Vaapi.vendorFromPci(""))
    }

    // ---- capabilities.rs ----

    private val encodersOut = "Encoders:\n V..... = Video\n ------\n V....D libx264              libx264 H.264 (codec h264)\n V....D h264_amf             AMD AMF H.264 Encoder (codec h264)\n V....D h264_nvenc           NVIDIA NVENC H.264 encoder (codec h264)\n V....D libopenh264          OpenH264 H.264 / AVC (codec h264)\n A....D aac                  AAC\n"
    private val filtersOut = "Filters:\n T.. = Timeline support\n ... libplacebo        VV->V      Apply various GPU filters from libplacebo\n ... frc_amf           V->V       AMF Frame Rate Converter\n ... scale             V->V       Scale the input video\n"
    private val canned = CommandRunner { _, args -> if ("-encoders" in args) encodersOut else filtersOut }

    @Test
    fun detectsEncodersAndFiltersInPreferenceOrder() = runBlocking {
        val d = Detection.detect(Platform.WINDOWS, File("ffmpeg"), canned)
        assertEquals(listOf(Encoder.H264_AMF, Encoder.H264_NVENC, Encoder.LIBOPENH264), d.encoders)
        assertTrue(d.libplacebo && d.frcAmf)
        assertTrue(Detection.parseEncoders(Platform.LINUX, "no encoders here").isEmpty())
        assertFalse(Detection.parseHasFilter(filtersOut, "minterpolate"))
        assertEquals(Vendor.AMD, GpuInfo.guess(d).vendor)
        val l = Detection.detect(Platform.LINUX, File("ffmpeg"), canned)
        assertEquals(listOf(Encoder.H264_NVENC, Encoder.LIBX264, Encoder.LIBOPENH264), l.encoders)
    }

    private fun caps(env: Environment, bench: BenchStore = BenchStore(), clientFg: Boolean = false): JsonObject =
        CapabilitiesJson.build(env, bench, "11111111-1111-4111-8111-111111111111", "PC", clientFg, "1.0.0")

    private fun ids(c: JsonObject) = (c["profiles"] as JsonArray).map { it.jsonObject["id"]!!.jsonPrimitive.content }

    @Test
    fun profileAvailabilityAndJsonShape() = runBlocking {
        val d = Detection.detect(Platform.WINDOWS, File("ffmpeg"), canned)
        val env = Environment(d, GpuInfo.guess(d), true, Platform.WINDOWS)
        val bench = BenchStore().apply {
            record("p-anime4k-2160", false)
            record("p-anime4k-1080", true)
        }
        val json = caps(env, bench)
        assertEquals(listOf("p-none", "p-anime4k-1080", "p-anime4k-1440", "p-anime4k-2160", "p-anime4k-1080-fg2x"), ids(json))
        assertEquals("v1", json["contract"]!!.jsonPrimitive.content)
        assertEquals("p-none", json["default_profile"]!!.jsonPrimitive.content)
        assertEquals("suppressed", json["client_fg"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"max_ram_bytes":536870912,"segment_wait_secs":15}"""), json["limits"])
        assertEquals("amd", json["gpu"]!!.jsonObject["vendor"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""["h264_amf","h264_nvenc","libopenh264"]"""), json["encoders"])
        fun by(id: String) = (json["profiles"] as JsonArray).first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject
        assertEquals("true", by("p-anime4k-1080")["measured_sustain"]!!.jsonPrimitive.content)
        assertEquals("false", by("p-anime4k-2160")["measured_sustain"]!!.jsonPrimitive.content)
        assertEquals("unknown", by("p-anime4k-1440")["measured_sustain"]!!.jsonPrimitive.content)
        assertEquals("none", by("p-none")["upscale"]!!.jsonPrimitive.content)
        assertEquals("frc_amf", by("p-anime4k-1080-fg2x")["fg"]!!.jsonPrimitive.content)
        assertEquals("h264_amf", by("p-anime4k-1080-fg2x")["encoder"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"width":1920,"height":1080}"""), by("p-anime4k-1080")["output"])
        assertEquals(1, ids(caps(env.copy(shadersOk = false, anime4kLargeOk = false), bench)).size)
        val nv = Detected(encoders = listOf(Encoder.H264_NVENC), libplacebo = true)
        assertEquals(listOf("p-none", "p-anime4k-1080", "p-anime4k-1440", "p-anime4k-2160"), ids(caps(Environment(nv, GpuInfo.guess(nv), true, Platform.WINDOWS), bench)))
    }

    @Test
    fun benchResultsPersist() {
        val f = File(tmp(), "link/bench.json")
        val b = BenchStore(f)
        assertEquals(Sustain.UNKNOWN, b.get("p-anime4k-1080"))
        b.record("p-anime4k-1080", true)
        assertEquals(Sustain.YES, BenchStore(f).get("p-anime4k-1080"))
    }

    private val linuxEncoders = " V....D libx264              libx264\n V....D h264_amf             AMD AMF\n V....D h264_nvenc           NVENC\n V....D h264_vaapi           H.264/AVC (VAAPI)\n V....D libopenh264          OpenH264\n"
    private val hwaccels = "Hardware acceleration methods:\nvdpau\ncuda\nvaapi\nqsv\ndrm\nvulkan\namf\n\n"
    private val amdNode = RenderNode("/dev/dri/renderD128", Vendor.AMD)

    private fun linuxEnv(node: RenderNode?, hw: String): Environment {
        val d = Detected(Detection.parseEncoders(Platform.LINUX, linuxEncoders), libplacebo = true, frcAmf = true, hwaccelVaapi = Detection.parseHasHwaccel(hw, "vaapi"))
        return Environment(d, GpuInfo.guessWith(d, node), true, Platform.LINUX, node)
    }

    @Test
    fun linuxPicksVaapiOnAnAmdRenderNode() {
        assertTrue(Detection.parseHasHwaccel(hwaccels, "vaapi") && !Detection.parseHasHwaccel(hwaccels, "d3d11va"))
        assertFalse(Detection.parseHasHwaccel("vaapi\n", "vaapi"), "only lines after the header count")
        val env = linuxEnv(amdNode, hwaccels)
        assertEquals(listOf(Encoder.H264_VAAPI, Encoder.H264_NVENC, Encoder.LIBX264, Encoder.LIBOPENH264), env.usableEncoders())
        assertEquals(Encoder.H264_VAAPI, env.encoder())
        assertEquals(Backend.Vaapi("/dev/dri/renderD128", true), env.backend())
        assertEquals(Vendor.AMD, env.gpu.vendor, "the node vendor wins over the NVENC guess")
        val c = caps(env)
        assertEquals("h264_vaapi", (c["encoders"] as JsonArray)[0].jsonPrimitive.content)
        assertFalse((c["encoders"] as JsonArray).any { it.jsonPrimitive.content == "h264_amf" })
        assertFalse("p-anime4k-1080-fg2x" in ids(c), "frc_amf is never offered on Linux")
        assertTrue("p-anime4k-1080" in ids(c))
        val none = linuxEnv(null, hwaccels)
        assertEquals(Encoder.H264_NVENC, none.encoder())
        assertEquals(Backend.Software, none.backend())
        val nv = linuxEnv(RenderNode("/dev/dri/renderD128", Vendor.NVIDIA), hwaccels)
        assertEquals(Encoder.H264_NVENC, nv.encoder())
        assertEquals(Backend.Software, nv.backend())
        assertEquals(Backend.Vaapi("/dev/dri/renderD128", false), linuxEnv(amdNode, "").backend())
    }

    @Test
    fun lsfgProfilesFollowTheProbeAndCarryTheirMultiplier() {
        var env = linuxEnv(amdNode, hwaccels)
        val c0 = caps(env)
        assertFalse("p-lsfg-1080-x2" in ids(c0))
        assertEquals("false", c0["lsfg"]!!.jsonObject["available"]!!.jsonPrimitive.content)
        assertEquals("disabled", c0["lsfg"]!!.jsonObject["reason"]!!.jsonPrimitive.content)
        env = env.copy(lsfg = LsfgStatus.available(LsfgPaths(File("/opt/t/temple-lsfg"), File("/s/Lossless.dll")), ProbeInfo("AMD Radeon RX 9070 XT (RADV GFX1201)", true)))
        val c = caps(env)
        for (id in listOf("p-lsfg-1080-x2", "p-lsfg-1080-x5", "p-lsfg-1080-auto120", "p-anime4k-lsfg-1080-x3", "p-anime4k-lsfg-1080-auto120")) assertTrue(id in ids(c), id)
        assertEquals(Json.parseToJsonElement("""{"available":true,"experimental":true,"multipliers":[2,3,4,5],"auto_target_fps":120}"""), c["lsfg"])
        fun by(id: String) = (c["profiles"] as JsonArray).first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject
        assertEquals("lsfg", by("p-lsfg-1080-x3")["fg"]!!.jsonPrimitive.content)
        assertEquals("3", by("p-lsfg-1080-x3")["fg_multiplier"]!!.jsonPrimitive.content)
        assertFalse("fg_target_fps" in by("p-lsfg-1080-x3"))
        assertEquals("120", by("p-anime4k-lsfg-1080-auto120")["fg_target_fps"]!!.jsonPrimitive.content)
        assertEquals("anime4k", by("p-anime4k-lsfg-1080-auto120")["upscale"]!!.jsonPrimitive.content)
        assertEquals("h264_vaapi", by("p-lsfg-1080-x2")["encoder"]!!.jsonPrimitive.content)
        assertFalse("fg_multiplier" in by("p-none"), "M0 profiles keep their shape")
        val noShaders = ids(caps(env.copy(shadersOk = false)))
        assertTrue("p-lsfg-1080-x4" in noShaders && "p-anime4k-lsfg-1080-x4" !in noShaders)
    }

    // ---- args.rs ----

    private val input = "http://127.0.0.1:50001/u/tok/p/0/AAAA"
    private val sinkBase = "http://127.0.0.1:50002/sink/tok/1"
    private val vaapi = Backend.Vaapi("/dev/dri/renderD128", true)

    private fun shaderOf(p: ProfileSpec) = (p.upscale as? Upscale.Anime4k)?.let { Anime4k.shaderFileName(it.mode, it.variant) }

    private fun params(id: String, enc: Encoder, backend: Backend = Backend.D3d11, start: Int = 0, audio: Int = 0): PipelineParams {
        val profile = Profiles.find(id)!!
        return PipelineParams(profile, enc, backend, input, sinkBase, start, audio, shaderOf(profile))
    }

    private fun args(id: String, enc: Encoder, start: Int = 0, audio: Int = 0) = Args.build(params(id, enc, Backend.D3d11, start, audio))

    private val tail = listOf(
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2", "-f", "hls", "-hls_time", "2", "-hls_segment_type", "fmp4", "-hls_flags",
        "independent_segments", "-hls_list_size", "6", "-start_number", "0", "-hls_fmp4_init_filename", "init.mp4", "-hls_segment_options",
        "movflags=+frag_discont:use_editlist=0:avoid_negative_ts=disabled", "-hls_segment_filename", "http://127.0.0.1:50002/sink/tok/1/seg_%d.m4s",
        "-method", "PUT", "-protocol_whitelist", "http,tcp,crypto", "http://127.0.0.1:50002/sink/tok/1/live.m3u8",
    )

    @Test
    fun goldenPNoneLibopenh264() {
        val want = listOf(
            "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-hwaccel", "d3d11va", "-protocol_whitelist", "http,tcp,crypto", "-i", input, "-map", "0:v:0",
            "-map", "0:a:0?", "-vf", "scale=w='min(iw,1920)':h='min(ih,1080)':force_original_aspect_ratio=decrease:force_divisible_by=2,format=nv12",
            "-c:v", "libopenh264", "-b:v", "6000k", "-pix_fmt", "yuv420p", "-force_key_frames", "expr:gte(t,n_forced*2)",
        ) + tail
        assertEquals(want, args("p-none", Encoder.LIBOPENH264))
    }

    @Test
    fun goldenPNoneNvencAndQsv() {
        val nv = args("p-none", Encoder.H264_NVENC)
        val i = nv.indexOf("-c:v")
        assertEquals(listOf("-c:v", "h264_nvenc", "-preset", "p4", "-rc", "vbr", "-b:v", "6000k", "-maxrate", "6000k", "-bf", "0", "-g", "120", "-forced-idr", "1", "-force_key_frames"), nv.subList(i, i + 17))
        val qsv = args("p-none", Encoder.H264_QSV)
        val j = qsv.indexOf("-c:v")
        assertEquals(listOf("-c:v", "h264_qsv", "-preset", "medium", "-b:v", "6000k", "-maxrate", "6000k", "-bf", "0", "-g", "120"), qsv.subList(j, j + 12))
        assertFalse("-init_hw_device" in nv, "AMF device only for AMF")
    }

    @Test
    fun goldenAnime4k1080Amf() {
        val want = listOf(
            "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-init_hw_device", "d3d11va=dx:0", "-init_hw_device", "amf=amf0@dx", "-hwaccel", "d3d11va",
            "-protocol_whitelist", "http,tcp,crypto", "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-vf",
            "libplacebo=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=anime4k-v4.0-a-m.hook",
            "-fps_mode", "passthrough", "-c:v", "h264_amf", "-usage", "transcoding", "-quality", "balanced", "-rc", "vbr_peak", "-b:v", "8000k", "-maxrate", "8000k",
            "-bf", "0", "-g", "120", "-forced_idr", "1", "-force_key_frames", "expr:gte(t,n_forced*2)",
        ) + tail
        assertEquals(want, args("p-anime4k-1080", Encoder.H264_AMF))
    }

    @Test
    fun goldenAnime4k1440And2160DifferOnlyInSizeVariantAndBitrate() {
        val a = args("p-anime4k-1440", Encoder.H264_NVENC)
        assertTrue("libplacebo=w=2560:h=1440:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=anime4k-v4.0-a-l.hook" in a)
        assertTrue("14000k" in a)
        val b = args("p-anime4k-2160", Encoder.H264_NVENC)
        assertTrue("libplacebo=w=3840:h=2160:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=anime4k-v4.0-a-l.hook" in b)
        assertTrue("28000k" in b)
    }

    @Test
    fun goldenFg2xAmfFilterChainAndHwDevice() {
        val a = args("p-anime4k-1080-fg2x", Encoder.H264_AMF)
        assertEquals(
            "libplacebo=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=anime4k-v4.0-a-m.hook," +
                "tpad=stop=1:stop_mode=clone,hwupload,frc_amf=profile=high:use_future_frame=0:fallback_mode=dup,trim=start_frame=2,settb=AVTB," +
                "setpts=STARTPTS+(N-2)/(FRAME_RATE*TB)",
            a[a.indexOf("-vf") + 1],
        )
        assertEquals("amf0", a[a.indexOf("-filter_hw_device") + 1])
        assertTrue("10000k" in a)
        val p = params("p-anime4k-1080-fg2x", Encoder.H264_NVENC).copy(shaderFile = "x.hook")
        assertEquals(ArgsException.Reason.NEEDS_AMF, assertFailsWith<ArgsException> { Args.build(p) }.reason)
    }

    @Test
    fun seekGenerationUsesSsStartNumberAndTsOffsetOnlyWhenKIsPositive() {
        val a = args("p-none", Encoder.LIBOPENH264, 30, 1)
        val i = a.indexOf("-ss")
        assertEquals("60", a[i + 1])
        assertTrue(i < a.indexOf("-i"), "-ss is an input option")
        assertEquals("60", a[a.indexOf("-output_ts_offset") + 1])
        assertEquals("30", a[a.indexOf("-start_number") + 1])
        assertTrue("0:a:1?" in a)
        val zero = args("p-none", Encoder.LIBOPENH264)
        assertFalse("-ss" in zero || "-output_ts_offset" in zero)
    }

    @Test
    fun invariantsForEveryProfileAndEncoder() {
        for (profile in Profiles.CATALOG.filter { it.fg.lsfg() == null }) for (backend in listOf(Backend.D3d11, vaapi, Backend.Software)) for (enc in Encoder.entries) {
            val a = runCatching { Args.build(PipelineParams(profile, enc, backend, input, sinkBase, 3, 0, shaderOf(profile))) }.getOrNull() ?: continue
            assertEquals(listOf(input), a.indices.filter { a[it] == "-i" }.map { a[it + 1] }, "the only input is the loopback fetcher")
            a.filter { "://" in it }.forEach { assertTrue(it.startsWith("http://127.0.0.1:"), it) }
            val wl = a.indices.filter { a[it] == "-protocol_whitelist" }.map { a[it + 1] }
            assertEquals(listOf(Args.PROTOCOL_WHITELIST, Args.PROTOCOL_WHITELIST), wl, "${profile.id} $enc $backend")
            assertFalse(a.any { "file:" in it || '\\' in it || ' ' in it }, "no paths or shell-isms")
            if ("h264_vaapi" in a) {
                assertTrue(a.zipWithNext().any { it == "-filter_hw_device" to "va" })
                assertTrue(a[a.indexOf("-vf") + 1].endsWith(",hwupload"))
            }
        }
    }

    @Test
    fun goldenPNoneVaapi() {
        val want = listOf(
            "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-init_hw_device", "vaapi=va:/dev/dri/renderD128", "-filter_hw_device", "va", "-hwaccel",
            "vaapi", "-hwaccel_device", "va", "-protocol_whitelist", "http,tcp,crypto", "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-vf",
            "scale=w='min(iw,1920)':h='min(ih,1080)':force_original_aspect_ratio=decrease:force_divisible_by=2,format=nv12,hwupload", "-c:v", "h264_vaapi",
            "-rc_mode", "VBR", "-b:v", "6000k", "-maxrate", "6000k", "-bf", "0", "-g", "120", "-force_key_frames", "expr:gte(t,n_forced*2)",
        ) + tail
        assertEquals(want, Args.build(params("p-none", Encoder.H264_VAAPI, vaapi)))
    }

    @Test
    fun goldenAnime4k1080VaapiUsesLibplaceboThenHwupload() {
        val a = Args.build(params("p-anime4k-1080", Encoder.H264_VAAPI, vaapi))
        assertEquals("libplacebo=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=anime4k-v4.0-a-m.hook,hwupload", a[a.indexOf("-vf") + 1])
        assertFalse("d3d11va" in a || "-fps_mode" in a)
        assertTrue("8000k" in a)
    }

    @Test
    fun linuxSoftwareFallbacksAskForNoHardware() {
        val x = Args.build(params("p-none", Encoder.LIBX264, Backend.Software))
        assertFalse(x.any { it.startsWith("-hwaccel") || it == "-init_hw_device" })
        val i = x.indexOf("-c:v")
        assertEquals(listOf("-c:v", "libx264", "-preset", "veryfast", "-b:v", "6000k", "-maxrate", "6000k", "-bufsize", "12000k", "-pix_fmt", "yuv420p", "-bf", "0", "-g", "120", "-force_key_frames", "expr:gte(t,n_forced*2)", "-c:a", "aac"), x.subList(i, i + 20))
        val o = Args.build(params("p-none", Encoder.LIBOPENH264, vaapi))
        assertTrue(o.zipWithNext().any { it == "-init_hw_device" to "vaapi=va:/dev/dri/renderD128" })
        assertTrue(o.zipWithNext().any { it == "-hwaccel" to "vaapi" })
        assertFalse("-filter_hw_device" in o)
        assertFalse(o.any { "hwupload" in it })
        val encOnly = Backend.Vaapi("/dev/dri/renderD129", false)
        val v = Args.build(params("p-none", Encoder.H264_VAAPI, encOnly))
        assertTrue("vaapi=va:/dev/dri/renderD129" in v && "-hwaccel" !in v)
        assertFalse("-init_hw_device" in Args.build(params("p-none", Encoder.LIBOPENH264, encOnly)))
    }

    private val dll = File("/home/u/.local/share/Steam/steamapps/common/Lossless Scaling/Lossless.dll")
    private val r24 = FrameRate(24_000, 1001)

    @Test
    fun encodersMustMatchTheirBackend() {
        val base = params("p-none", Encoder.LIBX264, Backend.Software)
        assertEquals(ArgsException.Reason.ENCODER_BACKEND, assertFailsWith<ArgsException> { Args.build(base.copy(encoder = Encoder.H264_VAAPI, backend = Backend.D3d11)) }.reason)
        assertEquals(ArgsException.Reason.ENCODER_BACKEND, assertFailsWith<ArgsException> { Args.build(base.copy(encoder = Encoder.H264_AMF, backend = vaapi)) }.reason)
        assertEquals(ArgsException.Reason.ENCODER_BACKEND, assertFailsWith<ArgsException> { Args.build(base.copy(encoder = Encoder.H264_VAAPI)) }.reason)
        val l = base.copy(profile = Profiles.find("p-lsfg-1080-x2")!!)
        assertEquals(ArgsException.Reason.IS_LSFG, assertFailsWith<ArgsException> { Args.build(l) }.reason)
        assertEquals(ArgsException.Reason.NOT_LSFG, assertFailsWith<ArgsException> { Args.buildLsfg(base, r24, dll) }.reason)
        assertEquals(ArgsException.Reason.DLL_NOT_ABSOLUTE, assertFailsWith<ArgsException> { Args.buildLsfg(l, r24, File("Lossless.dll")) }.reason)
        val fg = base.copy(profile = Profiles.find("p-anime4k-1080-fg2x")!!, shaderFile = "x.hook", encoder = Encoder.H264_VAAPI, backend = vaapi)
        assertEquals(ArgsException.Reason.NEEDS_AMF, assertFailsWith<ArgsException> { Args.build(fg) }.reason)
    }

    @Test
    fun goldenLsfgX2VaapiThreeProcesses() {
        val a = Args.buildLsfg(params("p-lsfg-1080-x2", Encoder.H264_VAAPI, vaapi), r24, dll)
        assertEquals(2 to FrameRate(48_000, 1001), a.multiplier to a.outputRate)
        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-init_hw_device", "vaapi=va:/dev/dri/renderD128", "-hwaccel", "vaapi", "-hwaccel_device",
                "va", "-protocol_whitelist", "http,tcp,crypto", "-i", input, "-map", "0:v:0", "-vf",
                "fps=24000/1001:start_time=0,scale=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,format=rgba",
                "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1",
            ),
            a.decode,
        )
        assertEquals(listOf("--dll", dll.path, "--width", "1920", "--height", "1080", "--multiplier", "2"), a.helper)
        val want = listOf(
            "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-init_hw_device", "vaapi=va:/dev/dri/renderD128", "-filter_hw_device", "va", "-f",
            "rawvideo", "-pix_fmt", "rgba", "-s", "1920x1080", "-framerate", "48000/1001", "-i", "pipe:0", "-protocol_whitelist", "http,tcp,crypto", "-i", input,
            "-map", "0:v:0", "-map", "1:a:0?", "-vf", "format=nv12,hwupload", "-fps_mode", "passthrough", "-c:v", "h264_vaapi", "-rc_mode", "VBR", "-b:v",
            "8000k", "-maxrate", "8000k", "-bf", "0", "-g", "96", "-force_key_frames", "expr:gte(t,n_forced*2)",
        ) + tail
        assertEquals(want, a.encode)
        assertFalse("127.0.0.1" in a.toString() || "Lossless" in a.toString())
    }

    @Test
    fun lsfgAnime4kAuto120PicksTheMultiplierAndLevelFromTheRate() {
        val a = Args.buildLsfg(params("p-anime4k-lsfg-1080-auto120", Encoder.H264_VAAPI, vaapi), FrameRate(30, 1), dll)
        assertEquals(4 to "120/1", a.multiplier to a.outputRate.ffmpeg())
        assertEquals(
            "fps=30/1:start_time=0,pad=w='2*ceil(max(iw,ih*1920/1080)/2)':h='2*ceil(max(ih,iw*1080/1920)/2)':x='(ow-iw)/2':y='(oh-ih)/2'," +
                "libplacebo=w=1920:h=1080:format=nv12:custom_shader_path=anime4k-v4.0-a-m.hook,format=rgba",
            a.decode[a.decode.indexOf("-vf") + 1],
        )
        assertTrue(a.encode.zipWithNext().any { it == "-level" to "51" }, "1080p120 needs level 5.1")
        assertTrue(a.encode.zipWithNext().any { it == "-g" to "240" })
        assertTrue("11000k" in a.encode)
        assertEquals(5, Args.buildLsfg(params("p-anime4k-lsfg-1080-auto120", Encoder.H264_VAAPI, vaapi), r24, dll).multiplier)
        val w = Args.buildLsfg(params("p-lsfg-1080-x5", Encoder.H264_AMF, Backend.D3d11), FrameRate(60, 1), dll)
        assertTrue(w.encode.zipWithNext().any { it == "-level" to "6.0" })
        assertEquals(listOf("-init_hw_device", "d3d11va=dx:0", "-init_hw_device", "amf=amf0@dx"), w.encode.subList(5, 9))
        assertTrue(w.encode.zipWithNext().any { it == "-forced_idr" to "1" })
        assertEquals(listOf("-hwaccel", "d3d11va"), w.decode.subList(5, 7))
    }

    @Test
    fun lsfgSeekAudioTrackAndLoopbackInvariants() {
        for (profile in Profiles.CATALOG.filter { it.fg.lsfg() != null }) for (backend in listOf(vaapi, Backend.Software, Backend.D3d11)) for (enc in Encoder.entries) {
            val a = runCatching { Args.buildLsfg(PipelineParams(profile, enc, backend, input, sinkBase, 30, 2, shaderOf(profile)), r24, dll) }.getOrNull() ?: continue
            for (v in listOf(a.decode, a.encode)) {
                val ss = v.indexOf("-ss")
                assertEquals("60", v[ss + 1])
                assertEquals(input, v[v.subList(ss, v.size).indexOf("-i") + ss + 1], "-ss belongs to the loopback input")
                v.filter { "://" in it }.forEach { assertTrue(it.startsWith("http://127.0.0.1:"), it) }
            }
            val e = a.encode
            assertEquals(listOf("pipe:0", input), e.indices.filter { e[it] == "-i" }.map { e[it + 1] }, profile.id)
            assertEquals(2, e.count { it == "-protocol_whitelist" })
            assertTrue("1:a:2?" in e)
            assertTrue(e.zipWithNext().any { it == "-output_ts_offset" to "60" })
            assertTrue(e.zipWithNext().any { it == "-start_number" to "30" })
            assertEquals("pipe:1", a.decode.last())
            assertFalse(a.decode.any { ":a:" in it }, "the decode process maps no audio")
        }
    }

    @Test
    fun h264Levels() {
        assertNull(Args.h264Level(1920, 1080, 60.0))
        assertEquals("5.1", Args.h264Level(1920, 1080, 120.0))
        assertEquals("5.2", Args.h264Level(1920, 1080, 150.0))
        assertEquals("6.0", Args.h264Level(1920, 1080, 300.0))
    }

    // ---- lsfg.rs ----

    private class FakeEnv(
        val vars: MutableMap<String, String> = mutableMapOf(),
        var dirs: List<File> = listOf(File("/opt/app")),
        val files: MutableSet<File> = mutableSetOf(),
        val texts: MutableMap<File, String> = mutableMapOf(),
        var steam: List<File> = listOf(File("/steam")),
    ) : LsfgEnv {
        override fun variable(name: String) = vars[name]
        override fun helperDirs() = dirs
        override fun isFile(path: File) = path in files
        override fun readText(path: File) = texts[path]
        override fun steamRoots() = steam
        fun text(p: File, t: String) = apply { files += p; texts[p] = t }
        fun file(p: File) = apply { files += p }
    }

    private fun steamDll(root: String) = File("/$root/steamapps/common/Lossless Scaling/${Lsfg.DLL_FILE_NAME}")
    private val helperPath get() = File("/opt/app/${Lsfg.HELPER_FILE_NAME}")
    private val okProbe: (File, File) -> ProbeResult = { _, _ -> ProbeResult.Ok(ProbeInfo("Fake GPU", true)) }

    @Test
    fun theVdfLibrariesAreReadWithTheirEscapes() {
        val vdf = "\"libraryfolders\"\n{\n\t\"0\"\n\t{\n\t\t\"path\"\t\t\"C:\\\\Program Files (x86)\\\\Steam\"\n\t}\n\t\"1\"\n\t{\n\t\t\"path\"\t\t\"relative\\\\library\"\n\t}\n}\n"
        assertTrue(Lsfg.parseLibraryFolders(vdf).isEmpty(), "no drive letters on Linux: nothing absolute")
        assertTrue(Lsfg.parseLibraryFolders("").isEmpty())
        assertTrue(Lsfg.parseLibraryFolders("garbage \"path\"").isEmpty())
        assertEquals(listOf("a\\b", "c\"d"), Lsfg.quotedStrings("\"a\\\\b\"  \"c\\\"d\""))
        assertEquals(listOf("PATH", "x"), Lsfg.quotedStrings("\"PATH\"  \"x"))
    }

    @Test
    fun steamCandidatesStartWithTheDefaultRootAndAddTheLibrariesOnce() {
        val vdf = "\"libraryfolders\"\n{\n\"0\"\n{\n\"path\" \"/steam\"\n}\n\"1\"\n{\n\"path\" \"/games/lib\"\n}\n}\n"
        val env = FakeEnv().text(File("/steam/steamapps/libraryfolders.vdf"), vdf)
        assertEquals(listOf(steamDll("steam"), steamDll("games/lib")), Lsfg.steamDllCandidates(env))
        assertEquals(listOf(steamDll("steam")), Lsfg.steamDllCandidates(FakeEnv()))
    }

    @Test
    fun linuxDefaultRootsComeInOrderWithoutRepeats() {
        val home = File("/home/ana")
        val xdg = File("/data")
        val flatpak = File(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam")
        assertEquals(listOf(File(home, ".local/share/Steam"), File(home, ".steam/steam"), File(home, ".steam/root"), flatpak), Lsfg.linuxSteamRoots(null, home))
        assertEquals(File(xdg, "Steam"), Lsfg.linuxSteamRoots(xdg, home)[0])
        assertEquals(File(home, ".local/share/Steam"), Lsfg.linuxSteamRoots(File("relative"), home)[0])
        assertEquals(4, Lsfg.linuxSteamRoots(File(home, ".local/share"), home).size)
        assertEquals(listOf(File(xdg, "Steam")), Lsfg.linuxSteamRoots(xdg, null))
        assertTrue(Lsfg.linuxSteamRoots(null, null).isEmpty())
        assertTrue(Lsfg.linuxSteamRoots(null, File("home")).isEmpty())
    }

    @Test
    fun theVdfOfLinuxHasPlainForwardSlashPaths() {
        val vdf = "\"libraryfolders\"\n{\n\t\"0\"\n\t{\n\t\t\"path\"\t\t\"/home/ana/.local/share/Steam\"\n\t\t\"label\"\t\t\"\"\n\t}\n\t\"1\"\n\t{\n\t\t\"path\"\t\t\"/mnt/games/SteamLibrary\"\n\t}\n}\n"
        assertEquals(listOf(File("/home/ana/.local/share/Steam"), File("/mnt/games/SteamLibrary")), Lsfg.parseLibraryFolders(vdf))
        val env = FakeEnv(steam = listOf(File("/home/ana/.local/share/Steam"))).text(File("/home/ana/.local/share/Steam/steamapps/libraryfolders.vdf"), vdf).file(steamDll("mnt/games/SteamLibrary"))
        assertEquals(steamDll("mnt/games/SteamLibrary"), Lsfg.findDll(env, null))
    }

    @Test
    fun severalRootsAreSearchedInOrderAndLibrariesAreDeduplicated() {
        val vdf = "\"path\" \"/mnt/lib\"\n"
        val env = FakeEnv(steam = listOf(File("/a/Steam"), File("/b/steam"), File("/c/flatpak")))
            .text(File("/a/Steam/steamapps/libraryfolders.vdf"), vdf).text(File("/b/steam/steamapps/libraryfolders.vdf"), vdf).text(File("/c/flatpak/steamapps/libraryfolders.vdf"), "")
        assertEquals(listOf(steamDll("a/Steam"), steamDll("mnt/lib"), steamDll("b/steam"), steamDll("c/flatpak")), Lsfg.steamDllCandidates(env))
        assertEquals(steamDll("c/flatpak"), Lsfg.findDll(env.file(steamDll("c/flatpak")), null))
    }

    @Test
    fun theDllIsTheConfiguredOneOrTheFirstFoundInSteam() {
        val onLib = steamDll("games/lib")
        val env = FakeEnv().text(File("/steam/steamapps/libraryfolders.vdf"), "\"path\" \"/games/lib\"\n").file(onLib)
        assertEquals(onLib, Lsfg.findDll(env, null))
        assertEquals(onLib, Lsfg.findDll(env, "  "))
        env.file(steamDll("steam"))
        assertEquals(steamDll("steam"), Lsfg.findDll(env, null))
        val chosen = File("/mine/Lossless.dll")
        env.file(chosen)
        assertEquals(chosen, Lsfg.findDll(env, chosen.path))
        assertNull(Lsfg.findDll(env, "/nowhere/Lossless.dll"))
        assertNull(Lsfg.findDll(env, "Lossless.dll"))
        assertNull(Lsfg.findDll(FakeEnv(), null))
    }

    @Test
    fun theHelperIsNextToTheExecutableOrInTheEnvironmentVariable() {
        assertEquals(helperPath, Lsfg.findHelper(FakeEnv().file(helperPath)))
        assertNull(Lsfg.findHelper(FakeEnv()))
        val dev = File("/dev/temple-lsfg")
        assertEquals(dev, Lsfg.findHelper(FakeEnv(vars = mutableMapOf(Lsfg.HELPER_ENV to dev.path)).file(helperPath).file(dev)))
        assertNull(Lsfg.findHelper(FakeEnv(vars = mutableMapOf(Lsfg.HELPER_ENV to "/nowhere/temple-lsfg")).file(helperPath)))
        assertNull(Lsfg.findHelper(FakeEnv(vars = mutableMapOf(Lsfg.HELPER_ENV to "temple-lsfg")).file(File("temple-lsfg"))))
        val below = FakeEnv(dirs = listOf(File("/opt/app/bin"), File("/opt/app"))).file(helperPath)
        assertEquals(helperPath, Lsfg.findHelper(below))
        val both = FakeEnv(dirs = listOf(File("/opt/app/bin"), File("/opt/app"))).file(helperPath).file(File("/opt/app/bin/${Lsfg.HELPER_FILE_NAME}"))
        assertEquals(File("/opt/app/bin/${Lsfg.HELPER_FILE_NAME}"), Lsfg.findHelper(both))
        assertEquals(helperPath, Lsfg.findHelper(FakeEnv(vars = mutableMapOf(Lsfg.HELPER_ENV to "")).file(helperPath)))
        assertNull(Lsfg.findHelper(FakeEnv(dirs = emptyList())))
    }

    @Test
    fun probeOutputMapsToAGpuOrAReasonCode() {
        assertEquals(ProbeResult.Ok(ProbeInfo("AMD Radeon RX 9070 XT (RADV GFX1201)", true)), Lsfg.parseProbeOutput("""{"ok":true,"device":"AMD Radeon RX 9070 XT (RADV GFX1201)","fp16":true}""", true))
        assertEquals(ProbeResult.Ok(ProbeInfo("GPU", false)), Lsfg.parseProbeOutput("\n  {\"ok\":true,\"device\":\"GPU\"}\n", true))
        for (code in Lsfg.HELPER_ERRORS) assertEquals(ProbeResult.Failed(code), Lsfg.parseProbeOutput("""{"ok":false,"error":"$code"}""", false))
        for ((text, exitOk) in listOf("" to true, "not json" to true, """{"ok":false}""" to false, """{"ok":false,"error":"boom"}""" to false, """{"ok":true}""" to true, """{"ok":true,"device":"GPU"}""" to false, "{\"ok\":true,\"device\":\"G\\u0007PU\"}" to true, "[1]" to true)) {
            assertEquals(ProbeResult.Failed(Lsfg.REASON_PROBE), Lsfg.parseProbeOutput(text, exitOk), text)
        }
    }

    @Test
    fun detectionReportsTheFirstMissingPiece() {
        val dllFile = steamDll("steam")
        assertEquals(Lsfg.REASON_HELPER, Lsfg.detect(FakeEnv().file(dllFile), null, okProbe).reason)
        assertEquals(Lsfg.REASON_DLL, Lsfg.detect(FakeEnv().file(helperPath), null, okProbe).reason)
        val env = FakeEnv().file(helperPath).file(dllFile)
        val st = Lsfg.detect(env, null, okProbe)
        assertTrue(st.isAvailable)
        assertNull(st.reason)
        assertEquals("Fake GPU", st.device)
        assertEquals(helperPath to dllFile, st.paths!!.helper to st.paths!!.dll)
        var seen: Pair<File, File>? = null
        Lsfg.detect(env, null) { h, d -> seen = h to d; okProbe(h, d) }
        assertEquals(helperPath to dllFile, seen)
        for (code in Lsfg.HELPER_ERRORS) assertEquals(code, Lsfg.detect(env, null) { _, _ -> ProbeResult.Failed(code) }.reason)
    }

    @Test
    fun capabilityIsExperimentalAndOnlyCarriesAReasonWhenUnavailable() {
        val env = FakeEnv().file(helperPath).file(steamDll("steam"))
        assertEquals(Json.parseToJsonElement("""{"available":true,"experimental":true,"multipliers":[2,3,4,5],"auto_target_fps":120}"""), CapabilitiesJson.lsfgJson(Lsfg.detect(env, null, okProbe).capability()))
        assertEquals(
            Json.parseToJsonElement("""{"available":false,"experimental":true,"multipliers":[2,3,4,5],"auto_target_fps":120,"reason":"helper_missing"}"""),
            CapabilitiesJson.lsfgJson(Lsfg.detect(FakeEnv(), null, okProbe).capability()),
        )
        assertEquals(Lsfg.REASON_DISABLED, LsfgStatus.DISABLED.reason)
    }

    @Test
    fun pathsNeverShowInDebug() {
        val paths = LsfgPaths(File("/secret-user/temple-lsfg"), File("/secret-user/Lossless.dll"))
        assertFalse("secret-user" in paths.toString())
        assertFalse("secret-user" in LsfgStatus.available(paths, ProbeInfo("GPU", false)).toString())
    }

    @Test
    fun aMissingHelperMakesTheRealProbeFailCleanly() {
        assertEquals(ProbeResult.Failed(Lsfg.REASON_PROBE), Lsfg.runProbe(File("/nowhere/temple-lsfg"), File("/nowhere/Lossless.dll")))
    }

    // ---- engine.rs (the chain with stand-in programs: `sh` stages piping into each other; no FFmpeg, no GPU) ----

    private fun shJob(decode: String, helper: String, encode: String) = EngineJob(
        args = listOf("-c", encode),
        workdir = File(System.getProperty("java.io.tmpdir")),
        startSegment = 0,
        sinkBase = "",
        lsfg = LsfgStages(listOf("-c", decode), File("/bin/sh"), listOf("-c", helper)),
    )

    @Test
    fun aChainPipesStagesAndSucceedsWhenAllDo() = runBlocking {
        val e = FfmpegEngine(File("/bin/sh"), null)
        val p = e.spawn(shJob("echo frame", "read x; echo \"\$x+gen\"; [ \"\$DISABLE_LSFGVK\" = 1 ]", "read y; [ \"\$y\" = frame+gen ]"))
        assertEquals(EngineExit.SUCCESS, p.await())
    }

    @Test
    fun theFirstFailureFailsTheChainAndKillStopsEveryStage() = runBlocking {
        val e = FfmpegEngine(File("/bin/sh"), null)
        assertEquals(EngineExit.FAILED, e.spawn(shJob("exit 3", "cat", "cat")).await())
        val p = e.spawn(shJob("sleep 30", "sleep 30", "sleep 30"))
        val started = System.nanoTime()
        p.kill()
        assertEquals(EngineExit.FAILED, p.await())
        assertTrue((System.nanoTime() - started) / 1_000_000_000 < 10)
    }
}
