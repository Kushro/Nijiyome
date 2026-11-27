// SPDX-License-Identifier: GPL-3.0-only
// Desktop embedded LSFG 3.1 / 3.1P engine: worker thread, 8x8 luma scene-cut detector,
// PeriodEstimator pacing (reusing core/framegen/fg_pacing.cpp), and PresentRing scheduling.
#include "fg_desktop_engine.h"

#include "common/exception.hpp"
#include "fg_pacing.h"
#include "gpu_pick.h"
#include "lsfg_3_1.hpp"
#include "lsfg_3_1p.hpp"
#include "shader_loader.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <filesystem>
#include <functional>
#include <future>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

namespace njfg {

namespace {

constexpr VkFormat kFormat = VK_FORMAT_R8G8B8A8_UNORM;
constexpr uint32_t kLumaGrid = 8;
constexpr uint32_t kLumaCells = kLumaGrid * kLumaGrid;
constexpr uint32_t kLumaCutThreshold = 26;
constexpr int64_t kBaseMarginNs = 4'000'000;
constexpr int64_t kMaxAdaptiveMarginNs = 40'000'000;
constexpr int64_t kDefaultPeriodNs = 41'708'333;  // 23.976 fps

int64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

std::filesystem::path pathFromUtf8(const std::string& s) {
    return std::filesystem::path(std::u8string(reinterpret_cast<const char8_t*>(s.data()), s.size()));
}

uint32_t requiredSlots(int32_t mult) {
    const uint32_t n = static_cast<uint32_t>(std::clamp(mult, kMinMultiplier, kMaxMultiplier));
    return std::min<uint32_t>(2u * n + 2u, kMaxPresentSlots);
}

uint32_t lsfgInstanceVersion() {
    const uint32_t loader = volkGetInstanceVersion();
    if (loader < VK_API_VERSION_1_1) return VK_API_VERSION_1_1;
    return loader < VK_API_VERSION_1_3 ? loader : VK_API_VERSION_1_3;
}

bool hasExt(const std::vector<VkExtensionProperties>& exts, const char* name) {
    for (const auto& e : exts) if (std::strcmp(e.extensionName, name) == 0) return true;
    return false;
}

struct Backend {
    void (*initialize)(uint64_t, bool, float, uint64_t, const std::function<std::vector<uint8_t>(const std::string&)>&);
    TempleLsfg::LocalContext (*createContextLocal)(VkExtent2D, VkFormat);
    void (*present)(int32_t, int, const std::vector<int>&);
    void (*waitIdle)();
    void (*deleteContext)(int32_t);
    void (*finalize)();
};

const Backend kBackend31{LSFG_3_1::initialize, LSFG_3_1::createContextLocal, LSFG_3_1::presentContext,
                         LSFG_3_1::waitIdle, LSFG_3_1::deleteContext, LSFG_3_1::finalize};
const Backend kBackend31p{LSFG_3_1P::initialize, LSFG_3_1P::createContextLocal, LSFG_3_1P::presentContext,
                          LSFG_3_1P::waitIdle, LSFG_3_1P::deleteContext, LSFG_3_1P::finalize};

struct InJob {
    uint32_t inSlot = 0;
    uint64_t fenceVal = 0;
    int64_t tsNs = 0;
    uint64_t epoch = 0;
};

struct OutFrame {
    uint32_t slot = 0;
    uint64_t fenceVal = 0;
    int64_t targetNs = 0;
    uint64_t epoch = 0;
    bool generated = false;
};

// Small 8x8 luma probe buffer allocated on LSFG's VkDevice (for anti-artifacts scene-cut detection).
// Sampled via compute-queue-legal vkCmdCopyImageToBuffer (never vkCmdBlitImage, since LSFG uses a compute-only queue).
struct LumaProbe {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory bufMem = VK_NULL_HANDLE;
    uint8_t* mapped = nullptr;
    bool prevValid = false;
    std::array<uint8_t, kLumaCells> prev{};

