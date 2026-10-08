/*
 * Copyright (C) 2017 The Android Open Source Project
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
// vendor.qti.hardware.audiohalext-utils, reconstructed from the factory PICO OS 5.13.7
// system/lib64/vendor.qti.hardware.audiohalext-utils.so (QTI source
// vendor/qcom/proprietary/commonsys/mm-audio/audio-hal-ext/ConfigStoreUtils.cpp,
// not published): log tag "ConfigStore", wouldLogInfo() is WOULD_LOG(INFO), the
// INFO and ERROR messages are logged from lines 39 and 43 as in the factory
// (android::base::LogMessage line arguments 0x27 and 0x2b). The template part is
// include/configstore/Utils.h.

#define LOG_TAG "ConfigStore"

#include <android-base/logging.h>
#include <configstore/Utils.h>

namespace vendor {
namespace qti {
namespace hardware {
namespace details {

bool wouldLogInfo() {
    return WOULD_LOG(INFO);
}

void logAlwaysInfo(const std::string& message) {
    LOG(INFO) << message;
}

void logAlwaysError(const std::string& message) {
    LOG(ERROR) << message;
}

}  // namespace details
}  // namespace hardware
}  // namespace qti
}  // namespace vendor
