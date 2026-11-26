// SPDX-License-Identifier: GPL-3.0-only
// Linux zero-copy OpenGL <-> Vulkan transport using VK_KHR_external_memory_fd,
// VK_KHR_external_semaphore_fd, GL_EXT_memory_object_fd, and GL_EXT_semaphore_fd.
#include "fg_desktop_types.h"

#include <dlfcn.h>
#include <unistd.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <vector>

namespace njfg {

namespace {

using GLenum = unsigned int;
using GLboolean = unsigned char;
using GLbitfield = unsigned int;
using GLint = int;
using GLsizei = int;
using GLuint = unsigned int;
using GLuint64 = uint64_t;

constexpr GLenum kGL_NO_ERROR = 0;
constexpr GLenum kGL_TRUE = 1;
constexpr GLenum kGL_TEXTURE_2D = 0x0DE1;
constexpr GLenum kGL_RGBA8 = 0x8058;
constexpr GLenum kGL_LINEAR = 0x2601;
constexpr GLenum kGL_TEXTURE_MIN_FILTER = 0x2801;
constexpr GLenum kGL_TEXTURE_MAG_FILTER = 0x2800;
constexpr GLenum kGL_TEXTURE_WRAP_S = 0x2802;
constexpr GLenum kGL_TEXTURE_WRAP_T = 0x2803;
constexpr GLenum kGL_CLAMP_TO_EDGE = 0x812F;
constexpr GLenum kGL_TEXTURE_BINDING_2D = 0x8069;
constexpr GLenum kGL_FRAMEBUFFER = 0x8D40;
constexpr GLenum kGL_FRAMEBUFFER_BINDING = 0x8CA6;
constexpr GLenum kGL_COLOR_ATTACHMENT0 = 0x8CE0;
constexpr GLenum kGL_FRAMEBUFFER_COMPLETE = 0x8CD5;
constexpr GLenum kGL_TEXTURE_TILING_EXT = 0x9580;
constexpr GLenum kGL_DEDICATED_MEMORY_OBJECT_EXT = 0x9581;
constexpr GLenum kGL_OPTIMAL_TILING_EXT = 0x9584;
constexpr GLenum kGL_HANDLE_TYPE_OPAQUE_FD_EXT = 0x9586;
constexpr GLenum kGL_LAYOUT_GENERAL_EXT = 0x958D;
constexpr GLbitfield kGL_TEXTURE_FETCH_BARRIER_BIT = 0x00000008;
constexpr GLbitfield kGL_FRAMEBUFFER_BARRIER_BIT = 0x00000400;

using PFN_glXGetProcAddressARB = void* (*)(const unsigned char*);
using PFN_glGetError = GLenum (*)();
using PFN_glGetIntegerv = void (*)(GLenum, GLint*);
using PFN_glFlush = void (*)();
using PFN_glFinish = void (*)();
using PFN_glMemoryBarrier = void (*)(GLbitfield);
using PFN_glGenTextures = void (*)(GLsizei, GLuint*);
using PFN_glDeleteTextures = void (*)(GLsizei, const GLuint*);
using PFN_glBindTexture = void (*)(GLenum, GLuint);
using PFN_glTexParameteri = void (*)(GLenum, GLenum, GLint);
using PFN_glGenFramebuffers = void (*)(GLsizei, GLuint*);
using PFN_glDeleteFramebuffers = void (*)(GLsizei, const GLuint*);
using PFN_glBindFramebuffer = void (*)(GLenum, GLuint);
using PFN_glFramebufferTexture2D = void (*)(GLenum, GLenum, GLenum, GLuint, GLint);
using PFN_glCheckFramebufferStatus = GLenum (*)(GLenum);
using PFN_glCopyImageSubData = void (*)(GLuint, GLenum, GLint, GLint, GLint, GLint,
                                        GLuint, GLenum, GLint, GLint, GLint, GLint,
                                        GLsizei, GLsizei, GLsizei);
using PFN_glCreateMemoryObjectsEXT = void (*)(GLsizei, GLuint*);
using PFN_glDeleteMemoryObjectsEXT = void (*)(GLsizei, const GLuint*);
using PFN_glMemoryObjectParameterivEXT = void (*)(GLuint, GLenum, const GLint*);
using PFN_glImportMemoryFdEXT = void (*)(GLuint, GLuint64, GLenum, GLint);
using PFN_glTexStorageMem2DEXT = void (*)(GLenum, GLsizei, GLenum, GLsizei, GLsizei, GLuint, GLuint64);
using PFN_glGenSemaphoresEXT = void (*)(GLsizei, GLuint*);
using PFN_glDeleteSemaphoresEXT = void (*)(GLsizei, const GLuint*);
using PFN_glImportSemaphoreFdEXT = void (*)(GLuint, GLenum, GLint);
using PFN_glSignalSemaphoreEXT = void (*)(GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*);
using PFN_glWaitSemaphoreEXT = void (*)(GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*);

struct GlFns {
    void* libGl = nullptr;
    PFN_glXGetProcAddressARB getProc = nullptr;
    PFN_glGetError getError = nullptr;
    PFN_glGetIntegerv getIntegerv = nullptr;
    PFN_glFlush flush = nullptr;
    PFN_glFinish finish = nullptr;
    PFN_glMemoryBarrier memoryBarrier = nullptr;
    PFN_glGenTextures genTextures = nullptr;
    PFN_glDeleteTextures deleteTextures = nullptr;
    PFN_glBindTexture bindTexture = nullptr;
    PFN_glTexParameteri texParameteri = nullptr;
    PFN_glGenFramebuffers genFramebuffers = nullptr;
    PFN_glDeleteFramebuffers deleteFramebuffers = nullptr;
    PFN_glBindFramebuffer bindFramebuffer = nullptr;
    PFN_glFramebufferTexture2D framebufferTexture2D = nullptr;
    PFN_glCheckFramebufferStatus checkFramebufferStatus = nullptr;
    PFN_glCopyImageSubData copyImageSubData = nullptr;
    PFN_glCreateMemoryObjectsEXT createMemObjs = nullptr;
    PFN_glDeleteMemoryObjectsEXT deleteMemObjs = nullptr;
    PFN_glMemoryObjectParameterivEXT memObjParamiv = nullptr;
    PFN_glImportMemoryFdEXT importMemFd = nullptr;
    PFN_glTexStorageMem2DEXT texStorageMem2D = nullptr;
    PFN_glGenSemaphoresEXT genSems = nullptr;
    PFN_glDeleteSemaphoresEXT delSems = nullptr;
    PFN_glImportSemaphoreFdEXT importSemFd = nullptr;
    PFN_glSignalSemaphoreEXT signalSem = nullptr;
    PFN_glWaitSemaphoreEXT waitSem = nullptr;

