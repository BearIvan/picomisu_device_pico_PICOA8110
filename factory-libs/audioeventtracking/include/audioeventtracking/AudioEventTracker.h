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

// Declarations of android::pico::audioeventtracking::AudioEventTracker (factory PICO OS 5.13.7
// /system/lib64/libaudioeventtracking.so), which the factory libaudioflinger,
// libaudiopolicyservice and libaudiopolicymanager (AudioPolicyManagerCustom) link and call:
// playback/capture starts and ends, record silencing, device changes and closed threads.
//
// The library has no published source; only the exported members used by Source are declared.
// They are non-virtual and called directly on getInstance(), as in the factory callers.
//
// The factory image has no 32-bit libaudioeventtracking.so (and no 32-bit audio server
// libraries). 32-bit builds of these libraries get inline members that do nothing.

#include <stdint.h>

#include <system/audio.h>

namespace android {
namespace pico {
namespace audioeventtracking {

// Argument of AudioEventTracker::onPlaybackStarted()/onCaptureStarted(), factory layout
// (read by TrackManager::onStart(), 0x140 bytes).
struct start_event_t {
    int32_t portId;                     // 0x000
    int32_t io;                         // 0x004 audio_io_handle_t of the stream
    uint32_t uid;                       // 0x008
    uint32_t device;                    // 0x00c audio_devices_t of the stream
    audio_attributes_t attributes;      // 0x010 content_type, usage, source, flags, tags
    int32_t stream;                     // 0x120 audio_stream_type_t (playback)
    audio_config_base_t config;         // 0x124 sample_rate, channel_mask, format
    uint32_t flags;                     // 0x130 audio_output_flags_t / audio_input_flags_t
    // playback (AudioPolicyService::doStartOutput), 0 for a capture:
    uint32_t mixerChannelMask;          // 0x134 channel mask of the spatializer mixer
    uint32_t spatializeFlags;           // 0x138 AUDIO_FLAG_ALWAYS_SPATIALIZE or
                                        //       AUDIO_FLAG_NEVER_SPATIALIZE of the client
    int32_t spatialized;                // 0x13c the client is spatialized
};
static_assert(sizeof(start_event_t) == 0x140, "factory start_event_t layout");

class AudioEventTracker {
public:
#ifdef __LP64__
    static AudioEventTracker* getInstance();

    void onPlaybackStarted(const start_event_t& event);
    void onPlaybackEnded(int portId);
    void onCaptureStarted(const start_event_t& event);
    void onCaptureEnded(int portId);
    void onCaptureSilenced(int portId, bool silenced);
    void onDeviceChanged(int io, uint32_t oldDevice, uint32_t newDevice);
    void onAudioFlingerThreadClosed(int io);
#else
    static AudioEventTracker* getInstance() {
        static AudioEventTracker sNone;
        return &sNone;
    }

    void onPlaybackStarted(const start_event_t&) {}
    void onPlaybackEnded(int) {}
    void onCaptureStarted(const start_event_t&) {}
    void onCaptureEnded(int) {}
    void onCaptureSilenced(int, bool) {}
    void onDeviceChanged(int, uint32_t, uint32_t) {}
    void onAudioFlingerThreadClosed(int) {}
#endif

private:
#ifdef __LP64__
    AudioEventTracker() = delete;
    ~AudioEventTracker() = delete;
#endif
};

} // namespace audioeventtracking
} // namespace pico
} // namespace android
