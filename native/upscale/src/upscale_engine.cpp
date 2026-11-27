// SPDX-License-Identifier: AGPL-3.0-or-later
// Real-ESRGAN realesr-animevideov3 engine on ncnn + Vulkan.
//
// The tiled inference loop in Engine::upscale is adapted from realesrgan-ncnn-vulkan src/realesrgan.cpp
// (RealESRGAN::process, non-TTA path, commit 37026f4 / v0.2.0), MIT License:
//   Copyright (c) 2021 Xintao Wang; Copyright (c) 2019 nihui (realsr-ncnn-vulkan).
//   Full text: native/upscale/third_party/realesrgan-ncnn-vulkan/LICENSE.
// Changes: RGBA8 in/out only (opaque images run as RGB), shaders compiled at runtime (upscale_shaders.h), cancellation
// polled before every tile, tile size clamped to a VRAM budget, pooled GPU buffers freed after every job, the x2/x3/x4
// nets loaded lazily, and the device chosen by native/common/gpu_pick.h instead of ncnn's default device.
#include "upscale_engine.h"

#include "upscale_shaders.h"

// ncnn
#include "gpu.h"
#include "layer.h"
#include "net.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <memory>

namespace njup {

namespace {

constexpr int kPrepadding = 10;  // realesrgan-ncnn-vulkan uses 10 for the "models" directory family
constexpr int kMinTile = 32;
constexpr int kMaxTile = 400;
constexpr int kMaxSide = 8192;  // input guard; the Kotlin planner caps outputs far below this (plan §4.3)
#if defined(__ANDROID__)
constexpr int kDefaultTile = 64;
constexpr uint32_t kVramCapMb = 96;
#else
constexpr int kDefaultTile = 128;
constexpr uint32_t kVramCapMb = 256;
#endif
constexpr const char* kModelName = "realesr-animevideov3";

njgpu::PickOptions pickOptions() {
    return njgpu::PickOptions{.rejectSoftwareByName = true};
}

uint64_t deviceLocalHeap(const VkPhysicalDeviceMemoryProperties& mp) {
    uint64_t heap = 0;
    for (uint32_t i = 0; i < mp.memoryHeapCount; ++i)
        if (mp.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) heap = std::max<uint64_t>(heap, mp.memoryHeaps[i].size);
    return heap;
}

void queryIdProperties(const ncnn::GpuInfo& info, njgpu::GpuCandidate& c) {
    if (!ncnn::support_VK_KHR_get_physical_device_properties2 || !ncnn::vkGetPhysicalDeviceProperties2KHR) return;
    if (info.api_version() < VK_MAKE_VERSION(1, 1, 0)) return;
    njgpu::VkIdPropertiesLayout id{};
    VkPhysicalDeviceProperties2KHR p2;
    std::memset(&p2, 0, sizeof(p2));
    p2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2_KHR;
    p2.pNext = &id;
    ncnn::vkGetPhysicalDeviceProperties2KHR(info.physicalDevice(), &p2);
    njgpu::applyIdProperties(c, id);
}

std::vector<DeviceEntry> enumerateDevices() {
    std::vector<DeviceEntry> out;
    const int n = ncnn::get_gpu_count();  // creates ncnn's VkInstance on first call
    for (int i = 0; i < n; ++i) {
        const ncnn::GpuInfo& info = ncnn::get_gpu_info(i);
        const VkPhysicalDeviceProperties& props = info.physicalDeviceProperties();
        DeviceEntry e;
        e.ncnnIndex = i;
        e.cand.vkType = static_cast<int>(props.deviceType);
        e.cand.vendorID = props.vendorID;
        e.cand.deviceID = props.deviceID;
        e.cand.name = props.deviceName;
        e.cand.deviceLocalHeapBytes = deviceLocalHeap(info.physicalDeviceMemoryProperties());
        queryIdProperties(info, e.cand);
        e.fp16Storage = info.support_fp16_storage();
        e.int8Storage = info.support_int8_storage();
        const char* drv = info.driver_name();
        if (drv) e.driverName = drv;
        // ncnn's own type() == 3 is its CPU bucket; never accept it even if the Vulkan type looked like a GPU.
        e.usable = njgpu::isUsable(e.cand, pickOptions()) && info.type() != 3;
        out.push_back(std::move(e));
    }
    return out;
}

std::string normalizeKey(const std::string& key) {
    std::string out;
    for (unsigned char ch : key) {
        if (ch == '-' || std::isspace(ch)) continue;
        out += static_cast<char>(std::tolower(ch));
    }
    return out;
}

std::FILE* openRead(const std::filesystem::path& p) {
#if defined(_WIN32)
    return _wfopen(p.c_str(), L"rb");
#else
    return std::fopen(p.c_str(), "rb");
#endif
}

// Rough device memory of one tile for this net (64 feature channels at tile resolution, the x4 PixelShuffle/Interp
// stage, and the per-row output buffer). Deliberately pessimistic; build 1 measures the real figure (plan §7).
uint64_t estimateTileBytes(int tile, int scale, int imageWidth, bool fp16, bool int8) {
    const uint64_t eb = fp16 ? 2 : 4;
    const uint64_t padded = static_cast<uint64_t>(tile) + 2 * kPrepadding;
    const uint64_t features = padded * padded * 64 * eb * 8;                  // live blobs + conv workspace
    const uint64_t upscaled = (padded * 4) * (padded * 4) * 3 * eb * 4;      // network runs at x4 internally
    const uint64_t rowOut = static_cast<uint64_t>(imageWidth) * scale * tile * scale * (int8 ? 4 : 16);
    return features + upscaled + rowOut;
}

}  // namespace

int chooseTileSize(int requested, int scale, int imageWidth, bool fp16, bool int8, uint32_t heapBudgetMb) {
    int tile = requested > 0 ? requested : kDefaultTile;
    tile = std::clamp(tile, kMinTile, kMaxTile);
    uint32_t capMb = kVramCapMb;
    if (heapBudgetMb > 0) capMb = std::min<uint32_t>(capMb, heapBudgetMb / 4);  // at most 25 % of the heap budget
    const uint64_t cap = static_cast<uint64_t>(std::max<uint32_t>(capMb, 16)) * 1024 * 1024;
    while (tile > kMinTile && estimateTileBytes(tile, scale, imageWidth, fp16, int8) > cap) tile -= 16;
    return std::max(tile, kMinTile);
}

ProbeInfo probe(const std::string& preferredKey) {
    ProbeInfo r;
    r.devices = enumerateDevices();
    if (r.devices.empty()) {
        r.error = "no_vulkan";
        r.detail = "no Vulkan loader or no Vulkan device";
        return r;
    }
    std::vector<njgpu::GpuCandidate> cands;
    cands.reserve(r.devices.size());
    for (const DeviceEntry& e : r.devices) {
        njgpu::GpuCandidate c = e.cand;
        if (!e.usable) c.vkType = njgpu::kVkTypeCpu;  // make the policy skip ncnn-rejected devices too
        cands.push_back(std::move(c));
    }
    const njgpu::PickedGpu picked = njgpu::pickPreferred(cands, preferredKey, pickOptions());
    if (picked.index < 0) {
        r.error = "no_hardware_gpu";
        r.detail = "only CPU or software Vulkan devices";
        return r;
    }
    const DeviceEntry& e = r.devices[static_cast<size_t>(picked.index)];
    r.ok = true;
    r.gpu = picked;
    r.fp16 = e.fp16Storage;
    r.int8 = e.int8Storage;
    r.driverName = e.driverName;
    const std::string key = normalizeKey(preferredKey);
    r.matchedPreferred = !key.empty() && (key == njgpu::uuidHex(picked) || key == njgpu::luidHex(picked));
    return r;
}

struct Engine::Impl {
    int device = -1;
    std::filesystem::path modelDir;
    ncnn::VulkanDevice* vkdev = nullptr;
    std::array<std::unique_ptr<ncnn::Net>, 5> nets{};  // indexed by scale (2..4)
    std::array<ncnn::Layer*, 5> bicubic{};               // alpha upscalers, indexed by scale
    ncnn::Pipeline* preproc = nullptr;
    ncnn::Pipeline* postproc = nullptr;
    ncnn::Option opt;  // the sanitized Option of the first loaded net; every net shares device and flags
    bool optReady = false;
    bool int8Path = false;

