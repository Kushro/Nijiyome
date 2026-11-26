// SPDX-License-Identifier: GPL-3.0-only
// Universal CPU staging fallback transport (works on Linux and Windows without external memory interop).
#include "fg_desktop_types.h"

#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

namespace njfg {

namespace {

struct StagingBuf {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    uint8_t* ptr = nullptr;
};

class StagingTransport final : public Transport {
public:
    TransportKind kind() const override { return TransportKind::STAGING; }
    uint32_t slotCount() const override { return slots_; }

    bool initVulkan(const TempleLsfg::LocalContext& lc, uint32_t width, uint32_t height, uint32_t slots) override {
        lc_ = lc;
        width_ = width;
        height_ = height;
        slots_ = slots;
        frameBytes_ = static_cast<size_t>(width) * height * 4;

        const VkCommandPoolCreateInfo cpci{
            .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
            .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
            .queueFamilyIndex = lc_.queueFamily,
        };
        if (vkCreateCommandPool(lc_.device, &cpci, nullptr, &pool_) != VK_SUCCESS) return false;
        const VkCommandBufferAllocateInfo cbai{
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
            .commandPool = pool_,
            .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            .commandBufferCount = 2,
        };
        if (vkAllocateCommandBuffers(lc_.device, &cbai, cbs_) != VK_SUCCESS) return false;
        const VkFenceCreateInfo fci{.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        if (vkCreateFence(lc_.device, &fci, nullptr, &fence_) != VK_SUCCESS) return false;

        up_.resize(kInputSlots);
        for (auto& b : up_) {
            if (!createBuf(b, frameBytes_, VK_BUFFER_USAGE_TRANSFER_SRC_BIT)) { destroyVulkan(); return false; }
        }
        down_.resize(slots_);
        for (auto& b : down_) {
            if (!createBuf(b, frameBytes_, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) { destroyVulkan(); return false; }
        }
        return true;
    }

    bool initHost(uint32_t /*width*/, uint32_t /*height*/) override { return true; }
    void flushHostBeforeDestroy() override {}

    void destroyVulkan() override {
        if (lc_.device == VK_NULL_HANDLE) return;
        vkDeviceWaitIdle(lc_.device);
        for (auto& b : up_) destroyBuf(b);
        for (auto& b : down_) destroyBuf(b);
        if (fence_) { vkDestroyFence(lc_.device, fence_, nullptr); fence_ = VK_NULL_HANDLE; }
        if (pool_) { vkDestroyCommandPool(lc_.device, pool_, nullptr); pool_ = VK_NULL_HANDLE; }
        lc_.device = VK_NULL_HANDLE;
    }

    void destroyHost() override {}

    bool hostSubmitRgba(uint32_t inSlot, const uint8_t* rgba, size_t bytes) override {
        if (inSlot >= up_.size() || !up_[inSlot].ptr || bytes < frameBytes_) return false;
        std::memcpy(up_[inSlot].ptr, rgba, frameBytes_);
        return true;
    }

    bool vkUploadInput(uint32_t inSlot, uint64_t /*fenceVal*/, VkImage dst0, VkImage dst1,
                       VkBuffer lumaBuf256) override {
        if (inSlot >= up_.size()) return false;
        VkCommandBuffer cb = cbs_[0];
        if (!beginCb(cb)) return false;

        const VkMemoryBarrier mb1{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_HOST_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             0, 1, &mb1, 0, nullptr, 0, nullptr);

        const VkBufferImageCopy bic{
            .bufferOffset = 0, .bufferRowLength = 0, .bufferImageHeight = 0,
            .imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
            .imageOffset = {0, 0, 0}, .imageExtent = {width_, height_, 1},
        };
        if (dst0) vkCmdCopyBufferToImage(cb, up_[inSlot].buffer, dst0, VK_IMAGE_LAYOUT_GENERAL, 1, &bic);
        if (dst1) vkCmdCopyBufferToImage(cb, up_[inSlot].buffer, dst1, VK_IMAGE_LAYOUT_GENERAL, 1, &bic);

        VkImage lumaSrc = dst0 ? dst0 : dst1;
        if (lumaSrc && lumaBuf256) {
            const VkMemoryBarrier mbMid{
                .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
                .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
                .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            };
            vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                                 0, 1, &mbMid, 0, nullptr, 0, nullptr);
            recordLumaSample8x8(cb, lumaSrc, width_, height_, lumaBuf256);
        }

        const VkMemoryBarrier mb2{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_HOST_READ_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_HOST_BIT,
                             0, 1, &mb2, 0, nullptr, 0, nullptr);
        if (vkEndCommandBuffer(cb) != VK_SUCCESS) return false;
        const VkSubmitInfo si{.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cb};
        return vkQueueSubmit(lc_.queue, 1, &si, VK_NULL_HANDLE) == VK_SUCCESS;
    }

    bool vkCopyOutputs(const std::vector<VkImage>& srcImages, const std::vector<uint32_t>& dstSlots,
                       uint64_t* outFenceVal) override {
        if (outFenceVal) *outFenceVal = 0;
        if (srcImages.size() != dstSlots.size() || srcImages.empty()) return false;
        VkCommandBuffer cb = cbs_[1];
        if (!beginCb(cb)) return false;

        const VkMemoryBarrier mb1{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 1, &mb1, 0, nullptr, 0, nullptr);

        const VkBufferImageCopy bic{
            .bufferOffset = 0, .bufferRowLength = 0, .bufferImageHeight = 0,
            .imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
            .imageOffset = {0, 0, 0}, .imageExtent = {width_, height_, 1},
        };
        for (size_t i = 0; i < srcImages.size(); ++i) {
            const uint32_t s = dstSlots[i];
            if (s >= down_.size()) return false;
            vkCmdCopyImageToBuffer(cb, srcImages[i], VK_IMAGE_LAYOUT_GENERAL, down_[s].buffer, 1, &bic);
        }

        const VkMemoryBarrier mb2{
            .sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_HOST_READ_BIT,
        };
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                             0, 1, &mb2, 0, nullptr, 0, nullptr);
        if (vkEndCommandBuffer(cb) != VK_SUCCESS) return false;

        vkResetFences(lc_.device, 1, &fence_);
        const VkSubmitInfo si{.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cb};
        if (vkQueueSubmit(lc_.queue, 1, &si, fence_) != VK_SUCCESS) return false;
        return vkWaitForFences(lc_.device, 1, &fence_, VK_TRUE, 2'000'000'000ull) == VK_SUCCESS;
    }

    void hostWaitSlot(uint32_t /*slot*/, uint64_t /*fenceVal*/) override {}
    int64_t hostSlotHandle(uint32_t /*slot*/) const override { return 0; }

    bool hostReadSlotRgba(uint32_t slot, uint8_t* dst, size_t bytes) const override {
        if (slot >= down_.size() || !down_[slot].ptr || bytes < frameBytes_) return false;
        std::memcpy(dst, down_[slot].ptr, frameBytes_);
        return true;
    }

private:
    bool beginCb(VkCommandBuffer cb) {
        if (vkResetCommandBuffer(cb, 0) != VK_SUCCESS) return false;
        const VkCommandBufferBeginInfo bi{
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
            .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        };
        return vkBeginCommandBuffer(cb, &bi) == VK_SUCCESS;
    }

    bool createBuf(StagingBuf& out, VkDeviceSize size, VkBufferUsageFlags usage) {
        const VkBufferCreateInfo bci{
            .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
            .size = size,
            .usage = usage,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        };
        if (vkCreateBuffer(lc_.device, &bci, nullptr, &out.buffer) != VK_SUCCESS) return false;
        VkMemoryRequirements req{};
        vkGetBufferMemoryRequirements(lc_.device, out.buffer, &req);
        VkPhysicalDeviceMemoryProperties mp{};
        vkGetPhysicalDeviceMemoryProperties(lc_.physicalDevice, &mp);
        const VkMemoryPropertyFlags base = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        uint32_t mt = findVkMemoryType(mp, req.memoryTypeBits, base | VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
        if (mt == UINT32_MAX) mt = findVkMemoryType(mp, req.memoryTypeBits, base);
        if (mt == UINT32_MAX) return false;
        const VkMemoryAllocateInfo mai{
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .allocationSize = req.size,
            .memoryTypeIndex = mt,
        };
        if (vkAllocateMemory(lc_.device, &mai, nullptr, &out.memory) != VK_SUCCESS) return false;
        if (vkBindBufferMemory(lc_.device, out.buffer, out.memory, 0) != VK_SUCCESS) return false;
        void* p = nullptr;
        if (vkMapMemory(lc_.device, out.memory, 0, VK_WHOLE_SIZE, 0, &p) != VK_SUCCESS) return false;
        out.ptr = static_cast<uint8_t*>(p);
        return true;
    }

    void destroyBuf(StagingBuf& b) {
        if (b.ptr && b.memory) { vkUnmapMemory(lc_.device, b.memory); b.ptr = nullptr; }
        if (b.buffer) { vkDestroyBuffer(lc_.device, b.buffer, nullptr); b.buffer = VK_NULL_HANDLE; }
        if (b.memory) { vkFreeMemory(lc_.device, b.memory, nullptr); b.memory = VK_NULL_HANDLE; }
    }

    TempleLsfg::LocalContext lc_{};
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    uint32_t slots_ = 0;
    size_t frameBytes_ = 0;
    VkCommandPool pool_ = VK_NULL_HANDLE;
    VkCommandBuffer cbs_[2]{};
    VkFence fence_ = VK_NULL_HANDLE;
    std::vector<StagingBuf> up_;
    std::vector<StagingBuf> down_;
};

}  // namespace

std::unique_ptr<Transport> createStagingTransport() {
    return std::make_unique<StagingTransport>();
}

}  // namespace njfg
