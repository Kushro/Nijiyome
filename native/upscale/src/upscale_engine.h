// SPDX-License-Identifier: AGPL-3.0-or-later
// GPU image upscaling with Real-ESRGAN realesr-animevideov3 (x2/x3/x4) on ncnn + Vulkan (docs/plan-upscaling.md §4.1).
// No CPU inference path exists: without a hardware Vulkan device probe() fails and init() refuses to run.
#pragma once

#include "gpu_pick.h"

#include <cstdint>
#include <filesystem>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace njup {

enum Result : int {
    kOk = 0,
    kCancelled = 1,
    kErrNotInitialized = -1,
    kErrBadArgs = -2,
    kErrNoVulkan = -3,
    kErrBadDevice = -4,      // index out of range, CPU or software device
    kErrModelMissing = -5,
    kErrModelLoad = -6,
    kErrShaderCompile = -7,
    kErrGpu = -8,            // allocation or submit failed
};

struct DeviceEntry {
    njgpu::GpuCandidate cand;
    int ncnnIndex = -1;
    bool usable = false;
    bool fp16Storage = false;
    bool int8Storage = false;
    std::string driverName;
};

struct ProbeInfo {
    bool ok = false;
    std::string error;   // "", "no_vulkan", "no_hardware_gpu"
    std::string detail;
    njgpu::PickedGpu gpu;  // gpu.index is the ncnn device index
    bool fp16 = false;
    bool int8 = false;
    std::string driverName;
    bool matchedPreferred = false;  // the preferred UUID/LUID named the chosen device
    std::vector<DeviceEntry> devices;
};

// Enumerates ncnn's Vulkan devices (creating ncnn's VkInstance once per process) and applies the shared framegen policy
// plus software-renderer rejection. preferredKey is an optional device UUID (32 hex) or LUID (16 hex), see gpu_pick.h.
ProbeInfo probe(const std::string& preferredKey);

class Engine {
public:
    Engine();
    ~Engine();
    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    // Binds the engine to an ncnn device and a directory holding realesr-animevideov3-x{2,3,4}.param/.bin. Nets load
    // lazily per scale on first use. Calling init again with a different device or directory releases first.
    int init(int deviceIndex, const std::filesystem::path& modelDir);

    // RGBA8 in, RGBA8 out of size (w*scale) x (h*scale). tile <= 0 picks the platform default; any tile is clamped to
    // the VRAM budget. cancelled is polled before every tile; returning true ends the job with kCancelled.
    int upscale(const uint8_t* rgba, int w, int h, int scale, int tile, const std::function<bool()>& cancelled,
                std::vector<uint8_t>& out);

    // Frees the nets, pipelines and pooled GPU buffers. ncnn's VkInstance and VkDevice stay alive until process exit.
    void release();

    bool initialized() const;

private:
    struct Impl;
    std::unique_ptr<Impl> d;
};

// Tile edge (input pixels) that fits the budget, exposed for logging. 0 = platform default (128 desktop, 64 Android).
int chooseTileSize(int requested, int scale, int imageWidth, bool fp16, bool int8, uint32_t heapBudgetMb);

}  // namespace njup
