// SPDX-License-Identifier: AGPL-3.0-or-later
// JNI bridge for app.nijiyome.upscale.UpscaleNative (desktop and Android load the same symbols).
//
// Kotlin side expected by these symbols (one object, every function @JvmStatic so the second JNI argument is jclass):
//
//   package app.nijiyome.upscale
//
//   internal object UpscaleNative {
//       /** JSON, see below. preferredKey: device UUID (32 hex) or LUID (16 hex) to prefer, or null/"" for the policy. */
//       @JvmStatic external fun nProbe(preferredKey: String?): String
//
//       /** Binds the engine to ncnn device [deviceIndex] (the probe's "index") and the folder holding
//        *  realesr-animevideov3-x{2,3,4}.param and .bin. 0 = ok, negative = error code (below). Loads x2 eagerly. */
//       @JvmStatic external fun nInit(deviceIndex: Int, modelDir: String): Int
//
//       /** RGBA8 (width*height*4 bytes) -> RGBA8 of (width*scale) x (height*scale). scale is 2, 3 or 4. tile <= 0 uses
//        *  the platform default (128 desktop, 64 Android); any tile is clamped to the VRAM budget. cancelToken is polled
//        *  before every tile (getAsBoolean() == true cancels). Returns null when cancelled or failed: the caller tells
//        *  them apart by asking its own token; a failure is also logged (stderr / logcat tag "NijiyomeUpscale"). */
//       @JvmStatic external fun nUpscale(rgba: ByteArray, width: Int, height: Int, scale: Int, tile: Int,
//                                        cancelToken: java.util.function.BooleanSupplier?): ByteArray?
//
//       /** Frees nets, pipelines and pooled VRAM. Safe to call twice; nInit must run again before nUpscale. */
//       @JvmStatic external fun nRelease()
//   }
//
// nProbe JSON:
//   {"ok":true,"index":0,"device":"AMD Radeon RX 9070 XT (RADV GFX1201)","type":"discrete","vendorId":4098,
//    "deviceId":30032,"uuid":"<32 hex or empty>","luid":"<16 hex or empty>","heapMb":16304,"fp16":true,"int8":true,
//    "driver":"radv","preferredMatched":false,"error":"","detail":"",
//    "devices":[{"index":0,"name":"...","type":"discrete","uuid":"...","usable":true}, ...]}
//   ok=false comes with error "no_vulkan" (no loader/device) or "no_hardware_gpu" (only CPU/software devices).
//
// nInit / internal codes: 0 ok, -1 not initialized, -2 bad arguments, -3 no Vulkan, -4 bad device (out of range, CPU
// or software), -5 model files missing, -6 model load failed, -7 shader compile failed, -8 GPU allocation/submit failed.
//
// All calls are serialized by one mutex; run them on the queue's single worker thread.
#include "upscale_engine.h"

#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <mutex>
#include <string>
#include <vector>

#if defined(__ANDROID__)
#include <android/log.h>
#endif

namespace {

std::mutex g_mutex;
njup::Engine* g_engine = nullptr;  // created on first nInit, never destroyed (process lifetime)

void logError(const char* what, int code) {
#if defined(__ANDROID__)
    __android_log_print(ANDROID_LOG_WARN, "NijiyomeUpscale", "%s failed: %d", what, code);
#else
    std::fprintf(stderr, "[nijiyome-upscale] %s failed: %d\n", what, code);
#endif
}

std::string jstrToUtf8(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* utf = env->GetStringUTFChars(s, nullptr);
    if (!utf) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(s, utf);
    return out;
}

std::filesystem::path jstrToPath(JNIEnv* env, jstring s) {
    if (!s) return {};
#if defined(_WIN32)
    // UTF-16 straight from the JVM so non-ASCII profile folders open with _wfopen.
    const jsize len = env->GetStringLength(s);
    const jchar* chars = env->GetStringChars(s, nullptr);
    if (!chars) return {};
    std::wstring w(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(len));
    env->ReleaseStringChars(s, chars);
    return std::filesystem::path(w);
#else
    return std::filesystem::path(jstrToUtf8(env, s));
#endif
}

std::string jsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (unsigned char c : s) {
        if (c == '"') out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\n') out += "\\n";
        else if (c >= 0x20) out += static_cast<char>(c);
    }
    return out;
}

const char* typeName(int vkType) {
    switch (vkType) {
        case njgpu::kVkTypeDiscrete: return "discrete";
        case njgpu::kVkTypeIntegrated: return "integrated";
        case njgpu::kVkTypeVirtual: return "virtual";
        case njgpu::kVkTypeCpu: return "cpu";
        default: return "other";
    }
}

