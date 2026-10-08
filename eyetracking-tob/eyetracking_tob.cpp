// Picomisu Source: business ("ToB") eye tracking mode for pxreyetrackingservice only.
//
// The factory libpxreyetrackingservice.so decides the mode in PXR::EyeUtil::isBusinessDev()
// (0x54ed0): true if the exported flag PXR::EyeUtil::isDebugBusiness is set, otherwise
// property_get_int32("ro.pxr.externalfunc") > 0. In business mode StandardAlgorithm passes
// "et_mode:tob;ipd_position:...;cam_path:..." to the eye tracking algorithm and fills the per-eye
// results. ro.pxr.externalfunc would switch the whole headset to the business edition, so this
// library is preloaded into the service (pxreyetrackingservice_tob.rc) and defines the flag as
// true; the dynamic linker binds the service library's GOT entry to this definition.

namespace PXR {

class EyeUtil {
public:
    static bool isDebugBusiness;
};

__attribute__((visibility("default"))) bool EyeUtil::isDebugBusiness = true;

}  // namespace PXR
