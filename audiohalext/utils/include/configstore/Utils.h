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

// QTI audio HAL extension config retrieval (vendor.qti.hardware.audiohalext-utils),
// reconstructed for the PICO 4 Pro Source build from the factory PICO OS 5.13.7
// binaries: the template instances in system/lib64/libaudiopolicymanager.so
// (get<ApmConfigs, IAudioHalExt, &IAudioHalExt::getApmConfigs> at 0x2648c, its
// helper lambda at 0x26828, tryGetService<IAudioHalExt> at 0x26acc) and in
// system/lib64/libavenhancements.so (getAVConfigs/get<AVConfigs, ...>). It is the
// android.hardware.configstore-utils pattern with an "is remote" result, info level
// logging and IAudioHalExt::tryGetService(). Used by the CAF policy_hal
// APMConfigHelper (#include <configstore/Utils.h> with AHAL_EXT_ENABLED).

#ifndef VENDOR_QTI_HARDWARE_AUDIOHALEXT_UTILS_H
#define VENDOR_QTI_HARDWARE_AUDIOHALEXT_UTILS_H

#include <vendor/qti/hardware/audiohalext/1.0/IAudioHalExt.h>
#include <vendor/qti/hardware/audiohalext/1.0/types.h>
#include <hidl/Status.h>

#include <sstream>

namespace vendor {
namespace qti {
namespace hardware {

namespace details {
// Templated classes can use the below method
// to avoid creating dependencies on liblog.
bool wouldLogInfo();
void logAlwaysInfo(const std::string& message);
void logAlwaysError(const std::string& message);
}  // namespace details

namespace audiohalext {
// import types from audiohalext
using ::vendor::qti::hardware::audiohalext::V1_0::ApmConfigs;
using ::vendor::qti::hardware::audiohalext::V1_0::ApmValues;
using ::vendor::qti::hardware::audiohalext::V1_0::AVConfigs;
using ::vendor::qti::hardware::audiohalext::V1_0::AVValues;
using ::vendor::qti::hardware::audiohalext::V1_0::OptionalString;

// a function to retrieve and cache the service handle
// for a particular interface
template <typename I>
android::sp<I> tryGetService() {
    // static initializer used for synchronizations
    static android::sp<I> configs = I::tryGetService();
    return configs;
}

// arguments V: type for the value (i.e., XXXConfigs)
//           I: interface class name
//           func: member function pointer
// isRemote is set when the value comes from the service (else defValue is returned).
template<typename V, typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const V&)>)>
decltype(V::value) get(const decltype(V::value) &defValue, bool &isRemote) {
    using namespace vendor::qti::hardware::details;
    // static initializer used for synchronizations
    auto getHelper = []()->V {
        V ret;
        android::sp<I> configs = tryGetService<I>();

        if (!configs.get()) {
            // fallback to the default value
            ret.specified = false;
        } else {
            auto status = (*configs.*func)([&ret](V v) {
                ret = v;
            });
            if (!status.isOk()) {
                std::ostringstream oss;
                oss << "HIDL call failed for retrieving a config item from "
                       "configstore : "
                    << status.description().c_str();
                logAlwaysError(oss.str());
                ret.specified = false;
            }
        }

        return ret;
    };
    static V cachedValue = getHelper();

    isRemote = cachedValue.specified;

    if (wouldLogInfo()) {
        std::string iname = __PRETTY_FUNCTION__;
        // func name starts with "func = " in __PRETTY_FUNCTION__
        auto pos = iname.find("func = ");
        if (pos != std::string::npos) {
            iname = iname.substr(pos + sizeof("func = "));
            iname.pop_back();  // remove trailing ']'
        } else {
            iname += " (unknown)";
        }

        std::ostringstream oss;
        oss << iname << " retrieved: "
            << (cachedValue.specified ? "" : " (default)");
        logAlwaysInfo(oss.str());
    }

    return cachedValue.specified ? cachedValue.value : defValue;
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const ApmConfigs&)>)>
ApmValues getApmConfigs(const ApmValues &defValue, bool &isRemote) {
    return get<ApmConfigs, I, func>(defValue, isRemote);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const AVConfigs&)>)>
AVValues getAVConfigs(const AVValues &defValue, bool &isRemote) {
    return get<AVConfigs, I, func>(defValue, isRemote);
}

}  // namespace audiohalext
}  // namespace hardware
}  // namespace qti
}  // namespace vendor

#endif  // VENDOR_QTI_HARDWARE_AUDIOHALEXT_UTILS_H