    template <typename T>
    T load(const char* name) const {
        void* p = getProc ? getProc(reinterpret_cast<const unsigned char*>(name)) : nullptr;
        if (!p && libGl) p = dlsym(libGl, name);
        return reinterpret_cast<T>(p);
    }

    bool init() {
        libGl = dlopen("libGL.so.1", RTLD_LAZY | RTLD_NOLOAD);
        if (!libGl) libGl = dlopen("libGL.so.1", RTLD_LAZY);
        if (!libGl) return false;
        getProc = reinterpret_cast<PFN_glXGetProcAddressARB>(dlsym(libGl, "glXGetProcAddressARB"));
        if (!getProc) getProc = reinterpret_cast<PFN_glXGetProcAddressARB>(dlsym(libGl, "glXGetProcAddress"));
        if (!getProc) return false;

        getError = load<PFN_glGetError>("glGetError");
        getIntegerv = load<PFN_glGetIntegerv>("glGetIntegerv");
        flush = load<PFN_glFlush>("glFlush");
        finish = load<PFN_glFinish>("glFinish");
        memoryBarrier = load<PFN_glMemoryBarrier>("glMemoryBarrier");
        genTextures = load<PFN_glGenTextures>("glGenTextures");
        deleteTextures = load<PFN_glDeleteTextures>("glDeleteTextures");
        bindTexture = load<PFN_glBindTexture>("glBindTexture");
        texParameteri = load<PFN_glTexParameteri>("glTexParameteri");
        genFramebuffers = load<PFN_glGenFramebuffers>("glGenFramebuffers");
        deleteFramebuffers = load<PFN_glDeleteFramebuffers>("glDeleteFramebuffers");
        bindFramebuffer = load<PFN_glBindFramebuffer>("glBindFramebuffer");
        framebufferTexture2D = load<PFN_glFramebufferTexture2D>("glFramebufferTexture2D");
        checkFramebufferStatus = load<PFN_glCheckFramebufferStatus>("glCheckFramebufferStatus");
        copyImageSubData = load<PFN_glCopyImageSubData>("glCopyImageSubData");
        createMemObjs = load<PFN_glCreateMemoryObjectsEXT>("glCreateMemoryObjectsEXT");
        deleteMemObjs = load<PFN_glDeleteMemoryObjectsEXT>("glDeleteMemoryObjectsEXT");
        memObjParamiv = load<PFN_glMemoryObjectParameterivEXT>("glMemoryObjectParameterivEXT");
        importMemFd = load<PFN_glImportMemoryFdEXT>("glImportMemoryFdEXT");
        texStorageMem2D = load<PFN_glTexStorageMem2DEXT>("glTexStorageMem2DEXT");
        genSems = load<PFN_glGenSemaphoresEXT>("glGenSemaphoresEXT");
        delSems = load<PFN_glDeleteSemaphoresEXT>("glDeleteSemaphoresEXT");
        importSemFd = load<PFN_glImportSemaphoreFdEXT>("glImportSemaphoreFdEXT");
        signalSem = load<PFN_glSignalSemaphoreEXT>("glSignalSemaphoreEXT");
        waitSem = load<PFN_glWaitSemaphoreEXT>("glWaitSemaphoreEXT");

        return getError && getIntegerv && flush && finish && genTextures && deleteTextures &&
               bindTexture && texParameteri && genFramebuffers && deleteFramebuffers &&
               bindFramebuffer && framebufferTexture2D && checkFramebufferStatus &&
               copyImageSubData && createMemObjs && deleteMemObjs && memObjParamiv &&
               importMemFd && texStorageMem2D && genSems && delSems && importSemFd &&
               signalSem && waitSem;
    }

