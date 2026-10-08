/*
 * Copyright (C) 2026 Picomisu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

// Declarations of the members of mtp::SysMtpClient (factory PICO OS 5.13.7
// /system/lib*/libsysperftracker.so) that the factory libhwui and libsurfaceflinger call.
//
// The library has no published source. Only the exported functions used by Source are
// declared, with the parameter types of their mangled names (64-bit "l" and 32-bit "x"
// are both int64_t); the class layout is private to the library (a RefBase, created and
// owned by getInstance()) and never touched by the callers. Every member is non-virtual
// and called directly, as in the factory callers.

#include <stdint.h>

#include <string>
#include <vector>

namespace android {
struct SurfaceClientItem;
namespace uirenderer {
class FrameInfo;
} // namespace uirenderer
} // namespace android

namespace mtp {

class SysMtpClient {
public:
    // The process-wide client (created on first use, never released).
    static SysMtpClient* getInstance();

    // libhwui CanvasContext::draw: one rendered frame of the window |name|.
    void addRenderFrame(android::uirenderer::FrameInfo& frameInfo, const std::string& name);

    // SurfaceFlinger::onVsyncReceived: every hardware vsync.
    void onVsync(int64_t timestamp);
    // SurfaceFlinger::handleMessageRefresh: a composed frame of a display and the producer
    // frames it showed.
    void addDisplayFrame(std::vector<android::SurfaceClientItem> frames, int layerStack,
                         int64_t lastComposeTime, int64_t composeTime, int64_t queueStartTime,
                         int64_t queueEndTime);

    // SurfaceFlinger::onTransact system monitor codes 2002..2016.
    void sendTaskToWritePb();
    void sendTaskToWritePtpPb();
    void setDeviceProp(std::string a, std::string b, std::string c);
    void shutDown();
    void notifyDisplayRefresh(int value);
    void notifyAutoDumpInfo(std::string name, std::vector<int> values);
    void setDailyDumpPerfettoCount(int count);
    void notifyCrashReportDumpInfo(std::string name, std::vector<int> values);
    void notifyBacklight(int a, int b, int c);
    void notifyLowPowerLevel(int level);
    void updateTerribleJankScope(int scope);
    void updateCameraRefresh(int value);
    void notifyVirtualDisplaySurfaceChanged(int value, std::string name);
    void notifyLaunchPackageInfo(std::string a, std::string b, int value, int64_t time);
    void notifySchedInfoDumpInfo(std::vector<int> values);
    void notifyLayerDumpInfo(std::string name, std::vector<int> values);

private:
    SysMtpClient() = delete;
    ~SysMtpClient() = delete;
};

} // namespace mtp
