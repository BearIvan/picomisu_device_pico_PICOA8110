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

// Declarations of android::pico::PxrMediaAnalyticsItem (factory PICO OS 5.13.7
// /system/lib*/libpxrmediametrics.so, a PICO clone of MediaAnalyticsItem that reports to
// /system/bin/pxrmediametrics), as called by the factory libstagefright (MediaCodec,
// RemoteMediaExtractor).
//
// The library has no published source; only the exported members used by Source are
// declared. Items are created by create() (operator new) and released with a non-virtual
// destructor followed by operator delete, as the factory callers do; their layout is private
// to the library. PerfData is the factory layout (constructors 0x8c70..0x8cb4,
// setPerformanceData 0xc3a0 copies it into the item).

#include <stdint.h>
#include <sys/types.h>

#include <string>

namespace android {
namespace pico {

class PxrMediaAnalyticsItem {
public:
    struct PerfData {
        PerfData();
        PerfData(const PerfData& other);
        PerfData(bool lowLatency, short* fps, int fpsCount, short* latencyMs, int latencyMsCount);

        bool mLowLatency;
        short* mFps;
        int mFpsCount;
        short* mLatencyMs;
        int mLatencyMsCount;
    };

    static PxrMediaAnalyticsItem* create(std::string key);
    ~PxrMediaAnalyticsItem();

    bool deathNotifyToServer();
    int64_t generateSessionID();
    void setUid(uid_t uid);
    void setInt32(const char* attr, int32_t value);
    void setCString(const char* attr, const char* value);
    bool getCString(const char* attr, char** value);
    int32_t count() const;
    bool selfrecord();
    void setPerformanceData(const PerfData& perfData);

private:
    PxrMediaAnalyticsItem() = delete;
};

} // namespace pico
} // namespace android