    void drainErrors() const {
        if (!getError) return;
        for (int i = 0; i < 8 && getError() != kGL_NO_ERROR; ++i) {}
    }
};

struct SharedImg {
    VkImage vkImage = VK_NULL_HANDLE;
    VkDeviceMemory vkMem = VK_NULL_HANDLE;
    VkDeviceSize allocSize = 0;
    int exportedFd = -1;  // transferred to GL in initHost()
    GLuint glMem = 0;
    GLuint glTex = 0;
    GLuint glFbo = 0;
};

struct SharedSem {
    VkSemaphore vkSem = VK_NULL_HANDLE;
    int exportedFd = -1;  // transferred to GL in initHost()
    GLuint glSem = 0;
};

class GlVkTransport final : public Transport {
public:
    ~GlVkTransport() override {
        closePendingFds();
    }

    TransportKind kind() const override { return TransportKind::GL_VULKAN_ZERO_COPY; }
    uint32_t slotCount() const override { return slots_; }

    bool initVulkan(const TempleLsfg::LocalContext& lc, uint32_t width, uint32_t height, uint32_t slots) override {
        lc_ = lc;
        width_ = width;
        height_ = height;
        slots_ = slots;

        getMemFd_ = reinterpret_cast<PFN_vkGetMemoryFdKHR>(vkGetDeviceProcAddr(lc_.device, "vkGetMemoryFdKHR"));
        getSemFd_ = reinterpret_cast<PFN_vkGetSemaphoreFdKHR>(vkGetDeviceProcAddr(lc_.device, "vkGetSemaphoreFdKHR"));
        if (!getMemFd_ || !getSemFd_) return false;

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
            .commandBufferCount = 3,
        };
        if (vkAllocateCommandBuffers(lc_.device, &cbai, cbs_) != VK_SUCCESS) return false;

        const VkFenceCreateInfo fci{.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        if (vkCreateFence(lc_.device, &fci, nullptr, &fence_) != VK_SUCCESS) return false;

        inImgs_.resize(kInputSlots);
        inSems_.resize(kInputSlots);
        for (uint32_t i = 0; i < kInputSlots; ++i) {
            if (!createVkExportedImage(inImgs_[i]) || !createVkExportedSemaphore(inSems_[i])) {
                destroyVulkan();
                return false;
            }
        }
        ringImgs_.resize(slots_);
        for (uint32_t i = 0; i < slots_; ++i) {
            if (!createVkExportedImage(ringImgs_[i])) {
                destroyVulkan();
                return false;
            }
        }

        // Transition all shared images from UNDEFINED to GENERAL once.
        if (!transitionAllToGeneral()) {
            destroyVulkan();
            return false;
        }
        return true;
    }