    bool init(const TempleLsfg::LocalContext& lc) {
        VkPhysicalDeviceMemoryProperties mp{};
        vkGetPhysicalDeviceMemoryProperties(lc.physicalDevice, &mp);
        const VkBufferCreateInfo bci{
            .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
            .size = kLumaCells * 4,
            .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        };
        if (vkCreateBuffer(lc.device, &bci, nullptr, &buffer) != VK_SUCCESS) return false;
        VkMemoryRequirements breq{};
        vkGetBufferMemoryRequirements(lc.device, buffer, &breq);
        const VkMemoryPropertyFlags host = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        uint32_t bmt = findVkMemoryType(mp, breq.memoryTypeBits, host | VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
        if (bmt == UINT32_MAX) bmt = findVkMemoryType(mp, breq.memoryTypeBits, host);
        if (bmt == UINT32_MAX) return false;
        const VkMemoryAllocateInfo bmai{.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = breq.size, .memoryTypeIndex = bmt};
        if (vkAllocateMemory(lc.device, &bmai, nullptr, &bufMem) != VK_SUCCESS) return false;
        if (vkBindBufferMemory(lc.device, buffer, bufMem, 0) != VK_SUCCESS) return false;
        void* p = nullptr;
        if (vkMapMemory(lc.device, bufMem, 0, VK_WHOLE_SIZE, 0, &p) != VK_SUCCESS) return false;
        mapped = static_cast<uint8_t*>(p);
        return true;
    }

    bool evaluateCut() {
        if (!mapped) return false;
        std::array<uint8_t, kLumaCells> cur{};
        for (uint32_t i = 0; i < kLumaCells; ++i) {
            const uint32_t r = mapped[i * 4 + 0];
            const uint32_t g = mapped[i * 4 + 1];
            const uint32_t b = mapped[i * 4 + 2];
            cur[i] = static_cast<uint8_t>((r * 77u + g * 150u + b * 29u) >> 8);
        }
        bool cut = false;
        if (prevValid) {
            uint32_t sumDiff = 0;
            for (uint32_t i = 0; i < kLumaCells; ++i) {
                const int d = static_cast<int>(cur[i]) - static_cast<int>(prev[i]);
                sumDiff += static_cast<uint32_t>(d < 0 ? -d : d);
            }
            cut = (sumDiff / kLumaCells) >= kLumaCutThreshold;
        }
        prev = cur;
        prevValid = true;
        return cut;
    }

    void reset() { prevValid = false; }

    void destroy(VkDevice dev) {
        if (dev == VK_NULL_HANDLE) return;
        if (mapped && bufMem) { vkUnmapMemory(dev, bufMem); mapped = nullptr; }
        if (buffer) { vkDestroyBuffer(dev, buffer, nullptr); buffer = VK_NULL_HANDLE; }
        if (bufMem) { vkFreeMemory(dev, bufMem, nullptr); bufMem = VK_NULL_HANDLE; }
    }
};

}  // namespace

uint32_t findVkMemoryType(const VkPhysicalDeviceMemoryProperties& mp, uint32_t bits, VkMemoryPropertyFlags want) {
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i)
        if ((bits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & want) == want) return i;
    return UINT32_MAX;
}

void recordLumaSample8x8(VkCommandBuffer cb, VkImage srcImg, uint32_t width, uint32_t height, VkBuffer lumaBuf256) {
    if (!srcImg || !lumaBuf256 || width == 0 || height == 0) return;
    std::array<VkBufferImageCopy, kLumaCells> regions{};
    for (uint32_t r = 0; r < kLumaGrid; ++r) {
        const int32_t y = static_cast<int32_t>(std::min<uint32_t>(height - 1, ((2u * r + 1u) * height) / (2u * kLumaGrid)));
        for (uint32_t c = 0; c < kLumaGrid; ++c) {
            const int32_t x = static_cast<int32_t>(std::min<uint32_t>(width - 1, ((2u * c + 1u) * width) / (2u * kLumaGrid)));
            const uint32_t idx = r * kLumaGrid + c;
            regions[idx] = VkBufferImageCopy{
                .bufferOffset = static_cast<VkDeviceSize>(idx) * 4,
                .bufferRowLength = 0,
                .bufferImageHeight = 0,
                .imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
                .imageOffset = {x, y, 0},
                .imageExtent = {1, 1, 1},
            };
        }
    }
    vkCmdCopyImageToBuffer(cb, srcImg, VK_IMAGE_LAYOUT_GENERAL, lumaBuf256, static_cast<uint32_t>(regions.size()), regions.data());
}

ProbeResult probeDesktopGpuAndDll(const std::string& dllPath) {
    ProbeResult res{};
    if (!dllPath.empty()) {
        auto set = std::make_shared<tlsfg::ShaderSet>();
        const int32_t rc = tlsfg::extractShaders(pathFromUtf8(dllPath), *set);
        if (rc != tlsfg::FgResult::OK) {
            res.errorCode = (rc == tlsfg::FgResult::DLL_UNREADABLE) ? "dll_unreadable" : "missing_spirv";
            res.errorDetail = "Lossless.dll missing SPIR-V shaders";
            return res;
        }
        res.fp16 = set->hasFp16();
    }

    if (volkInitialize() != VK_SUCCESS) {
        res.errorCode = "no_vulkan";
        res.errorDetail = "Vulkan loader not found";
        return res;
    }
    const uint32_t instVer = lsfgInstanceVersion();
    const VkApplicationInfo ai{.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "nijiyome-fg", .apiVersion = instVer};
    const VkInstanceCreateInfo ici{.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &ai};
    VkInstance inst = VK_NULL_HANDLE;
    if (vkCreateInstance(&ici, nullptr, &inst) != VK_SUCCESS || !inst) {
        res.errorCode = "no_vulkan";
        res.errorDetail = "vkCreateInstance failed";
        return res;
    }
    volkLoadInstance(inst);

    uint32_t count = 0;
    vkEnumeratePhysicalDevices(inst, &count, nullptr);
    std::vector<VkPhysicalDevice> devs(count);
    if (count > 0) vkEnumeratePhysicalDevices(inst, &count, devs.data());

    // Device choice lives in native/common/gpu_pick.h (shared with native/upscale). Software-name rejection stays off
    // here so framegen keeps exactly the devices it accepted before the extraction (CPU types were already excluded).
    std::vector<njgpu::GpuCandidate> cands;
    cands.reserve(devs.size());
    for (VkPhysicalDevice d : devs) {
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(d, &props);
        VkPhysicalDeviceMemoryProperties mp{};
        vkGetPhysicalDeviceMemoryProperties(d, &mp);
        uint64_t heap = 0;
        for (uint32_t i = 0; i < mp.memoryHeapCount; ++i)
            if (mp.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) heap = std::max<uint64_t>(heap, mp.memoryHeaps[i].size);
        njgpu::GpuCandidate c;
        c.vkType = static_cast<int>(props.deviceType);
        c.vendorID = props.vendorID;
        c.deviceID = props.deviceID;
        c.name = props.deviceName;
        c.deviceLocalHeapBytes = heap;
        cands.push_back(std::move(c));
    }
    const njgpu::PickedGpu picked = njgpu::pickBest(cands, njgpu::PickOptions{.rejectSoftwareByName = false});

    const bool found = picked.index >= 0;
    bool bestStorageOk = false;
    const bool dllHasFp16 = res.fp16;
    if (found) {
        VkPhysicalDevice d = devs[static_cast<size_t>(picked.index)];
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(d, &props);

        const uint32_t usable = std::min(instVer, props.apiVersion);
        VkPhysicalDeviceVulkan12Features f12{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
        VkPhysicalDeviceFeatures2 f2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
        if (usable >= VK_API_VERSION_1_2) f2.pNext = &f12;
        if (usable >= VK_API_VERSION_1_1) vkGetPhysicalDeviceFeatures2(d, &f2);

        uint32_t ec = 0;
        vkEnumerateDeviceExtensionProperties(d, nullptr, &ec, nullptr);
        std::vector<VkExtensionProperties> exts(ec);
        if (ec > 0) vkEnumerateDeviceExtensionProperties(d, nullptr, &ec, exts.data());

        const bool storageOk = usable >= VK_API_VERSION_1_1 &&
                               f2.features.shaderStorageImageExtendedFormats &&
                               f2.features.shaderStorageImageReadWithoutFormat &&
                               f2.features.shaderStorageImageWriteWithoutFormat;
#if defined(_WIN32)
        const bool zc = hasExt(exts, "VK_KHR_external_memory_win32") && hasExt(exts, "VK_KHR_external_semaphore_win32");
#else
        const bool zc = hasExt(exts, "VK_KHR_external_memory_fd") && hasExt(exts, "VK_KHR_external_semaphore_fd");
#endif
        bestStorageOk = storageOk;
        res.deviceName = props.deviceName;
        res.deviceUuid = picked.vendorDeviceKey();
        res.fp16 = (dllPath.empty() || dllHasFp16) && (usable >= VK_API_VERSION_1_2 && f12.shaderFloat16 == VK_TRUE);
        res.zeroCopyVk = zc;
    }

    vkDestroyInstance(inst, nullptr);
    if (!found) {
        res.errorCode = "no_vulkan";
        res.errorDetail = "no hardware Vulkan GPU";
        return res;
    }
    if (!bestStorageOk) {
        res.errorCode = "device_unsupported";
        res.errorDetail = "Vulkan storage image features missing";
        return res;
    }
    res.ok = true;
    return res;
}

struct DesktopFrameGenEngine::Impl {
    EngineConfig cfg{};
    uint32_t slots = 0;
    std::unique_ptr<Transport> transport;
    std::thread worker;
    std::atomic<bool> running{false};
    std::atomic<bool> stopRequested{false};
    std::atomic<bool> paused{false};
    std::atomic<bool> antiArtifacts{true};
    std::atomic<int64_t> sourcePeriodOverrideNs{0};
    std::atomic<int64_t> vsyncNs{16'666'667};
    std::atomic<uint64_t> epoch{1};

    // Input queue (host -> worker) & input free slots
    std::mutex inMtx;
    std::condition_variable inCv;
    std::deque<uint32_t> freeInSlots;
    std::deque<InJob> inQueue;

    // Present queue (worker -> host) & output free slots
    std::mutex outMtx;
    std::deque<uint32_t> freeOutSlots;
    std::deque<OutFrame> presentQueue;
    int32_t curDisplayedSlot = -1;
    int32_t prevDisplayedSlot = -1;
    int64_t lastPostNs = -1;

    // Stats (protected by statsMtx)
    mutable std::mutex statsMtx;
    StatsSnapshot stats{};
    int64_t adaptiveMarginNs = 0;
    double computeEmaNs = 0.0;
    int64_t outFpsWindowStartNs = 0;
    uint64_t outFpsWindowFrames = 0;

    void releaseOutSlotLocked(uint32_t s) {
        freeOutSlots.push_back(s);
    }

    int32_t acquireOutSlot() {
        std::lock_guard<std::mutex> lock(outMtx);
        if (freeOutSlots.empty()) return -1;
        const uint32_t s = freeOutSlots.front();
        freeOutSlots.pop_front();
        return static_cast<int32_t>(s);
    }

    void workerMain(std::shared_ptr<tlsfg::ShaderSet> shaders, ProbeResult probe,
                    std::promise<bool> readyPromise, std::future<void> hostInitDoneFuture) {
        const Backend& be = cfg.performance ? kBackend31p : kBackend31;
        const uint64_t gens = static_cast<uint64_t>(cfg.multiplier - 1);
        const float lsfgFlow = 1.0f / std::clamp(cfg.flowScale, 0.25f, 1.0f);
        bool lsfgInit = false;
        TempleLsfg::LocalContext lc{};
        LumaProbe luma{};
        bool lumaOk = false;

        try {
            be.initialize(probe.deviceUuid, false, lsfgFlow, gens, tlsfg::makeLoader(shaders, probe.fp16));
            lsfgInit = true;
            lc = be.createContextLocal(VkExtent2D{cfg.width, cfg.height}, kFormat);

            // Try zero-copy transport first unless staging was forced.
            if (cfg.forceTransport != static_cast<int32_t>(TransportKind::STAGING)) {
#if defined(_WIN32)
                if (cfg.d3d12DeviceStructPtr != 0 && probe.zeroCopyVk) {
                    auto t = createD3d12VkTransport(cfg.d3d12DeviceStructPtr);
                    if (t->initVulkan(lc, cfg.width, cfg.height, slots)) transport = std::move(t);
                }
#else
                if (probe.zeroCopyVk) {
                    auto t = createGlVkTransport();
                    if (t->initVulkan(lc, cfg.width, cfg.height, slots)) transport = std::move(t);
                }
#endif
            }
            if (!transport) {
                auto t = createStagingTransport();
                if (!t->initVulkan(lc, cfg.width, cfg.height, slots)) {
                    throw std::runtime_error("staging initVulkan failed");
                }
                transport = std::move(t);
            }
            lumaOk = luma.init(lc);
        } catch (const std::exception& e) {
            {
                std::lock_guard<std::mutex> lock(statsMtx);
                stats.errorCode = e.what();
                stats.active = false;
            }
            if (transport) { transport->destroyVulkan(); transport.reset(); }
            if (lsfgInit) {
                if (lc.device) { try { be.deleteContext(lc.id); } catch (...) {} }
                try { be.finalize(); } catch (...) {}
            }
            readyPromise.set_value(false);
            return;
        }

        readyPromise.set_value(true);
        // Wait for host thread to finish GL/D3D12 import (or fallback abort).
        hostInitDoneFuture.wait();

        if (stopRequested.load()) {
            luma.destroy(lc.device);
            if (transport) transport->destroyVulkan();
            try { be.waitIdle(); be.deleteContext(lc.id); be.finalize(); } catch (...) {}
            return;
        }

        efg::PeriodEstimator estimator;
        uint64_t presentsDone = 0;
        uint64_t lastEpoch = epoch.load();
        bool needBootstrap = true;

        try {
            while (!stopRequested.load()) {
                InJob job{};
                {
                    std::unique_lock<std::mutex> lock(inMtx);
                    inCv.wait_for(lock, std::chrono::milliseconds(10), [&] {
                        return !inQueue.empty() || stopRequested.load();
                    });
                    if (stopRequested.load()) break;
                    if (inQueue.empty()) continue;
                    job = inQueue.front();
                    inQueue.pop_front();
                }

                const uint64_t curEpoch = epoch.load();
                if (job.epoch != curEpoch) {
                    transport->vkDiscardInput(job.inSlot, job.fenceVal);
                    std::lock_guard<std::mutex> lock(inMtx);
                    freeInSlots.push_back(job.inSlot);
                    continue;
                }
                if (curEpoch != lastEpoch) {
                    lastEpoch = curEpoch;
                    estimator.reset();
                    luma.reset();
                    needBootstrap = true;
                }

                const bool isPaused = paused.load();
                const bool discont = estimator.isDiscontinuity(job.tsNs);
                estimator.update(job.tsNs);
                if (discont) {
                    epoch.fetch_add(1);
                    lastEpoch = epoch.load();
                    luma.reset();
                    needBootstrap = true;
                }

                int64_t periodNs = sourcePeriodOverrideNs.load();
                if (periodNs <= 0) periodNs = estimator.periodNs();
                if (periodNs <= 0) periodNs = kDefaultPeriodNs;

                const auto t0 = nowNs();

                if (isPaused) {
                    // Paused (e.g. seek while paused): pass real frame straight through without LSFG.
                    VkImage targetIn = (presentsDone % 2 == 0) ? lc.in0 : lc.in1;
                    transport->vkUploadInput(job.inSlot, job.fenceVal, targetIn, VK_NULL_HANDLE, VK_NULL_HANDLE);
                    const int32_t outSlot = acquireOutSlot();
                    if (outSlot >= 0) {
                        uint64_t fval = 0;
                        if (transport->vkCopyOutputs({targetIn}, {static_cast<uint32_t>(outSlot)}, &fval)) {
                            std::lock_guard<std::mutex> lock(outMtx);
                            presentQueue.push_back(OutFrame{static_cast<uint32_t>(outSlot), fval, nowNs(), curEpoch, false});
                        } else {
                            std::lock_guard<std::mutex> lock(outMtx);
                            releaseOutSlotLocked(static_cast<uint32_t>(outSlot));
                        }
                    }
                    needBootstrap = true;
                    luma.reset();
                } else if (needBootstrap) {
                    // Bootstrap: seed BOTH in0 and in1 with the first frame and display real frame immediately.
                    transport->vkUploadInput(job.inSlot, job.fenceVal, lc.in0, lc.in1,
                                             lumaOk ? luma.buffer : VK_NULL_HANDLE);
                    be.present(lc.id, -1, {});
                    ++presentsDone;
                    const int32_t outSlot = acquireOutSlot();
                    if (outSlot >= 0) {
                        uint64_t fval = 0;
                        if (transport->vkCopyOutputs({lc.in0}, {static_cast<uint32_t>(outSlot)}, &fval)) {
                            if (lumaOk) luma.evaluateCut();  // seed prev luma
                            std::lock_guard<std::mutex> lock(outMtx);
                            presentQueue.push_back(OutFrame{static_cast<uint32_t>(outSlot), fval, nowNs(), curEpoch, false});
                        } else {
                            std::lock_guard<std::mutex> lock(outMtx);
                            releaseOutSlotLocked(static_cast<uint32_t>(outSlot));
                        }
                    }
                    needBootstrap = false;
                } else {
                    // Normal generation interval: upload into parity slot, run LSFG, check scene cut, copy outputs.
                    const bool toIn0 = (presentsDone % 2 == 0);
                    VkImage realImg = toIn0 ? lc.in0 : lc.in1;
                    transport->vkUploadInput(job.inSlot, job.fenceVal,
                                             toIn0 ? lc.in0 : VK_NULL_HANDLE,
                                             toIn0 ? VK_NULL_HANDLE : lc.in1,
                                             lumaOk ? luma.buffer : VK_NULL_HANDLE);
                    be.present(lc.id, -1, {});
                    ++presentsDone;

                    // Acquire N output slots (N-1 generated + 1 real); if ring is tight, fall back to real-only.
                    const size_t nOut = lc.out.size() + 1;
                    std::vector<uint32_t> picked;
                    picked.reserve(nOut);
                    {
                        std::lock_guard<std::mutex> lock(outMtx);
                        while (picked.size() < nOut && !freeOutSlots.empty()) {
                            picked.push_back(freeOutSlots.front());
                            freeOutSlots.pop_front();
                        }
                    }

                    bool onlyReal = (picked.size() < nOut);
                    if (onlyReal && picked.size() > 1) {
                        std::lock_guard<std::mutex> lock(outMtx);
                        while (picked.size() > 1) {
                            releaseOutSlotLocked(picked.back());
                            picked.pop_back();
                        }
                    }

                    if (!picked.empty()) {
                        std::vector<VkImage> srcs;
                        if (onlyReal) {
                            srcs.push_back(realImg);
                        } else {
                            for (VkImage oi : lc.out) srcs.push_back(oi);
                            srcs.push_back(realImg);
                        }
                        uint64_t fval = 0;
                        const bool copied = transport->vkCopyOutputs(srcs, picked, &fval);
                        const int64_t computeNs = nowNs() - t0;
                        computeEmaNs = (computeEmaNs <= 0.0) ? static_cast<double>(computeNs)
                                                             : (computeEmaNs * 7.0 + static_cast<double>(computeNs)) / 8.0;

                        const bool isCut = copied && lumaOk && antiArtifacts.load() && luma.evaluateCut();
                        if (!copied) {
                            std::lock_guard<std::mutex> lock(outMtx);
                            for (uint32_t s : picked) releaseOutSlotLocked(s);
                        } else if (isCut && !onlyReal) {
                            // Scene cut: discard generated frames, keep only the real frame (last slot).
                            const uint32_t realSlot = picked.back();
                            {
                                std::lock_guard<std::mutex> lock(outMtx);
                                for (size_t i = 0; i + 1 < picked.size(); ++i) releaseOutSlotLocked(picked[i]);
                                const int64_t lat = efg::latencyNs(periodNs, static_cast<int64_t>(computeEmaNs), kBaseMarginNs + adaptiveMarginNs);
                                presentQueue.push_back(OutFrame{realSlot, fval, job.tsNs + lat, curEpoch, false});
                            }
                            std::lock_guard<std::mutex> sl(statsMtx);
                            stats.sceneCuts++;
                        } else {
                            const int64_t lat = efg::latencyNs(periodNs, static_cast<int64_t>(computeEmaNs), kBaseMarginNs + adaptiveMarginNs);
                            int64_t targets[kMaxMultiplier]{};
                            const int n = static_cast<int>(picked.size());
                            efg::schedule(job.tsNs - periodNs, lat, periodNs, n, targets);
                            std::lock_guard<std::mutex> lock(outMtx);
                            for (int i = 0; i < n; ++i) {
                                const bool gen = !onlyReal && (i + 1 < n);
                                presentQueue.push_back(OutFrame{picked[static_cast<size_t>(i)], fval, targets[i], curEpoch, gen});
                            }
                        }

                        // Update live telemetry
                        const int64_t lat = efg::latencyNs(periodNs, static_cast<int64_t>(computeEmaNs), kBaseMarginNs + adaptiveMarginNs);
                        std::lock_guard<std::mutex> sl(statsMtx);
                        stats.inFrames++;
                        stats.inFps = periodNs > 0 ? (1e9 / static_cast<double>(periodNs)) : 0.0;
                        stats.computeMs = computeEmaNs / 1e6;
                        stats.latencyMs = static_cast<double>(lat) / 1e6;
                        stats.overloaded = computeEmaNs > static_cast<double>(periodNs) * 0.85;
                    } else {
                        std::lock_guard<std::mutex> sl(statsMtx);
                        stats.droppedLate++;
                    }
                }

                // Release input slot back to host thread.
                {
                    std::lock_guard<std::mutex> lock(inMtx);
                    freeInSlots.push_back(job.inSlot);
                }
            }
        } catch (const std::exception& e) {
            std::lock_guard<std::mutex> sl(statsMtx);
            stats.errorCode = e.what();
            stats.active = false;
        }

        luma.destroy(lc.device);
        if (transport) transport->destroyVulkan();
        try {
            be.waitIdle();
            be.deleteContext(lc.id);
            be.finalize();
        } catch (...) {}
    }
};

DesktopFrameGenEngine::DesktopFrameGenEngine() : impl_(std::make_unique<Impl>()) {}
DesktopFrameGenEngine::~DesktopFrameGenEngine() { stop(); }

bool DesktopFrameGenEngine::start(const EngineConfig& cfg) {
    stop();
    if (cfg.width == 0 || cfg.height == 0 || cfg.dllPath.empty()) return false;

    auto shaders = std::make_shared<tlsfg::ShaderSet>();
    if (tlsfg::extractShaders(pathFromUtf8(cfg.dllPath), *shaders) != tlsfg::FgResult::OK) {
        std::lock_guard<std::mutex> lock(impl_->statsMtx);
        impl_->stats.errorCode = "missing_spirv";
        return false;
    }
    ProbeResult probe = probeDesktopGpuAndDll(cfg.dllPath);
    if (!probe.ok) {
        std::lock_guard<std::mutex> lock(impl_->statsMtx);
        impl_->stats.errorCode = probe.errorCode;
        return false;
    }

    impl_->cfg = cfg;
    impl_->cfg.multiplier = std::clamp(cfg.multiplier, kMinMultiplier, kMaxMultiplier);
    impl_->slots = requiredSlots(impl_->cfg.multiplier);
    impl_->stopRequested.store(false);
    impl_->paused.store(false);
    impl_->antiArtifacts.store(cfg.antiArtifacts);
    impl_->vsyncNs.store(cfg.vsyncNs > 0 ? cfg.vsyncNs : 16'666'667);
    impl_->epoch.store(1);
    impl_->curDisplayedSlot = -1;
    impl_->prevDisplayedSlot = -1;
    impl_->lastPostNs = -1;
    impl_->computeEmaNs = 0.0;
    impl_->adaptiveMarginNs = 0;
    impl_->outFpsWindowStartNs = nowNs();
    impl_->outFpsWindowFrames = 0;

    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->freeInSlots.clear();
        impl_->inQueue.clear();
        for (uint32_t i = 0; i < kInputSlots; ++i) impl_->freeInSlots.push_back(i);
    }
    {
        std::lock_guard<std::mutex> lock(impl_->outMtx);
        impl_->freeOutSlots.clear();
        impl_->presentQueue.clear();
        for (uint32_t i = 0; i < impl_->slots; ++i) impl_->freeOutSlots.push_back(i);
    }

    std::promise<bool> readyPromise;
    auto readyFuture = readyPromise.get_future();
    std::promise<void> hostInitDone;
    auto hostInitDoneFuture = hostInitDone.get_future();

    impl_->worker = std::thread(&Impl::workerMain, impl_.get(), shaders, probe,
                                std::move(readyPromise), std::move(hostInitDoneFuture));

    const bool vkOk = readyFuture.get();
    if (!vkOk) {
        if (impl_->worker.joinable()) impl_->worker.join();
        return false;
    }

    // Complete host-side import on the caller (GL / D3D12) thread.
    const bool hostOk = impl_->transport && impl_->transport->initHost(impl_->cfg.width, impl_->cfg.height);
    if (!hostOk) {
        // Zero-copy host import failed (e.g. GL driver rejected opaque_fd): restart cleanly in staging mode!
        impl_->stopRequested.store(true);
        hostInitDone.set_value();
        if (impl_->worker.joinable()) impl_->worker.join();
        if (impl_->transport) { impl_->transport->destroyHost(); impl_->transport.reset(); }
        if (cfg.forceTransport != static_cast<int32_t>(TransportKind::STAGING)) {
            EngineConfig fallback = cfg;
            fallback.forceTransport = static_cast<int32_t>(TransportKind::STAGING);
            return start(fallback);
        }
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(impl_->statsMtx);
        impl_->stats = StatsSnapshot{};
        impl_->stats.active = true;
        impl_->stats.transport = static_cast<int32_t>(impl_->transport->kind());
        impl_->stats.multiplier = impl_->cfg.multiplier;
    }
    impl_->running.store(true);
    hostInitDone.set_value();
    return true;
}

void DesktopFrameGenEngine::stop() {
    if (!impl_->running.load() && !impl_->worker.joinable()) return;
    impl_->running.store(false);
    impl_->stopRequested.store(true);
    impl_->inCv.notify_all();
    if (impl_->transport) impl_->transport->flushHostBeforeDestroy();
    if (impl_->worker.joinable()) impl_->worker.join();
    if (impl_->transport) {
        impl_->transport->destroyHost();
        impl_->transport.reset();
    }
    std::lock_guard<std::mutex> lock(impl_->statsMtx);
    impl_->stats.active = false;
}

void DesktopFrameGenEngine::setPaused(bool paused) {
    const bool was = impl_->paused.exchange(paused);
    if (paused && !was) {
        impl_->epoch.fetch_add(1);
    }
}

void DesktopFrameGenEngine::flush() {
    impl_->epoch.fetch_add(1);
    impl_->inCv.notify_one();
    {
        std::lock_guard<std::mutex> lock(impl_->outMtx);
        while (!impl_->presentQueue.empty()) {
            impl_->releaseOutSlotLocked(impl_->presentQueue.front().slot);
            impl_->presentQueue.pop_front();
        }
    }
}

void DesktopFrameGenEngine::setSourcePeriodNs(int64_t periodNs) {
    impl_->sourcePeriodOverrideNs.store(periodNs > 0 ? periodNs : 0);
}

void DesktopFrameGenEngine::setVsyncNs(int64_t vsyncNs) {
    if (vsyncNs > 0) impl_->vsyncNs.store(vsyncNs);
}

void DesktopFrameGenEngine::setAntiArtifacts(bool enabled) {
    impl_->antiArtifacts.store(enabled);
}

bool DesktopFrameGenEngine::submitGlFrame(uint32_t srcGlTexId, int64_t timestampNs) {
    if (!impl_->running.load() || !impl_->transport) return false;
    uint32_t slot = 0;
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        if (impl_->freeInSlots.empty()) return false;
        slot = impl_->freeInSlots.front();
        impl_->freeInSlots.pop_front();
    }
    if (!impl_->transport->hostSubmitGl(slot, srcGlTexId)) {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->freeInSlots.push_back(slot);
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->inQueue.push_back(InJob{slot, 0, timestampNs, impl_->epoch.load()});
    }
    impl_->inCv.notify_one();
    return true;
}

