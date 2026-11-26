// SPDX-License-Identifier: GPL-3.0-only
// JNI bridge for app.nijiyome.player.mpv.framegen.DesktopFrameGenNative.
#include "fg_desktop_engine.h"

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <string>

namespace {

std::string jstrToUtf8(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* utf = env->GetStringUTFChars(s, nullptr);
    if (!utf) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(s, utf);
    return out;
}

njfg::DesktopFrameGenEngine* asEngine(jlong handle) {
    return reinterpret_cast<njfg::DesktopFrameGenEngine*>(static_cast<uintptr_t>(handle));
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

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nProbe(JNIEnv* env, jclass, jstring jDllPath) {
    const njfg::ProbeResult r = njfg::probeDesktopGpuAndDll(jstrToUtf8(env, jDllPath));
    std::string json = "{";
    json += std::string("\"ok\":") + (r.ok ? "true" : "false");
    json += ",\"device\":\"" + jsonEscape(r.deviceName) + "\"";
    json += std::string(",\"fp16\":") + (r.fp16 ? "true" : "false");
    json += std::string(",\"zeroCopyVk\":") + (r.zeroCopyVk ? "true" : "false");
    json += ",\"error\":\"" + jsonEscape(r.errorCode) + "\"";
    json += "}";
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jlong JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nCreate(JNIEnv*, jclass) {
    auto* eng = new njfg::DesktopFrameGenEngine();
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(eng));
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nDestroy(JNIEnv*, jclass, jlong handle) {
    auto* eng = asEngine(handle);
    if (!eng) return;
    eng->stop();
    delete eng;
}

JNIEXPORT jint JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nStart(
    JNIEnv* env, jclass, jlong handle, jstring jDllPath, jint width, jint height,
    jint multiplier, jfloat flowScale, jboolean performance, jboolean antiArtifacts,
    jlong vsyncNs, jint forceTransport, jlong d3d12DeviceStructPtr) {
    auto* eng = asEngine(handle);
    if (!eng || width <= 0 || height <= 0) return 0;
    njfg::EngineConfig cfg{};
    cfg.dllPath = jstrToUtf8(env, jDllPath);
    cfg.width = static_cast<uint32_t>(width);
    cfg.height = static_cast<uint32_t>(height);
    cfg.multiplier = static_cast<int32_t>(multiplier);
    cfg.flowScale = static_cast<float>(flowScale);
    cfg.performance = performance == JNI_TRUE;
    cfg.antiArtifacts = antiArtifacts == JNI_TRUE;
    cfg.vsyncNs = static_cast<int64_t>(vsyncNs);
    cfg.forceTransport = static_cast<int32_t>(forceTransport);
    cfg.d3d12DeviceStructPtr = static_cast<uintptr_t>(d3d12DeviceStructPtr);
    if (!eng->start(cfg)) return 0;
    return static_cast<jint>(eng->transportKind());
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nStop(JNIEnv*, jclass, jlong handle) {
    if (auto* eng = asEngine(handle)) eng->stop();
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSetPaused(JNIEnv*, jclass, jlong handle, jboolean paused) {
    if (auto* eng = asEngine(handle)) eng->setPaused(paused == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nFlush(JNIEnv*, jclass, jlong handle) {
    if (auto* eng = asEngine(handle)) eng->flush();
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSetSourcePeriodNs(JNIEnv*, jclass, jlong handle, jlong periodNs) {
    if (auto* eng = asEngine(handle)) eng->setSourcePeriodNs(static_cast<int64_t>(periodNs));
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSetVsyncNs(JNIEnv*, jclass, jlong handle, jlong vsyncNs) {
    if (auto* eng = asEngine(handle)) eng->setVsyncNs(static_cast<int64_t>(vsyncNs));
}

JNIEXPORT void JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSetAntiArtifacts(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    if (auto* eng = asEngine(handle)) eng->setAntiArtifacts(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSubmitGlFrame(
    JNIEnv*, jclass, jlong handle, jint srcGlTexId, jlong timestampNs) {
    auto* eng = asEngine(handle);
    if (!eng) return JNI_FALSE;
    return eng->submitGlFrame(static_cast<uint32_t>(srcGlTexId), static_cast<int64_t>(timestampNs)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSubmitD3d12Frame(
    JNIEnv*, jclass, jlong handle, jlong srcResourcePtr, jlong timestampNs) {
    auto* eng = asEngine(handle);
    if (!eng) return JNI_FALSE;
    return eng->submitD3d12Frame(static_cast<uintptr_t>(srcResourcePtr), static_cast<int64_t>(timestampNs)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSubmitRgbaFrame(
    JNIEnv* env, jclass, jlong handle, jobject directBuf, jint byteCount, jlong timestampNs) {
    auto* eng = asEngine(handle);
    if (!eng || !directBuf || byteCount <= 0) return JNI_FALSE;
    auto* ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(directBuf));
    const jlong cap = env->GetDirectBufferCapacity(directBuf);
    if (!ptr || cap < byteCount) return JNI_FALSE;
    return eng->submitRgbaFrame(ptr, static_cast<size_t>(byteCount), static_cast<int64_t>(timestampNs)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nAcquirePresentSlot(
    JNIEnv*, jclass, jlong handle, jlong nowNs) {
    auto* eng = asEngine(handle);
    return eng ? static_cast<jint>(eng->acquirePresentSlot(static_cast<int64_t>(nowNs))) : -1;
}

JNIEXPORT jint JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSlotCount(JNIEnv*, jclass, jlong handle) {
    auto* eng = asEngine(handle);
    return eng ? static_cast<jint>(eng->slotCount()) : 0;
}

JNIEXPORT jlong JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nSlotHandle(JNIEnv*, jclass, jlong handle, jint slot) {
    auto* eng = asEngine(handle);
    return (eng && slot >= 0) ? static_cast<jlong>(eng->slotHandle(static_cast<uint32_t>(slot))) : 0;
}

JNIEXPORT jboolean JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nReadSlotRgba(
    JNIEnv* env, jclass, jlong handle, jint slot, jobject directBuf, jint byteCount) {
    auto* eng = asEngine(handle);
    if (!eng || slot < 0 || !directBuf || byteCount <= 0) return JNI_FALSE;
    auto* ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(directBuf));
    const jlong cap = env->GetDirectBufferCapacity(directBuf);
    if (!ptr || cap < byteCount) return JNI_FALSE;
    return eng->readSlotRgba(static_cast<uint32_t>(slot), ptr, static_cast<size_t>(byteCount)) ? JNI_TRUE : JNI_FALSE;
}

// Writes [inFps, outFps, computeMs, latencyMs] into outDoubles[4] and
// [active, transport, multiplier, overloaded, inFrames, outFrames, generated, droppedLate, sceneCuts] into outLongs[9].
JNIEXPORT jstring JNICALL
Java_app_nijiyome_player_mpv_framegen_DesktopFrameGenNative_nStats(
    JNIEnv* env, jclass, jlong handle, jdoubleArray jDoubles, jlongArray jLongs) {
    auto* eng = asEngine(handle);
    if (!eng) return env->NewStringUTF("");
    const njfg::StatsSnapshot s = eng->stats();
    if (jDoubles && env->GetArrayLength(jDoubles) >= 4) {
        jdouble d[4] = {s.inFps, s.outFps, s.computeMs, s.latencyMs};
        env->SetDoubleArrayRegion(jDoubles, 0, 4, d);
    }
    if (jLongs && env->GetArrayLength(jLongs) >= 9) {
        jlong l[9] = {
            s.active ? 1 : 0, s.transport, s.multiplier, s.overloaded ? 1 : 0,
            static_cast<jlong>(s.inFrames), static_cast<jlong>(s.outFrames),
            static_cast<jlong>(s.generatedFrames), static_cast<jlong>(s.droppedLate),
            static_cast<jlong>(s.sceneCuts),
        };
        env->SetLongArrayRegion(jLongs, 0, 9, l);
    }
    return env->NewStringUTF(s.errorCode.c_str());
}

}  // extern "C"