    bool initHost(uint32_t width, uint32_t height) override {
        if (!gl_.init()) {
            closePendingFds();
            return false;
        }
        gl_.drainErrors();
        GLint prevTex = 0, prevFbo = 0;
        gl_.getIntegerv(kGL_TEXTURE_BINDING_2D, &prevTex);
        gl_.getIntegerv(kGL_FRAMEBUFFER_BINDING, &prevFbo);

        bool ok = true;
        for (uint32_t i = 0; ok && i < kInputSlots; ++i) {
            ok = importGlImage(inImgs_[i], width, height, false) && importGlSemaphore(inSems_[i]);
        }
        for (uint32_t i = 0; ok && i < slots_; ++i) {
            ok = importGlImage(ringImgs_[i], width, height, true);
        }

        gl_.bindTexture(kGL_TEXTURE_2D, static_cast<GLuint>(prevTex));
        gl_.bindFramebuffer(kGL_FRAMEBUFFER, static_cast<GLuint>(prevFbo));
        if (!ok) {
            destroyHost();
            return false;
        }
        hostReady_ = true;
        return true;
    }

    void flushHostBeforeDestroy() override {
        if (hostReady_ && gl_.finish) gl_.finish();
    }

    void destroyVulkan() override {
        closePendingFds();
        if (lc_.device == VK_NULL_HANDLE) return;
        vkDeviceWaitIdle(lc_.device);
        for (auto& img : inImgs_) destroyVkImage(img);
        for (auto& img : ringImgs_) destroyVkImage(img);
        for (auto& sem : inSems_) destroyVkSemaphore(sem);
        if (fence_) { vkDestroyFence(lc_.device, fence_, nullptr); fence_ = VK_NULL_HANDLE; }
        if (pool_) { vkDestroyCommandPool(lc_.device, pool_, nullptr); pool_ = VK_NULL_HANDLE; }
        lc_.device = VK_NULL_HANDLE;
    }

    void destroyHost() override {
        closePendingFds();
        if (!gl_.deleteTextures) return;
        for (auto& img : inImgs_) destroyGlImage(img);
        for (auto& img : ringImgs_) destroyGlImage(img);
        for (auto& sem : inSems_) destroyGlSemaphore(sem);
        hostReady_ = false;
    }

    bool hostSubmitGl(uint32_t inSlot, uint32_t srcGlTexId) override {
        if (!hostReady_ || inSlot >= inImgs_.size() || srcGlTexId == 0) return false;
        gl_.copyImageSubData(
            srcGlTexId, kGL_TEXTURE_2D, 0, 0, 0, 0,
            inImgs_[inSlot].glTex, kGL_TEXTURE_2D, 0, 0, 0, 0,
            static_cast<GLsizei>(width_), static_cast<GLsizei>(height_), 1);
        const GLenum layout = kGL_LAYOUT_GENERAL_EXT;
        gl_.signalSem(inSems_[inSlot].glSem, 0, nullptr, 1, &inImgs_[inSlot].glTex, &layout);
        gl_.flush();
        return true;
    }

    bool vkUploadInput(uint32_t inSlot, uint64_t /*fenceVal*/, VkImage dst0, VkImage dst1,
                       VkBuffer lumaBuf256) override {
        if (inSlot >= inImgs_.size()) return false;
        VkCommandBuffer cb = cbs_[0];
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

        const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        const VkSubmitInfo si{
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .waitSemaphoreCount = 1,
            .pWaitSemaphores = &inSems_[inSlot].vkSem,
            .pWaitDstStageMask = &waitStage,
            .commandBufferCount = 1,
            .pCommandBuffers = &cb,
        };
        return vkQueueSubmit(lc_.queue, 1, &si, VK_NULL_HANDLE) == VK_SUCCESS;
    }

