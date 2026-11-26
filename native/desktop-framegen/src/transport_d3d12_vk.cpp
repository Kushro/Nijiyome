// SPDX-License-Identifier: GPL-3.0-only
// Windows zero-copy Direct3D 12 <-> Vulkan transport using D3D12 shared resources/fences
// (VK_KHR_external_memory_win32 D3D12_RESOURCE_BIT + VK_KHR_external_semaphore_win32 D3D12_FENCE_BIT).
#if defined(_WIN32)

#include "fg_desktop_types.h"

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_4.h>
#include <vulkan/vulkan_win32.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>

namespace njfg {

namespace {

// Safely probe Skiko's DirectXDevice struct to locate ID3D12Device* and ID3D12CommandQueue*
// via COM QueryInterface rather than assuming fixed struct offsets.
bool probeDirectXDevice(uintptr_t structPtr, ID3D12Device** outDev, ID3D12CommandQueue** outQueue) {
    if (structPtr == 0) return false;
    *outDev = nullptr;
    *outQueue = nullptr;
    auto** slots = reinterpret_cast<void**>(structPtr);
    for (int i = 0; i < 8 && (!*outDev || !*outQueue); ++i) {
        __try {
            void* candidate = slots[i];
            if (!candidate || reinterpret_cast<uintptr_t>(candidate) < 0x10000) continue;
            auto* unk = static_cast<IUnknown*>(candidate);
            if (!*outDev) {
                ID3D12Device* d = nullptr;
                if (SUCCEEDED(unk->QueryInterface(__uuidof(ID3D12Device), reinterpret_cast<void**>(&d))) && d) {
                    *outDev = d;
                    continue;
                }
            }
            if (!*outQueue) {
                ID3D12CommandQueue* q = nullptr;
                if (SUCCEEDED(unk->QueryInterface(__uuidof(ID3D12CommandQueue), reinterpret_cast<void**>(&q))) && q) {
                    *outQueue = q;
                    continue;
                }
            }
        } __except (EXCEPTION_EXECUTE_HANDLER) {
            continue;
        }
    }
    if (*outDev && *outQueue) return true;
    if (*outDev) { (*outDev)->Release(); *outDev = nullptr; }
    if (*outQueue) { (*outQueue)->Release(); *outQueue = nullptr; }
    return false;
}

struct SharedD3dImg {
    ID3D12Resource* d3dRes = nullptr;
    HANDLE ntHandle = nullptr;
    VkImage vkImage = VK_NULL_HANDLE;
    VkDeviceMemory vkMem = VK_NULL_HANDLE;
};

struct SharedD3dFence {
    ID3D12Fence* d3dFence = nullptr;
    HANDLE ntHandle = nullptr;
    VkSemaphore vkSem = VK_NULL_HANDLE;
    uint64_t value = 0;
};

class D3d12VkTransport final : public Transport {
public:
    explicit D3d12VkTransport(uintptr_t structPtr) : structPtr_(structPtr) {}
    ~D3d12VkTransport() override {
        closeHandles();
    }

    TransportKind kind() const override { return TransportKind::D3D12_VULKAN_ZERO_COPY; }
    uint32_t slotCount() const override { return slots_; }

