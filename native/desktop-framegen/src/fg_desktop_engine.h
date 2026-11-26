// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "fg_desktop_types.h"
#include <cstdint>
#include <memory>

namespace njfg {

class DesktopFrameGenEngine {
public:
    DesktopFrameGenEngine();
    ~DesktopFrameGenEngine();

    DesktopFrameGenEngine(const DesktopFrameGenEngine&) = delete;
    DesktopFrameGenEngine& operator=(const DesktopFrameGenEngine&) = delete;

    // Must be called on host graphics thread (GL/D3D12 current).
    bool start(const EngineConfig& cfg);
    void stop();

    // Hot updates (safe from any thread).
    void setPaused(bool paused);
    void flush();
    void setSourcePeriodNs(int64_t periodNs);
    void setVsyncNs(int64_t vsyncNs);
    void setAntiArtifacts(bool enabled);

    // Host graphics thread calls:
    bool submitGlFrame(uint32_t srcGlTexId, int64_t timestampNs);
    bool submitD3d12Frame(uintptr_t srcResourcePtr, int64_t timestampNs);
    bool submitRgbaFrame(const uint8_t* rgba, size_t bytes, int64_t timestampNs);

    // Host graphics thread: returns slot index >= 0 to display now (with GPU sync already issued), or -1 if none yet.
    int32_t acquirePresentSlot(int64_t nowNs);
    int64_t slotHandle(uint32_t slot) const;
    bool readSlotRgba(uint32_t slot, uint8_t* dst, size_t bytes) const;

    uint32_t slotCount() const;
    TransportKind transportKind() const;
    StatsSnapshot stats() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace njfg