    void vkDiscardInput(uint32_t inSlot, uint64_t /*fenceVal*/) override {
        if (inSlot >= inSems_.size() || lc_.device == VK_NULL_HANDLE) return;
        const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        const VkSubmitInfo si{
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .waitSemaphoreCount = 1,
            .pWaitSemaphores = &inSems_[inSlot].vkSem,
            .pWaitDstStageMask = &waitStage,
        };
        vkResetFences(lc_.device, 1, &fence_);
        if (vkQueueSubmit(lc_.queue, 1, &si, fence_) == VK_SUCCESS) {
            vkWaitForFences(lc_.device, 1, &fence_, VK_TRUE, 2'000'000'000ull);
        }
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

        vkResetFences(lc_.device, 1, &fence_);
        const VkSubmitInfo si{
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .commandBufferCount = 1,
            .pCommandBuffers = &cb,
        };
        if (vkQueueSubmit(lc_.queue, 1, &si, fence_) != VK_SUCCESS) return false;
        return vkWaitForFences(lc_.device, 1, &fence_, VK_TRUE, 2'000'000'000ull) == VK_SUCCESS;
    }

    void hostWaitSlot(uint32_t slot, uint64_t /*fenceVal*/) override {
        if (!hostReady_ || slot >= ringImgs_.size()) return;
        if (gl_.memoryBarrier) {
            gl_.memoryBarrier(kGL_TEXTURE_FETCH_BARRIER_BIT | kGL_FRAMEBUFFER_BARRIER_BIT);
        }
    }

    int64_t hostSlotHandle(uint32_t slot) const override {
        if (!hostReady_ || slot >= ringImgs_.size()) return 0;
        return static_cast<int64_t>(ringImgs_[slot].glFbo);
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

    bool createVkExportedImage(SharedImg& out) {
        const VkExternalMemoryImageCreateInfo emi{
            .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT,
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
        VkPhysicalDeviceMemoryProperties mp{};
        vkGetPhysicalDeviceMemoryProperties(lc_.physicalDevice, &mp);
        const uint32_t mt = findVkMemoryType(mp, req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (mt == UINT32_MAX) return false;

        const VkMemoryDedicatedAllocateInfo ded{
            .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
            .image = out.vkImage,
        };
        const VkExportMemoryAllocateInfo exp{
            .sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,
            .pNext = &ded,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT,
        };
        const VkMemoryAllocateInfo mai{
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .pNext = &exp,
            .allocationSize = req.size,
            .memoryTypeIndex = mt,
        };
        if (vkAllocateMemory(lc_.device, &mai, nullptr, &out.vkMem) != VK_SUCCESS) return false;
        if (vkBindImageMemory(lc_.device, out.vkImage, out.vkMem, 0) != VK_SUCCESS) return false;
        out.allocSize = req.size;

        const VkMemoryGetFdInfoKHR gfi{
            .sType = VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR,
            .memory = out.vkMem,
            .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT,
        };
        return getMemFd_(lc_.device, &gfi, &out.exportedFd) == VK_SUCCESS && out.exportedFd >= 0;
    }

    bool createVkExportedSemaphore(SharedSem& out) {
        const VkExportSemaphoreCreateInfo esci{
            .sType = VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO,
            .handleTypes = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT,
        };
        const VkSemaphoreCreateInfo sci{
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
            .pNext = &esci,
        };
        if (vkCreateSemaphore(lc_.device, &sci, nullptr, &out.vkSem) != VK_SUCCESS) return false;
        const VkSemaphoreGetFdInfoKHR sfi{
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR,
            .semaphore = out.vkSem,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT,
        };
        return getSemFd_(lc_.device, &sfi, &out.exportedFd) == VK_SUCCESS && out.exportedFd >= 0;
    }

    bool transitionAllToGeneral() {
        VkCommandBuffer cb = cbs_[2];
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
        vkResetFences(lc_.device, 1, &fence_);
        const VkSubmitInfo si{.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cb};
        if (vkQueueSubmit(lc_.queue, 1, &si, fence_) != VK_SUCCESS) return false;
        return vkWaitForFences(lc_.device, 1, &fence_, VK_TRUE, 2'000'000'000ull) == VK_SUCCESS;
    }

    bool importGlImage(SharedImg& img, uint32_t width, uint32_t height, bool withFbo) {
        if (img.exportedFd < 0) return false;
        gl_.createMemObjs(1, &img.glMem);
        const GLint ded = static_cast<GLint>(kGL_TRUE);
        gl_.memObjParamiv(img.glMem, kGL_DEDICATED_MEMORY_OBJECT_EXT, &ded);
        const int fd = img.exportedFd;
        img.exportedFd = -1;  // GL consumes fd
        gl_.importMemFd(img.glMem, img.allocSize, kGL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
        gl_.genTextures(1, &img.glTex);
        gl_.bindTexture(kGL_TEXTURE_2D, img.glTex);
        gl_.texParameteri(kGL_TEXTURE_2D, kGL_TEXTURE_TILING_EXT, static_cast<GLint>(kGL_OPTIMAL_TILING_EXT));
        gl_.texParameteri(kGL_TEXTURE_2D, kGL_TEXTURE_MIN_FILTER, static_cast<GLint>(kGL_LINEAR));
        gl_.texParameteri(kGL_TEXTURE_2D, kGL_TEXTURE_MAG_FILTER, static_cast<GLint>(kGL_LINEAR));
        gl_.texParameteri(kGL_TEXTURE_2D, kGL_TEXTURE_WRAP_S, static_cast<GLint>(kGL_CLAMP_TO_EDGE));
        gl_.texParameteri(kGL_TEXTURE_2D, kGL_TEXTURE_WRAP_T, static_cast<GLint>(kGL_CLAMP_TO_EDGE));
        gl_.texStorageMem2D(kGL_TEXTURE_2D, 1, kGL_RGBA8, static_cast<GLsizei>(width), static_cast<GLsizei>(height), img.glMem, 0);
        if (gl_.getError() != kGL_NO_ERROR) return false;

        if (withFbo) {
            gl_.genFramebuffers(1, &img.glFbo);
            gl_.bindFramebuffer(kGL_FRAMEBUFFER, img.glFbo);
            gl_.framebufferTexture2D(kGL_FRAMEBUFFER, kGL_COLOR_ATTACHMENT0, kGL_TEXTURE_2D, img.glTex, 0);
            if (gl_.checkFramebufferStatus(kGL_FRAMEBUFFER) != kGL_FRAMEBUFFER_COMPLETE) return false;
        }
        return gl_.getError() == kGL_NO_ERROR;
    }

    bool importGlSemaphore(SharedSem& sem) {
        if (sem.exportedFd < 0) return false;
        gl_.genSems(1, &sem.glSem);
        const int fd = sem.exportedFd;
        sem.exportedFd = -1;
        gl_.importSemFd(sem.glSem, kGL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
        return gl_.getError() == kGL_NO_ERROR;
    }

    void closePendingFds() {
        for (auto& i : inImgs_) if (i.exportedFd >= 0) { ::close(i.exportedFd); i.exportedFd = -1; }
        for (auto& r : ringImgs_) if (r.exportedFd >= 0) { ::close(r.exportedFd); r.exportedFd = -1; }
        for (auto& s : inSems_) if (s.exportedFd >= 0) { ::close(s.exportedFd); s.exportedFd = -1; }
    }

    void destroyVkImage(SharedImg& img) {
        if (img.vkImage) { vkDestroyImage(lc_.device, img.vkImage, nullptr); img.vkImage = VK_NULL_HANDLE; }
        if (img.vkMem) { vkFreeMemory(lc_.device, img.vkMem, nullptr); img.vkMem = VK_NULL_HANDLE; }
    }
    void destroyVkSemaphore(SharedSem& sem) {
        if (sem.vkSem) { vkDestroySemaphore(lc_.device, sem.vkSem, nullptr); sem.vkSem = VK_NULL_HANDLE; }
    }
    void destroyGlImage(SharedImg& img) {
        if (img.glFbo) { gl_.deleteFramebuffers(1, &img.glFbo); img.glFbo = 0; }
        if (img.glTex) { gl_.deleteTextures(1, &img.glTex); img.glTex = 0; }
        if (img.glMem) { gl_.deleteMemObjs(1, &img.glMem); img.glMem = 0; }
    }
    void destroyGlSemaphore(SharedSem& sem) {
        if (sem.glSem) { gl_.delSems(1, &sem.glSem); sem.glSem = 0; }
    }

    TempleLsfg::LocalContext lc_{};
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    uint32_t slots_ = 0;
    bool hostReady_ = false;
    GlFns gl_{};
    PFN_vkGetMemoryFdKHR getMemFd_ = nullptr;
    PFN_vkGetSemaphoreFdKHR getSemFd_ = nullptr;
    VkCommandPool pool_ = VK_NULL_HANDLE;
    VkCommandBuffer cbs_[3]{};
    VkFence fence_ = VK_NULL_HANDLE;
    std::vector<SharedImg> inImgs_;
    std::vector<SharedImg> ringImgs_;
    std::vector<SharedSem> inSems_;
};

}  // namespace

std::unique_ptr<Transport> createGlVkTransport() {
    return std::make_unique<GlVkTransport>();
}

}  // namespace njfg