    void destroyAll() {
        for (ncnn::Layer*& l : bicubic) {
            if (!l) continue;
            l->destroy_pipeline(opt);
            delete l;
            l = nullptr;
        }
        delete preproc;
        preproc = nullptr;
        delete postproc;
        postproc = nullptr;
        for (auto& n : nets) n.reset();
        optReady = false;
        int8Path = false;
        vkdev = nullptr;
        device = -1;
        modelDir.clear();
    }

    ncnn::Option baseOption() const {
        const ncnn::GpuInfo& info = ncnn::get_gpu_info(device);
        ncnn::Option o;
        o.use_vulkan_compute = true;
        o.vulkan_device_index = device;
        o.num_threads = 1;  // the CPU only feeds the GPU
        o.lightmode = true;
        o.use_fp16_packed = info.support_fp16_packed();
        o.use_fp16_storage = info.support_fp16_storage();
        o.use_fp16_arithmetic = false;
        o.use_bf16_packed = false;
        o.use_bf16_storage = false;
        o.use_int8_storage = info.support_int8_storage();
        o.use_int8_arithmetic = false;
        return o;
    }

    int compilePipelines() {
        ncnn::Option sopt = opt;
        sopt.use_int8_storage = int8Path;
        sopt.vulkan_device_index = device;
        std::vector<uint32_t> spv;
        if (ncnn::compile_spirv_module(kPreprocComp, sopt, spv) != 0 || spv.empty()) return kErrShaderCompile;
        const std::vector<ncnn::vk_specialization_type> noSpecs;
        preproc = new ncnn::Pipeline(vkdev);
        preproc->set_optimal_local_size_xyz(32, 32, 3);
        if (preproc->create(spv.data(), spv.size() * sizeof(uint32_t), noSpecs) != 0) return kErrShaderCompile;
        spv.clear();
        if (ncnn::compile_spirv_module(kPostprocComp, sopt, spv) != 0 || spv.empty()) return kErrShaderCompile;
        postproc = new ncnn::Pipeline(vkdev);
        postproc->set_optimal_local_size_xyz(32, 32, 3);
        if (postproc->create(spv.data(), spv.size() * sizeof(uint32_t), noSpecs) != 0) return kErrShaderCompile;
        return kOk;
    }