bool DesktopFrameGenEngine::submitD3d12Frame(uintptr_t srcResourcePtr, int64_t timestampNs) {
    if (!impl_->running.load() || !impl_->transport) return false;
    uint32_t slot = 0;
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        if (impl_->freeInSlots.empty()) return false;
        slot = impl_->freeInSlots.front();
        impl_->freeInSlots.pop_front();
    }
    uint64_t fval = 0;
    if (!impl_->transport->hostSubmitD3d12(slot, srcResourcePtr, &fval)) {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->freeInSlots.push_back(slot);
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->inQueue.push_back(InJob{slot, fval, timestampNs, impl_->epoch.load()});
    }
    impl_->inCv.notify_one();
    return true;
}

bool DesktopFrameGenEngine::submitRgbaFrame(const uint8_t* rgba, size_t bytes, int64_t timestampNs) {
    if (!impl_->running.load() || !impl_->transport) return false;
    uint32_t slot = 0;
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        if (impl_->freeInSlots.empty()) return false;
        slot = impl_->freeInSlots.front();
        impl_->freeInSlots.pop_front();
    }
    if (!impl_->transport->hostSubmitRgba(slot, rgba, bytes)) {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->freeInSlots.push_back(slot);
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(impl_->inMtx);
        impl_->inQueue.push_back(InJob{slot, 0, timestampNs, impl_->epoch.load()});
    }
    impl_->inCv.notify_one();
    return true;
}