    bool initVulkan(const TempleLsfg::LocalContext& lc, uint32_t width, uint32_t height, uint32_t slots) override {
        lc_ = lc;
        width_ = width;
        height_ = height;
        slots_ = slots;

        if (!probeDirectXDevice(structPtr_, &d3dDev_, &d3dQueue_)) return false;

        getMemWin32Props_ = reinterpret_cast<PFN_vkGetMemoryWin32HandlePropertiesKHR>(
            vkGetDeviceProcAddr(lc_.device, "vkGetMemoryWin32HandlePropertiesKHR"));
        importSemWin32_ = reinterpret_cast<PFN_vkImportSemaphoreWin32HandleKHR>(
            vkGetDeviceProcAddr(lc_.device, "vkImportSemaphoreWin32HandleKHR"));
        if (!getMemWin32Props_ || !importSemWin32_) {
            destroyAllOnFail();
            return false;
        }

        // Create D3D12 copy command allocator + command list for fast GPU-to-GPU CopyResource.
        for (uint32_t i = 0; i < kInputSlots; ++i) {
            if (FAILED(d3dDev_->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&copyAlloc_[i])))) {
                destroyAllOnFail();
                return false;
            }
        }
        if (FAILED(d3dDev_->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, copyAlloc_[0], nullptr, IID_PPV_ARGS(&copyList_)))) {
            destroyAllOnFail();
            return false;
        }
        copyList_->Close();

        // Create Vulkan command pool + command buffers + fence.
        const VkCommandPoolCreateInfo cpci{
            .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
            .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
            .queueFamilyIndex = lc_.queueFamily,
        };
        if (vkCreateCommandPool(lc_.device, &cpci, nullptr, &vkPool_) != VK_SUCCESS) { destroyAllOnFail(); return false; }
        const VkCommandBufferAllocateInfo cbai{
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
            .commandPool = vkPool_,
            .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            .commandBufferCount = 3,
        };
        if (vkAllocateCommandBuffers(lc_.device, &cbai, vkCbs_) != VK_SUCCESS) { destroyAllOnFail(); return false; }
        const VkFenceCreateInfo fci{.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        if (vkCreateFence(lc_.device, &fci, nullptr, &vkFence_) != VK_SUCCESS) { destroyAllOnFail(); return false; }

        // Allocate shared D3D12 textures and import into Vulkan via VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT.
        inImgs_.resize(kInputSlots);
        for (uint32_t i = 0; i < kInputSlots; ++i) {
            if (!createSharedTexture(inImgs_[i])) { destroyAllOnFail(); return false; }
        }
        ringImgs_.resize(slots_);
        for (uint32_t i = 0; i < slots_; ++i) {
            if (!createSharedTexture(ringImgs_[i])) { destroyAllOnFail(); return false; }
        }
        if (!createSharedTimelineFence(inFence_) || !createSharedTimelineFence(outFence_)) {
            destroyAllOnFail();
            return false;
        }

        if (!transitionAllToGeneral()) {
            destroyAllOnFail();
            return false;
        }
        ready_ = true;
        return true;
    }

    bool initHost(uint32_t /*width*/, uint32_t /*height*/) override {
        return ready_;
    }