    int ensureNet(int scale) {
        if (nets[scale]) return kOk;
        const std::string stem = std::string(kModelName) + "-x" + std::to_string(scale);
        const std::filesystem::path param = modelDir / (stem + ".param");
        std::filesystem::path bin = modelDir / (stem + ".bin");
        // The three official .bin files are byte-identical (x2/x3 are the x4 net plus a downscale in the .param), so
        // packaging may ship only the x4 weights.
        std::error_code ec;
        if (!std::filesystem::exists(bin, ec)) bin = modelDir / (std::string(kModelName) + "-x4.bin");
        if (!std::filesystem::exists(param, ec) || !std::filesystem::exists(bin, ec)) return kErrModelMissing;

        auto net = std::make_unique<ncnn::Net>();
        net->opt = optReady ? opt : baseOption();
        net->set_vulkan_device(device);
        {
            std::FILE* fp = openRead(param);
            if (!fp) return kErrModelMissing;
            const int rc = net->load_param(fp);
            std::fclose(fp);
            if (rc != 0) return kErrModelLoad;
        }
        {
            std::FILE* fp = openRead(bin);
            if (!fp) return kErrModelMissing;
            const int rc = net->load_model(fp);
            std::fclose(fp);
            if (rc != 0) return kErrModelLoad;
        }
        if (!optReady) {
            opt = net->opt;  // load_param drops the flags the device lacks
            int8Path = opt.use_fp16_storage && opt.use_int8_storage;
            optReady = true;
            const int rc = compilePipelines();
            if (rc != kOk) return rc;
        }
        nets[scale] = std::move(net);
        return kOk;
    }

