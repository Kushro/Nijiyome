// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <volk.h>
#include "temple_local.hpp"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace njfg {

constexpr int32_t kMinMultiplier = 2;
constexpr int32_t kMaxMultiplier = 4;
constexpr uint32_t kMaxPresentSlots = 12;
constexpr uint32_t kInputSlots = 2;

enum class TransportKind : int32_t {
    NONE = 0,
    GL_VULKAN_ZERO_COPY = 1,
    D3D12_VULKAN_ZERO_COPY = 2,
    STAGING = 3,
};

struct ProbeResult {
    bool ok = false;
    std::string deviceName;
    uint64_t deviceUuid = 0;
    bool fp16 = false;
    bool zeroCopyVk = false;
    std::string errorCode;
    std::string errorDetail;
};

struct EngineConfig {
    std::string dllPath;
    uint32_t width = 0;
    uint32_t height = 0;
    int32_t multiplier = 2;
    float flowScale = 0.85f;
    bool performance = false;
    bool antiArtifacts = true;
    int64_t vsyncNs = 16'666'667;
    // Preferred transport: 0 = auto (zero-copy first, fallback staging), 3 = force staging
    int32_t forceTransport = 0;
    // Windows D3D12 Skiko DirectXDevice* pointer (0 on Linux or staging)
    uintptr_t d3d12DeviceStructPtr = 0;
};

struct StatsSnapshot {
    bool active = false;
    int32_t transport = 0;
    int32_t multiplier = 2;
    double inFps = 0.0;
    double outFps = 0.0;
    double computeMs = 0.0;
    double latencyMs = 0.0;
    uint64_t inFrames = 0;
    uint64_t outFrames = 0;
    uint64_t generatedFrames = 0;
    uint64_t droppedLate = 0;
    uint64_t sceneCuts = 0;
    bool overloaded = false;
    std::string errorCode;
};

// Abstract GPU/CPU transport connecting the host graphics API (OpenGL on Linux, D3D12 on Windows,
// or CPU staging fallback) with LSFG's Vulkan device (lc).
class Transport {
public:
    virtual ~Transport() = default;
    virtual TransportKind kind() const = 0;
    virtual uint32_t slotCount() const = 0;

    // Called on Vulkan worker thread right after LSFG createContextLocal.
    // Returns false to trigger fallback to staging.
    virtual bool initVulkan(const TempleLsfg::LocalContext& lc, uint32_t width, uint32_t height, uint32_t slots) = 0;

    // Called on host graphics thread (GL / D3D12) right after initVulkan succeeds.
    virtual bool initHost(uint32_t width, uint32_t height) = 0;

    // Called on host graphics thread before worker destroys Vulkan objects (e.g., glFinish).
    virtual void flushHostBeforeDestroy() = 0;

    // Called on Vulkan worker thread BEFORE LSFG::deleteContext / finalize.
    virtual void destroyVulkan() = 0;

    // Called on host graphics thread AFTER worker thread has joined.
    virtual void destroyHost() = 0;

    // Host thread: submit input frame from GL texture id (Linux) or ID3D12Resource* (Windows) or RGBA bytes (staging).
    // Returns true if copied into `inSlot`.
    virtual bool hostSubmitGl(uint32_t inSlot, uint32_t srcGlTexId) { (void)inSlot; (void)srcGlTexId; return false; }
    virtual bool hostSubmitD3d12(uint32_t inSlot, uintptr_t srcResourcePtr, uint64_t* outFenceVal) {
        (void)inSlot; (void)srcResourcePtr; (void)outFenceVal; return false;
    }
    virtual bool hostSubmitRgba(uint32_t inSlot, const uint8_t* rgba, size_t bytes) {
        (void)inSlot; (void)rgba; (void)bytes; return false;
    }

    // Worker thread: record & submit copy from extIn[inSlot] into dst0 (and optionally dst1 for bootstrap)
    // plus optional 8x8 luma probe sample (via compute-queue-legal vkCmdCopyImageToBuffer).
    // Waits on host->VK semaphore/fence if zero-copy.
    virtual bool vkUploadInput(uint32_t inSlot, uint64_t fenceVal, VkImage dst0, VkImage dst1,
                               VkBuffer lumaBuf256) = 0;

    // Worker thread: drain/unsignal host->VK sync for a skipped input slot (e.g. stale epoch after flush/seek)
    // so binary semaphores or command allocators are never left pending when `inSlot` is recycled.
    virtual void vkDiscardInput(uint32_t inSlot, uint64_t fenceVal) {
        (void)inSlot; (void)fenceVal;
    }

    // Worker thread: copy LSFG outputs + real frame into `slots[0..count-1]` of the PresentRing,
    // signaling VK->host semaphores/fences and waiting on completion fence (for computeMs + luma readback).
    virtual bool vkCopyOutputs(const std::vector<VkImage>& srcImages, const std::vector<uint32_t>& dstSlots,
                               uint64_t* outFenceVal) = 0;

    // Host thread: wait (GPU-side!) for `slot` to be readable by Skiko before drawing it.
    virtual void hostWaitSlot(uint32_t slot, uint64_t fenceVal) = 0;

    // Host thread: per-slot handle for Skiko wrapping.
    // Linux GL: returns GL FBO id (for BackendRenderTarget.makeGL).
    // Windows D3D12: returns ID3D12Resource* pointer (for BackendRenderTarget.makeDirect3D).
    // Staging: returns 0 (use hostReadSlotRgba instead).
    virtual int64_t hostSlotHandle(uint32_t slot) const = 0;

    // Staging only: copy RGBA8 pixels of `slot` into `dst`.
    virtual bool hostReadSlotRgba(uint32_t slot, uint8_t* dst, size_t bytes) const {
        (void)slot; (void)dst; (void)bytes; return false;
    }
};

std::unique_ptr<Transport> createStagingTransport();
#if defined(_WIN32)
std::unique_ptr<Transport> createD3d12VkTransport(uintptr_t d3d12DeviceStructPtr);
#else
std::unique_ptr<Transport> createGlVkTransport();
#endif

// Helper shared by transports & probe
uint32_t findVkMemoryType(const VkPhysicalDeviceMemoryProperties& mp, uint32_t bits, VkMemoryPropertyFlags want);
void recordLumaSample8x8(VkCommandBuffer cb, VkImage srcImg, uint32_t width, uint32_t height, VkBuffer lumaBuf256);
ProbeResult probeDesktopGpuAndDll(const std::string& dllPath);

}  // namespace njfg
