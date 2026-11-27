// SPDX-License-Identifier: GPL-3.0-only
// Shared Vulkan physical-device choice for desktop-framegen and upscale (docs/plan-upscaling.md §4.1, §4.6).
//
// Extracted from native/desktop-framegen fg_desktop_engine.cpp (probeDesktopGpuAndDll) without changing its policy:
// discrete > integrated > virtual, CPU and "other" devices never chosen; within the same rank the larger DEVICE_LOCAL
// heap wins, and on an exact tie the first enumerated device stays.
//
// Header-only and free of any Vulkan header on purpose: framegen compiles against volk + Khronos headers, upscale
// against ncnn's simplevk.h, and both define the same Vulkan names. Callers fill GpuCandidate from whatever loader they
// use; the VkPhysicalDeviceType numbers below are fixed by the Vulkan spec, so they are compared as plain integers.
#pragma once

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace njgpu {

// VkPhysicalDeviceType values (Vulkan 1.0 spec, stable).
constexpr int kVkTypeOther = 0;
constexpr int kVkTypeIntegrated = 1;
constexpr int kVkTypeDiscrete = 2;
constexpr int kVkTypeVirtual = 3;
constexpr int kVkTypeCpu = 4;

// VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES (core 1.1, same value as the KHR alias).
constexpr uint32_t kVkStructureTypeIdProperties = 1000071004u;

// Layout of VkPhysicalDeviceIDProperties (Vulkan 1.1). ncnn's simplevk.h does not declare it, so it is mirrored here
// and chained into VkPhysicalDeviceProperties2::pNext by the caller.
struct VkIdPropertiesLayout {
    uint32_t sType = kVkStructureTypeIdProperties;
    void* pNext = nullptr;
    uint8_t deviceUUID[16] = {};
    uint8_t driverUUID[16] = {};
    uint8_t deviceLUID[8] = {};
    uint32_t deviceNodeMask = 0;
    uint32_t deviceLUIDValid = 0;  // VkBool32
};

struct GpuCandidate {
    int vkType = kVkTypeOther;  // VkPhysicalDeviceType
    uint32_t vendorID = 0;
    uint32_t deviceID = 0;
    std::string name;
    uint64_t deviceLocalHeapBytes = 0;  // largest DEVICE_LOCAL heap
    bool hasUuid = false;
    std::array<uint8_t, 16> deviceUuid{};
    bool hasLuid = false;
    std::array<uint8_t, 8> deviceLuid{};
};

struct PickOptions {
    // Also reject devices whose name says they are a software rasterizer (llvmpipe, lavapipe, SwiftShader) even when the
    // driver reports a GPU type. framegen passes false to keep its historical behaviour bit for bit; upscale passes true.
    bool rejectSoftwareByName = true;
};

struct PickedGpu {
    int index = -1;  // index into the candidate list, -1 when none qualifies
    int vkType = kVkTypeOther;
    uint32_t vendorID = 0;
    uint32_t deviceID = 0;
    std::string name;
    uint64_t deviceLocalHeapBytes = 0;
    bool hasUuid = false;
    std::array<uint8_t, 16> deviceUuid{};
    bool hasLuid = false;
    std::array<uint8_t, 8> deviceLuid{};
    // framegen's lsfg-vk device key: vendorID << 32 | deviceID (it cannot tell two identical GPUs apart).
    uint64_t vendorDeviceKey() const { return (static_cast<uint64_t>(vendorID) << 32) | deviceID; }
};

// 3 discrete, 2 integrated, 1 virtual, 0 never chosen (CPU, other).
inline int typeRank(int vkType) {
    switch (vkType) {
        case kVkTypeDiscrete: return 3;
        case kVkTypeIntegrated: return 2;
        case kVkTypeVirtual: return 1;
        default: return 0;
    }
}

inline bool looksLikeSoftwareRenderer(const std::string& name) {
    std::string lower(name);
    std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    for (const char* needle : {"llvmpipe", "lavapipe", "swiftshader", "softpipe"})
        if (lower.find(needle) != std::string::npos) return true;
    return false;
}

inline bool isUsable(const GpuCandidate& c, const PickOptions& opt) {
    if (typeRank(c.vkType) == 0) return false;
    if (opt.rejectSoftwareByName && looksLikeSoftwareRenderer(c.name)) return false;
    return true;
}

inline PickedGpu describe(const std::vector<GpuCandidate>& cands, int index) {
    PickedGpu p;
    if (index < 0 || index >= static_cast<int>(cands.size())) return p;
    const GpuCandidate& c = cands[static_cast<size_t>(index)];
    p.index = index;
    p.vkType = c.vkType;
    p.vendorID = c.vendorID;
    p.deviceID = c.deviceID;
    p.name = c.name;
    p.deviceLocalHeapBytes = c.deviceLocalHeapBytes;
    p.hasUuid = c.hasUuid;
    p.deviceUuid = c.deviceUuid;
    p.hasLuid = c.hasLuid;
    p.deviceLuid = c.deviceLuid;
    return p;
}

// The framegen policy. Same comparison as the original loop:
//   if (found && (r < bestRank || (r == bestRank && heap <= bestHeap))) continue;
inline PickedGpu pickBest(const std::vector<GpuCandidate>& cands, const PickOptions& opt = {}) {
    int best = -1;
    int bestRank = 0;
    uint64_t bestHeap = 0;
    for (size_t i = 0; i < cands.size(); ++i) {
        const GpuCandidate& c = cands[i];
        if (!isUsable(c, opt)) continue;
        const int r = typeRank(c.vkType);
        if (best >= 0 && (r < bestRank || (r == bestRank && c.deviceLocalHeapBytes <= bestHeap))) continue;
        best = static_cast<int>(i);
        bestRank = r;
        bestHeap = c.deviceLocalHeapBytes;
    }
    return describe(cands, best);
}

inline std::string hexOf(const uint8_t* bytes, size_t n) {
    static const char* kHex = "0123456789abcdef";
    std::string out;
    out.reserve(n * 2);
    for (size_t i = 0; i < n; ++i) {
        out += kHex[bytes[i] >> 4];
        out += kHex[bytes[i] & 0xF];
    }
    return out;
}

inline std::string uuidHex(const PickedGpu& p) { return p.hasUuid ? hexOf(p.deviceUuid.data(), 16) : std::string(); }
inline std::string luidHex(const PickedGpu& p) { return p.hasLuid ? hexOf(p.deviceLuid.data(), 8) : std::string(); }

// Picks the usable device whose UUID (32 hex chars) or LUID (16 hex chars) equals preferredKey, e.g. the GPU Skia's GL
// context reports (GL_DEVICE_UUID_EXT) or the D3D11 adapter LUID. Dashes and case are ignored. Falls back to pickBest
// when the key is empty, malformed, unknown, or names a device the policy rejects (a CPU device is never returned).
inline PickedGpu pickPreferred(const std::vector<GpuCandidate>& cands, const std::string& preferredKey, const PickOptions& opt = {}) {
    std::string key;
    for (unsigned char ch : preferredKey) {
        if (ch == '-' || std::isspace(ch)) continue;
        if (!std::isxdigit(ch)) return pickBest(cands, opt);
        key += static_cast<char>(std::tolower(ch));
    }
    if (key.size() == 32 || key.size() == 16) {
        for (size_t i = 0; i < cands.size(); ++i) {
            const GpuCandidate& c = cands[i];
            if (!isUsable(c, opt)) continue;
            const bool hit = (key.size() == 32 && c.hasUuid && hexOf(c.deviceUuid.data(), 16) == key) ||
                             (key.size() == 16 && c.hasLuid && hexOf(c.deviceLuid.data(), 8) == key);
            if (hit) return describe(cands, static_cast<int>(i));
        }
    }
    return pickBest(cands, opt);
}

// Copies the ID properties a caller queried with vkGetPhysicalDeviceProperties2 into the candidate.
inline void applyIdProperties(GpuCandidate& c, const VkIdPropertiesLayout& id) {
    c.hasUuid = false;
    for (uint8_t b : id.deviceUUID) {
        if (b != 0) { c.hasUuid = true; break; }
    }
    if (c.hasUuid) std::memcpy(c.deviceUuid.data(), id.deviceUUID, 16);
    c.hasLuid = id.deviceLUIDValid != 0;
    if (c.hasLuid) std::memcpy(c.deviceLuid.data(), id.deviceLUID, 8);
}

}  // namespace njgpu