    ncnn::Layer* ensureBicubic(int scale) {
        if (bicubic[scale]) return bicubic[scale];
        ncnn::Layer* l = ncnn::create_layer("Interp");
        if (!l) return nullptr;
        l->vkdev = vkdev;
        ncnn::ParamDict pd;
        pd.set(0, 3);  // bicubic
        pd.set(1, static_cast<float>(scale));
        pd.set(2, static_cast<float>(scale));
        l->load_param(pd);
        if (l->create_pipeline(opt) != 0) {
            delete l;
            return nullptr;
        }
        bicubic[scale] = l;
        return l;
    }
};

Engine::Engine() : d(std::make_unique<Impl>()) {}

Engine::~Engine() { release(); }

bool Engine::initialized() const { return d->vkdev != nullptr && d->optReady; }

void Engine::release() { d->destroyAll(); }

int Engine::init(int deviceIndex, const std::filesystem::path& modelDir) {
    if (initialized() && d->device == deviceIndex && d->modelDir == modelDir) return kOk;
    release();

    const std::vector<DeviceEntry> devices = enumerateDevices();
    if (devices.empty()) return kErrNoVulkan;
    if (deviceIndex < 0 || deviceIndex >= static_cast<int>(devices.size())) return kErrBadDevice;
    if (!devices[static_cast<size_t>(deviceIndex)].usable) return kErrBadDevice;  // never a CPU/software device

    ncnn::VulkanDevice* vkdev = ncnn::get_gpu_device(deviceIndex);
    if (!vkdev) return kErrGpu;
    d->device = deviceIndex;
    d->modelDir = modelDir;
    d->vkdev = vkdev;

    // Load one net now so a broken driver, a missing model or a shader that does not compile fails here (plan R1).
    const int rc = d->ensureNet(2);
    if (rc != kOk) {
        d->destroyAll();
        return rc;
    }
    return kOk;
}

int Engine::upscale(const uint8_t* rgba, int w, int h, int scale, int tile, const std::function<bool()>& cancelled,
                    std::vector<uint8_t>& out) {
    if (!initialized()) return kErrNotInitialized;
    if (!rgba || w <= 0 || h <= 0 || w > kMaxSide || h > kMaxSide || scale < 2 || scale > 4) return kErrBadArgs;
    const int netRc = d->ensureNet(scale);
    if (netRc != kOk) return netRc;
    const ncnn::Net& net = *d->nets[scale];

    bool hasAlpha = false;
    const size_t pixels = static_cast<size_t>(w) * static_cast<size_t>(h);
    for (size_t i = 0; i < pixels && !hasAlpha; ++i) hasAlpha = rgba[i * 4 + 3] != 255;
    const int channels = hasAlpha ? 4 : 3;
    ncnn::Layer* alphaUp = nullptr;
    if (hasAlpha) {
        alphaUp = d->ensureBicubic(scale);
        if (!alphaUp) return kErrGpu;
    }

    const bool int8Path = d->int8Path;
    const int T = chooseTileSize(tile, scale, w, d->opt.use_fp16_storage, int8Path, d->vkdev->get_heap_budget());
    const int pad = kPrepadding;
    const int outW = w * scale;
    out.assign(static_cast<size_t>(outW) * static_cast<size_t>(h) * scale * 4, 0);

    ncnn::VkAllocator* blobAlloc = d->vkdev->acquire_blob_allocator();
    ncnn::VkAllocator* stagingAlloc = d->vkdev->acquire_staging_allocator();
    int rc = kOk;
    {
        ncnn::Option opt = net.opt;
        opt.blob_vkallocator = blobAlloc;
        opt.workspace_vkallocator = blobAlloc;
        opt.staging_vkallocator = stagingAlloc;

        const int xtiles = (w + T - 1) / T;
        const int ytiles = (h + T - 1) / T;
        const size_t tileElem = opt.use_fp16_storage ? 2u : 4u;

        for (int yi = 0; yi < ytiles && rc == kOk; yi++) {
            if (cancelled && cancelled()) {
                rc = kCancelled;
                break;
            }
            const int tileHNoPad = std::min((yi + 1) * T, h) - yi * T;
            const int inY0 = std::max(yi * T - pad, 0);
            const int inY1 = std::min((yi + 1) * T + pad, h);
            const unsigned char* rowPtr = rgba + static_cast<size_t>(inY0) * w * 4;

            ncnn::Mat in;
            if (int8Path) {
                in = ncnn::Mat(w, inY1 - inY0, const_cast<unsigned char*>(rowPtr), static_cast<size_t>(4u), 1);
            } else {
                in = ncnn::Mat::from_pixels(rowPtr, hasAlpha ? ncnn::Mat::PIXEL_RGBA : ncnn::Mat::PIXEL_RGBA2RGB, w, inY1 - inY0);
            }

            ncnn::VkCompute cmd(d->vkdev);

            ncnn::VkMat inGpu;
            cmd.record_clone(in, inGpu, opt);
            if (xtiles > 1) {
                if (cmd.submit_and_wait() != 0) { rc = kErrGpu; break; }
                cmd.reset();
            }

            const int outY0 = yi * T;
            const int outY1 = std::min((yi + 1) * T, h);
            ncnn::VkMat outGpu;
            if (int8Path)
                outGpu.create(outW, (outY1 - outY0) * scale, static_cast<size_t>(4u), 1, blobAlloc);
            else
                outGpu.create(outW, (outY1 - outY0) * scale, channels, static_cast<size_t>(4u), 1, blobAlloc);
            if (inGpu.empty() || outGpu.empty()) { rc = kErrGpu; break; }

            for (int xi = 0; xi < xtiles; xi++) {
                if (xi > 0 && cancelled && cancelled()) {
                    rc = kCancelled;
                    break;
                }
                const int tileWNoPad = std::min((xi + 1) * T, w) - xi * T;

                // preproc: crop the padded tile, normalize, split alpha
                ncnn::VkMat inTile;
                ncnn::VkMat inAlphaTile;
                {
                    const int tileX0 = xi * T - pad;
                    const int tileX1 = std::min((xi + 1) * T, w) + pad;
                    const int tileY0 = yi * T - pad;
                    const int tileY1 = std::min((yi + 1) * T, h) + pad;
                    inTile.create(tileX1 - tileX0, tileY1 - tileY0, 3, tileElem, 1, blobAlloc);
                    if (channels == 4) inAlphaTile.create(tileWNoPad, tileHNoPad, 1, tileElem, 1, blobAlloc);
                    if (inTile.empty() || (channels == 4 && inAlphaTile.empty())) { rc = kErrGpu; break; }

                    std::vector<ncnn::VkMat> bindings(3);
                    bindings[0] = inGpu;
                    bindings[1] = inTile;
                    bindings[2] = inAlphaTile;

                    std::vector<ncnn::vk_constant_type> constants(13);
                    constants[0].i = inGpu.w;
                    constants[1].i = inGpu.h;
                    constants[2].i = static_cast<int>(inGpu.cstep);
                    constants[3].i = inTile.w;
                    constants[4].i = inTile.h;
                    constants[5].i = static_cast<int>(inTile.cstep);
                    constants[6].i = pad;
                    constants[7].i = pad;
                    constants[8].i = xi * T;
                    constants[9].i = std::min(yi * T, pad);
                    constants[10].i = channels;
                    constants[11].i = inAlphaTile.w;
                    constants[12].i = inAlphaTile.h;

                    ncnn::VkMat dispatcher;
                    dispatcher.w = inTile.w;
                    dispatcher.h = inTile.h;
                    dispatcher.c = channels;
                    cmd.record_pipeline(d->preproc, bindings, constants, dispatcher);
                }

                // the network
                ncnn::VkMat outTile;
                {
                    ncnn::Extractor ex = net.create_extractor();
                    ex.set_blob_vkallocator(blobAlloc);
                    ex.set_workspace_vkallocator(blobAlloc);
                    ex.set_staging_vkallocator(stagingAlloc);
                    if (ex.input("data", inTile) != 0 || ex.extract("output", outTile, cmd) != 0 || outTile.empty()) {
                        rc = kErrGpu;
                        break;
                    }
                }

                ncnn::VkMat outAlphaTile;
                if (channels == 4 && alphaUp->forward(inAlphaTile, outAlphaTile, cmd, opt) != 0) {
                    rc = kErrGpu;
                    break;
                }

                // postproc: denormalize and write this tile into the row buffer
                {
                    const int gxMax = std::min(T * scale, outGpu.w - xi * T * scale);
                    std::vector<ncnn::VkMat> bindings(3);
                    bindings[0] = outTile;
                    bindings[1] = outAlphaTile;
                    bindings[2] = outGpu;

                    std::vector<ncnn::vk_constant_type> constants(13);
                    constants[0].i = outTile.w;
                    constants[1].i = outTile.h;
                    constants[2].i = static_cast<int>(outTile.cstep);
                    constants[3].i = outGpu.w;
                    constants[4].i = outGpu.h;
                    constants[5].i = static_cast<int>(outGpu.cstep);
                    constants[6].i = xi * T * scale;
                    constants[7].i = gxMax;
                    constants[8].i = pad * scale;
                    constants[9].i = pad * scale;
                    constants[10].i = channels;
                    constants[11].i = outAlphaTile.w;
                    constants[12].i = outAlphaTile.h;

                    ncnn::VkMat dispatcher;
                    dispatcher.w = gxMax;
                    dispatcher.h = outGpu.h;
                    dispatcher.c = int8Path ? 4 : channels;
                    cmd.record_pipeline(d->postproc, bindings, constants, dispatcher);
                }

                if (xtiles > 1) {
                    if (cmd.submit_and_wait() != 0) { rc = kErrGpu; break; }
                    cmd.reset();
                }
            }
            if (rc != kOk) break;

            // download this row of tiles
            ncnn::Mat outRow;
            cmd.record_clone(outGpu, outRow, opt);
            if (cmd.submit_and_wait() != 0 || outRow.empty()) { rc = kErrGpu; break; }
            unsigned char* dst = out.data() + static_cast<size_t>(outY0) * scale * outW * 4;
            if (int8Path) {
                std::memcpy(dst, outRow.data, static_cast<size_t>(outRow.w) * outRow.h * 4);
            } else {
                outRow.to_pixels(dst, hasAlpha ? ncnn::Mat::PIXEL_RGBA : ncnn::Mat::PIXEL_RGB2RGBA);
            }
        }
    }
    // Give the pooled VRAM back between jobs so the player and framegen never compete with an idle cache.
    blobAlloc->clear();
    stagingAlloc->clear();
    d->vkdev->reclaim_blob_allocator(blobAlloc);
    d->vkdev->reclaim_staging_allocator(stagingAlloc);

    if (rc != kOk) out.clear();
    return rc;
}

}  // namespace njup