    void flushHostBeforeDestroy() override {
        if (!d3dQueue_ || !d3dDev_) return;
        ID3D12Fence* drainFence = nullptr;
        if (SUCCEEDED(d3dDev_->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&drainFence)))) {
            HANDLE ev = CreateEventW(nullptr, FALSE, FALSE, nullptr);
            if (ev) {
                d3dQueue_->Signal(drainFence, 1);
                if (drainFence->GetCompletedValue() < 1) {
                    drainFence->SetEventOnCompletion(1, ev);
                    WaitForSingleObject(ev, 2000);
                }
                CloseHandle(ev);
            }
            drainFence->Release();
        }
    }

    void destroyVulkan() override {
        if (lc_.device != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(lc_.device);
            for (auto& i : inImgs_) destroyVkImg(i);
            for (auto& r : ringImgs_) destroyVkImg(r);
            if (inFence_.vkSem) { vkDestroySemaphore(lc_.device, inFence_.vkSem, nullptr); inFence_.vkSem = VK_NULL_HANDLE; }
            if (outFence_.vkSem) { vkDestroySemaphore(lc_.device, outFence_.vkSem, nullptr); outFence_.vkSem = VK_NULL_HANDLE; }
            if (vkFence_) { vkDestroyFence(lc_.device, vkFence_, nullptr); vkFence_ = VK_NULL_HANDLE; }
            if (vkPool_) { vkDestroyCommandPool(lc_.device, vkPool_, nullptr); vkPool_ = VK_NULL_HANDLE; }
            lc_.device = VK_NULL_HANDLE;
        }
    }

    void destroyHost() override {
        closeHandles();
        for (auto& i : inImgs_) if (i.d3dRes) { i.d3dRes->Release(); i.d3dRes = nullptr; }
        for (auto& r : ringImgs_) if (r.d3dRes) { r.d3dRes->Release(); r.d3dRes = nullptr; }
        if (inFence_.d3dFence) { inFence_.d3dFence->Release(); inFence_.d3dFence = nullptr; }
        if (outFence_.d3dFence) { outFence_.d3dFence->Release(); outFence_.d3dFence = nullptr; }
        if (copyList_) { copyList_->Release(); copyList_ = nullptr; }
        for (uint32_t i = 0; i < kInputSlots; ++i) {
            if (copyAlloc_[i]) { copyAlloc_[i]->Release(); copyAlloc_[i] = nullptr; }
        }
        if (d3dQueue_) { d3dQueue_->Release(); d3dQueue_ = nullptr; }
        if (d3dDev_) { d3dDev_->Release(); d3dDev_ = nullptr; }
        ready_ = false;
    }

    bool hostSubmitD3d12(uint32_t inSlot, uintptr_t srcResourcePtr, uint64_t* outFenceVal) override {
        if (!ready_ || inSlot >= inImgs_.size() || srcResourcePtr == 0) return false;
        auto* srcRes = reinterpret_cast<ID3D12Resource*>(srcResourcePtr);
        if (FAILED(copyAlloc_[inSlot]->Reset())) return false;
        if (FAILED(copyList_->Reset(copyAlloc_[inSlot], nullptr))) return false;
        copyList_->CopyResource(inImgs_[inSlot].d3dRes, srcRes);
        if (FAILED(copyList_->Close())) return false;
        ID3D12CommandList* lists[] = {copyList_};
        d3dQueue_->ExecuteCommandLists(1, lists);
        const uint64_t sig = ++inFence_.value;
        if (FAILED(d3dQueue_->Signal(inFence_.d3dFence, sig))) return false;
        if (outFenceVal) *outFenceVal = sig;
        return true;
    }

    bool vkUploadInput(uint32_t inSlot, uint64_t fenceVal, VkImage dst0, VkImage dst1,
                       VkBuffer lumaBuf256) override {
        if (inSlot >= inImgs_.size()) return false;
        VkCommandBuffer cb = vkCbs_[0];
        if (!beginCb(cb)) return false;

        const VkMemoryBarrier mb1{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             0, 1, &mb1, 0, nullptr, 0, nullptr);

        const VkImageCopy reg = fullCopy();
        if (dst0) vkCmdCopyImage(cb, inImgs_[inSlot].vkImage, VK_IMAGE_LAYOUT_GENERAL, dst0, VK_IMAGE_LAYOUT_GENERAL, 1, &reg);
        if (dst1) vkCmdCopyImage(cb, inImgs_[inSlot].vkImage, VK_IMAGE_LAYOUT_GENERAL, dst1, VK_IMAGE_LAYOUT_GENERAL, 1, &reg);
        if (lumaBuf256) recordLumaSample8x8(cb, inImgs_[inSlot].vkImage, width_, height_, lumaBuf256);

        const VkMemoryBarrier mb2{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_HOST_READ_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_HOST_BIT,
                             0, 1, &mb2, 0, nullptr, 0, nullptr);
        if (vkEndCommandBuffer(cb) != VK_SUCCESS) return false;

        const VkTimelineSemaphoreSubmitInfo tsi{
            .sType = VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO,
            .waitSemaphoreValueCount = 1,
            .pWaitSemaphoreValues = &fenceVal,
        };
        const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        const VkSubmitInfo si{
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .pNext = &tsi,
            .waitSemaphoreCount = 1,
            .pWaitSemaphores = &inFence_.vkSem,
            .pWaitDstStageMask = &waitStage,
            .commandBufferCount = 1,
            .pCommandBuffers = &cb,
        };
        return vkQueueSubmit(lc_.queue, 1, &si, VK_NULL_HANDLE) == VK_SUCCESS;
    }

    void vkDiscardInput(uint32_t /*inSlot*/, uint64_t fenceVal) override {
        if (!ready_ || fenceVal == 0 || inFence_.vkSem == VK_NULL_HANDLE || lc_.device == VK_NULL_HANDLE) return;
        const VkSemaphoreWaitInfo wi{
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO,
            .semaphoreCount = 1,
            .pSemaphores = &inFence_.vkSem,
            .pValues = &fenceVal,
        };
        vkWaitSemaphores(lc_.device, &wi, 2'000'000'000ull);
    }

    bool vkCopyOutputs(const std::vector<VkImage>& srcImages, const std::vector<uint32_t>& dstSlots,
                       uint64_t* outFenceVal) override {
        if (srcImages.size() != dstSlots.size() || srcImages.empty()) return false;
        VkCommandBuffer cb = vkCbs_[1];
        if (!beginCb(cb)) return false;

        const VkMemoryBarrier mb1{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 1, &mb1, 0, nullptr, 0, nullptr);

        const VkImageCopy reg = fullCopy();
        for (size_t i = 0; i < srcImages.size(); ++i) {
            const uint32_t s = dstSlots[i];
            if (s >= ringImgs_.size()) return false;
            vkCmdCopyImage(cb, srcImages[i], VK_IMAGE_LAYOUT_GENERAL,
                           ringImgs_[s].vkImage, VK_IMAGE_LAYOUT_GENERAL, 1, &reg);
        }

        const VkMemoryBarrier mb2{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_HOST_READ_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 1, &mb2, 0, nullptr, 0, nullptr);
        if (vkEndCommandBuffer(cb) != VK_SUCCESS) return false;

        const uint64_t sigVal = ++outFence_.value;
        if (outFenceVal) *outFenceVal = sigVal;
        const VkTimelineSemaphoreSubmitInfo tsi{
            .sType = VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO,
            .signalSemaphoreValueCount = 1,
            .pSignalSemaphoreValues = &sigVal,
        };
        vkResetFences(lc_.device, 1, &vkFence_);
        const VkSubmitInfo si{
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .pNext = &tsi,
            .commandBufferCount = 1,
            .pCommandBuffers = &cb,
            .signalSemaphoreCount = 1,
            .pSignalSemaphores = &outFence_.vkSem,
        };
        if (vkQueueSubmit(lc_.queue, 1, &si, vkFence_) != VK_SUCCESS) return false;
        return vkWaitForFences(lc_.device, 1, &vkFence_, VK_TRUE, 2'000'000'000ull) == VK_SUCCESS;
    }

    void hostWaitSlot(uint32_t /*slot*/, uint64_t fenceVal) override {
        if (!ready_ || fenceVal == 0) return;
        d3dQueue_->Wait(outFence_.d3dFence, fenceVal);
    }

    int64_t hostSlotHandle(uint32_t slot) const override {
        if (!ready_ || slot >= ringImgs_.size()) return 0;
        return static_cast<int64_t>(reinterpret_cast<uintptr_t>(ringImgs_[slot].d3dRes));
    }

private:
    VkImageCopy fullCopy() const {
        return VkImageCopy{
            .srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, .srcOffset = {0, 0, 0},
            .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, .dstOffset = {0, 0, 0},
            .extent = {width_, height_, 1},
        };
    }

    bool beginCb(VkCommandBuffer cb) {
        if (vkResetCommandBuffer(cb, 0) != VK_SUCCESS) return false;
        const VkCommandBufferBeginInfo bi{
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
            .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        };
        return vkBeginCommandBuffer(cb, &bi) == VK_SUCCESS;
    }

    bool createSharedTexture(SharedD3dImg& out) {
        D3D12_HEAP_PROPERTIES hp{};
        hp.Type = D3D12_HEAP_TYPE_DEFAULT;
        D3D12_RESOURCE_DESC rd{};
        rd.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
        rd.Width = width_;
        rd.Height = height_;
        rd.DepthOrArraySize = 1;
        rd.MipLevels = 1;
        rd.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        rd.SampleDesc.Count = 1;
        rd.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
        rd.Flags = D3D12_RESOURCE_FLAG_ALLOW_SIMULTANEOUS_ACCESS | D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET;

        if (FAILED(d3dDev_->CreateCommittedResource(
                &hp, D3D12_HEAP_FLAG_SHARED, &rd, D3D12_RESOURCE_STATE_COMMON,
                nullptr, IID_PPV_ARGS(&out.d3dRes)))) {
            return false;
        }
        if (FAILED(d3dDev_->CreateSharedHandle(out.d3dRes, nullptr, GENERIC_ALL, nullptr, &out.ntHandle))) {
            return false;
        }

        const VkExternalMemoryImageCreateInfo emi{
            .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT,
        };
        const VkImageCreateInfo ici{
            .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
            .pNext = &emi,
            .imageType = VK_IMAGE_TYPE_2D,
            .format = VK_FORMAT_R8G8B8A8_UNORM,
            .extent = {width_, height_, 1},
            .mipLevels = 1,
            .arrayLayers = 1,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .tiling = VK_IMAGE_TILING_OPTIMAL,
            .usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT |
                     VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        };
        if (vkCreateImage(lc_.device, &ici, nullptr, &out.vkImage) != VK_SUCCESS) return false;

        VkMemoryRequirements req{};
        vkGetImageMemoryRequirements(lc_.device, out.vkImage, &req);
        VkMemoryWin32HandlePropertiesKHR hprops{.sType = VK_STRUCTURE_TYPE_MEMORY_WIN32_HANDLE_PROPERTIES_KHR};
        if (getMemWin32Props_(lc_.device, VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT, out.ntHandle, &hprops) != VK_SUCCESS) {
            return false;
        }
        VkPhysicalDeviceMemoryProperties mp{};
        vkGetPhysicalDeviceMemoryProperties(lc_.physicalDevice, &mp);
        const uint32_t mt = findVkMemoryType(mp, req.memoryTypeBits & hprops.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (mt == UINT32_MAX) return false;

        const VkMemoryDedicatedAllocateInfo ded{
            .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
            .image = out.vkImage,
        };
        const VkImportMemoryWin32HandleInfoKHR imp{
            .sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR,
            .pNext = &ded,
            .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT,
            .handle = out.ntHandle,
        };
        const VkMemoryAllocateInfo mai{
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .pNext = &imp,
            .allocationSize = req.size,
            .memoryTypeIndex = mt,
        };
        if (vkAllocateMemory(lc_.device, &mai, nullptr, &out.vkMem) != VK_SUCCESS) return false;
        return vkBindImageMemory(lc_.device, out.vkImage, out.vkMem, 0) == VK_SUCCESS;
    }

    bool createSharedTimelineFence(SharedD3dFence& out) {
        if (FAILED(d3dDev_->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&out.d3dFence)))) return false;
        if (FAILED(d3dDev_->CreateSharedHandle(out.d3dFence, nullptr, GENERIC_ALL, nullptr, &out.ntHandle))) return false;
        const VkSemaphoreTypeCreateInfo stci{
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO,
            .semaphoreType = VK_SEMAPHORE_TYPE_TIMELINE,
            .initialValue = 0,
        };
        const VkSemaphoreCreateInfo sci{
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
            .pNext = &stci,
        };
        if (vkCreateSemaphore(lc_.device, &sci, nullptr, &out.vkSem) != VK_SUCCESS) return false;
        const VkImportSemaphoreWin32HandleInfoKHR imp{
            .sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_WIN32_HANDLE_INFO_KHR,
            .semaphore = out.vkSem,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_D3D12_FENCE_BIT,
            .handle = out.ntHandle,
        };
        return importSemWin32_(lc_.device, &imp) == VK_SUCCESS;
    }

    bool transitionAllToGeneral() {
        VkCommandBuffer cb = vkCbs_[2];
        if (!beginCb(cb)) return false;
        std::vector<VkImageMemoryBarrier> bs;
        auto push = [&](VkImage img) {
            bs.push_back(VkImageMemoryBarrier{
                .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
                .srcAccessMask = 0,
                .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
                .newLayout = VK_IMAGE_LAYOUT_GENERAL,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .image = img,
                .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
            });
        };
        for (auto& i : inImgs_) push(i.vkImage);
        for (auto& r : ringImgs_) push(r.vkImage);
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             0, 0, nullptr, 0, nullptr, static_cast<uint32_t>(bs.size()), bs.data());
        if (vkEndCommandBuffer(cb) != VK_SUCCESS) return false;
        vkResetFences(lc_.device, 1, &vkFence_);
        const VkSubmitInfo si{.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cb};
        if (vkQueueSubmit(lc_.queue, 1, &si, vkFence_) != VK_SUCCESS) return false;
        return vkWaitForFences(lc_.device, 1, &vkFence_, VK_TRUE, 2'000'000'000ull) == VK_SUCCESS;
    }

    void destroyVkImg(SharedD3dImg& img) {
        if (img.vkImage) { vkDestroyImage(lc_.device, img.vkImage, nullptr); img.vkImage = VK_NULL_HANDLE; }
        if (img.vkMem) { vkFreeMemory(lc_.device, img.vkMem, nullptr); img.vkMem = VK_NULL_HANDLE; }
    }

    void closeHandles() {
        for (auto& i : inImgs_) if (i.ntHandle) { CloseHandle(i.ntHandle); i.ntHandle = nullptr; }
        for (auto& r : ringImgs_) if (r.ntHandle) { CloseHandle(r.ntHandle); r.ntHandle = nullptr; }
        if (inFence_.ntHandle) { CloseHandle(inFence_.ntHandle); inFence_.ntHandle = nullptr; }
        if (outFence_.ntHandle) { CloseHandle(outFence_.ntHandle); outFence_.ntHandle = nullptr; }
    }

    void destroyAllOnFail() {
        destroyVulkan();
        destroyHost();
    }

    uintptr_t structPtr_ = 0;
    TempleLsfg::LocalContext lc_{};
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    uint32_t slots_ = 0;
    bool ready_ = false;
    ID3D12Device* d3dDev_ = nullptr;
    ID3D12CommandQueue* d3dQueue_ = nullptr;
    ID3D12CommandAllocator* copyAlloc_[kInputSlots]{};
    ID3D12GraphicsCommandList* copyList_ = nullptr;
    PFN_vkGetMemoryWin32HandlePropertiesKHR getMemWin32Props_ = nullptr;
    PFN_vkImportSemaphoreWin32HandleKHR importSemWin32_ = nullptr;
    VkCommandPool vkPool_ = VK_NULL_HANDLE;
    VkCommandBuffer vkCbs_[3]{};
    VkFence vkFence_ = VK_NULL_HANDLE;
    std::vector<SharedD3dImg> inImgs_;
    std::vector<SharedD3dImg> ringImgs_;
    SharedD3dFence inFence_{};
    SharedD3dFence outFence_{};
};

}  // namespace

std::unique_ptr<Transport> createD3d12VkTransport(uintptr_t d3d12DeviceStructPtr) {
    return std::make_unique<D3d12VkTransport>(d3d12DeviceStructPtr);
}

}  // namespace njfg

#endif  // _WIN32