std::string probeJson(const njup::ProbeInfo& r) {
    const auto b = [](bool v) { return v ? "true" : "false"; };
    std::string json = "{";
    json += std::string("\"ok\":") + b(r.ok);
    json += ",\"index\":" + std::to_string(r.ok ? r.gpu.index : -1);
    json += ",\"device\":\"" + jsonEscape(r.gpu.name) + "\"";
    json += std::string(",\"type\":\"") + typeName(r.gpu.vkType) + "\"";
    json += ",\"vendorId\":" + std::to_string(r.gpu.vendorID);
    json += ",\"deviceId\":" + std::to_string(r.gpu.deviceID);
    json += ",\"uuid\":\"" + njgpu::uuidHex(r.gpu) + "\"";
    json += ",\"luid\":\"" + njgpu::luidHex(r.gpu) + "\"";
    json += ",\"heapMb\":" + std::to_string(r.gpu.deviceLocalHeapBytes / (1024 * 1024));
    json += std::string(",\"fp16\":") + b(r.fp16);
    json += std::string(",\"int8\":") + b(r.int8);
    json += ",\"driver\":\"" + jsonEscape(r.driverName) + "\"";
    json += std::string(",\"preferredMatched\":") + b(r.matchedPreferred);
    json += ",\"error\":\"" + jsonEscape(r.error) + "\"";
    json += ",\"detail\":\"" + jsonEscape(r.detail) + "\"";
    json += ",\"devices\":[";
    for (size_t i = 0; i < r.devices.size(); ++i) {
        const njup::DeviceEntry& e = r.devices[i];
        if (i > 0) json += ",";
        json += "{\"index\":" + std::to_string(e.ncnnIndex);
        json += ",\"name\":\"" + jsonEscape(e.cand.name) + "\"";
        json += std::string(",\"type\":\"") + typeName(e.cand.vkType) + "\"";
        json += ",\"uuid\":\"" + (e.cand.hasUuid ? njgpu::hexOf(e.cand.deviceUuid.data(), 16) : std::string()) + "\"";
        json += std::string(",\"usable\":") + b(e.usable) + "}";
    }
    json += "]}";
    return json;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_app_nijiyome_upscale_UpscaleNative_nProbe(JNIEnv* env, jclass, jstring jPreferredKey) {
    const std::string key = jstrToUtf8(env, jPreferredKey);
    std::lock_guard<std::mutex> lock(g_mutex);
    const njup::ProbeInfo r = njup::probe(key);
    return env->NewStringUTF(probeJson(r).c_str());
}

JNIEXPORT jint JNICALL
Java_app_nijiyome_upscale_UpscaleNative_nInit(JNIEnv* env, jclass, jint deviceIndex, jstring jModelDir) {
    const std::filesystem::path dir = jstrToPath(env, jModelDir);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dir.empty()) return njup::kErrBadArgs;
    if (!g_engine) g_engine = new njup::Engine();
    const int rc = g_engine->init(static_cast<int>(deviceIndex), dir);
    if (rc != njup::kOk) logError("nInit", rc);
    return rc;
}

JNIEXPORT jbyteArray JNICALL
Java_app_nijiyome_upscale_UpscaleNative_nUpscale(JNIEnv* env, jclass, jbyteArray jRgba, jint width, jint height,
                                                  jint scale, jint tile, jobject cancelToken) {
    if (!jRgba || width <= 0 || height <= 0) return nullptr;
    const int64_t bytes = static_cast<int64_t>(width) * height * 4;
    if (bytes > INT32_MAX || env->GetArrayLength(jRgba) != static_cast<jsize>(bytes)) {
        logError("nUpscale(size)", njup::kErrBadArgs);
        return nullptr;
    }
    const jsize len = static_cast<jsize>(bytes);
    // Copied, not pinned: the cancel callback re-enters Java between tiles, which a critical region forbids.
    std::vector<uint8_t> in(static_cast<size_t>(len));
    env->GetByteArrayRegion(jRgba, 0, len, reinterpret_cast<jbyte*>(in.data()));
    if (env->ExceptionCheck()) return nullptr;

    jmethodID getAsBoolean = nullptr;
    if (cancelToken) {
        jclass cls = env->GetObjectClass(cancelToken);
        if (cls) getAsBoolean = env->GetMethodID(cls, "getAsBoolean", "()Z");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            getAsBoolean = nullptr;
        }
        if (cls) env->DeleteLocalRef(cls);
    }
    const auto cancelled = [&]() -> bool {
        if (!getAsBoolean) return false;
        const jboolean v = env->CallBooleanMethod(cancelToken, getAsBoolean);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return true;  // a throwing token counts as cancelled
        }
        return v == JNI_TRUE;
    };

    std::vector<uint8_t> out;
    int rc;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        rc = g_engine ? g_engine->upscale(in.data(), width, height, scale, tile, cancelled, out) : njup::kErrNotInitialized;
    }
    if (rc != njup::kOk) {
        if (rc != njup::kCancelled) logError("nUpscale", rc);
        return nullptr;
    }
    if (out.size() > static_cast<size_t>(INT32_MAX)) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;  // OutOfMemoryError pending for the caller
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

JNIEXPORT void JNICALL
Java_app_nijiyome_upscale_UpscaleNative_nRelease(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->release();
}

}  // extern "C"