int32_t DesktopFrameGenEngine::acquirePresentSlot(int64_t now) {
    if (!impl_->running.load() || !impl_->transport) return -1;
    const uint64_t curEpoch = impl_->epoch.load();
    const int64_t vsync = impl_->vsyncNs.load();

    std::optional<OutFrame> chosen;
    uint64_t dropped = 0;
    {
        std::lock_guard<std::mutex> lock(impl_->outMtx);
        // Drop stale epochs immediately.
        while (!impl_->presentQueue.empty() && impl_->presentQueue.front().epoch != curEpoch) {
            impl_->releaseOutSlotLocked(impl_->presentQueue.front().slot);
            impl_->presentQueue.pop_front();
        }
        // Drop late frames when a newer frame is already available.
        while (impl_->presentQueue.size() > 1 && efg::isLate(impl_->presentQueue.front().targetNs, now, vsync)) {
            impl_->releaseOutSlotLocked(impl_->presentQueue.front().slot);
            impl_->presentQueue.pop_front();
            ++dropped;
        }
        if (!impl_->presentQueue.empty()) {
            const OutFrame& front = impl_->presentQueue.front();
            const int64_t aligned = efg::alignToVsync(front.targetNs, impl_->lastPostNs, vsync, vsync / 4);
            if (aligned <= now + vsync / 2) {
                chosen = front;
                impl_->presentQueue.pop_front();
                impl_->lastPostNs = now;
                // Keep previously displayed slot pinned for one frame so GPU draw finishes before Vulkan reuses it.
                if (impl_->prevDisplayedSlot >= 0) {
                    impl_->releaseOutSlotLocked(static_cast<uint32_t>(impl_->prevDisplayedSlot));
                }
                impl_->prevDisplayedSlot = impl_->curDisplayedSlot;
                impl_->curDisplayedSlot = static_cast<int32_t>(chosen->slot);
            }
        }
    }

    if (chosen) {
        impl_->transport->hostWaitSlot(chosen->slot, chosen->fenceVal);
        std::lock_guard<std::mutex> sl(impl_->statsMtx);
        impl_->stats.outFrames++;
        if (chosen->generated) impl_->stats.generatedFrames++;
        impl_->stats.droppedLate += dropped;
        if (dropped > 0) {
            impl_->adaptiveMarginNs = std::min<int64_t>(impl_->adaptiveMarginNs + 1'000'000, kMaxAdaptiveMarginNs);
        }
        impl_->outFpsWindowFrames++;
        const int64_t elapsed = now - impl_->outFpsWindowStartNs;
        if (elapsed >= 500'000'000) {
            impl_->stats.outFps = static_cast<double>(impl_->outFpsWindowFrames) * 1e9 / static_cast<double>(elapsed);
            impl_->outFpsWindowStartNs = now;
            impl_->outFpsWindowFrames = 0;
        }
    } else if (dropped > 0) {
        std::lock_guard<std::mutex> sl(impl_->statsMtx);
        impl_->stats.droppedLate += dropped;
    }

    return impl_->curDisplayedSlot;
}

int64_t DesktopFrameGenEngine::slotHandle(uint32_t slot) const {
    return impl_->transport ? impl_->transport->hostSlotHandle(slot) : 0;
}

bool DesktopFrameGenEngine::readSlotRgba(uint32_t slot, uint8_t* dst, size_t bytes) const {
    return impl_->transport ? impl_->transport->hostReadSlotRgba(slot, dst, bytes) : false;
}

uint32_t DesktopFrameGenEngine::slotCount() const { return impl_->slots; }

TransportKind DesktopFrameGenEngine::transportKind() const {
    return impl_->transport ? impl_->transport->kind() : TransportKind::NONE;
}

StatsSnapshot DesktopFrameGenEngine::stats() const {
    std::lock_guard<std::mutex> lock(impl_->statsMtx);
    return impl_->stats;
}

}  // namespace njfg
