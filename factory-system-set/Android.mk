# Installed-set parity with the factory PICO OS 5.13.7 system image (user build of the same
# Android 10 base): AOSP base product modules that the factory image does not ship.
#
# This module installs nothing (LOCAL_UNINSTALLABLE_MODULE); listed in PRODUCT_PACKAGES, its
# LOCAL_OVERRIDES_MODULES removes the named modules (and what only they require) from the
# product module set, the build system's way to drop inherited PRODUCT_PACKAGES entries.
#   healthd          base_product.mk; the factory has no /system/bin/healthd
#   mediametrics     base_system.mk; the factory runs PICO pxrmediametrics instead and has no
#                    /system/bin/mediametrics nor mediametrics.rc
#   hid              base_system.mk; no /system/bin/hid, hid.jar, libhidcommand_jni on the factory
#   schedtest        base_system.mk; not on the factory
#   recovery-refresh not on the factory
#   PrintSpooler     base_system.mk; the factory has no PrintSpooler package (the image assembly
#                    drops the APK) nor libprintspooler_jni
#   LiveWallpapersPicker  full_base.mk; the factory has no live wallpaper picker (the image
#                    assembly drops the APK) nor the android.software.live_wallpaper.xml
#                    feature file that only the picker requires
# The userdebug-only PRODUCT_PACKAGES_DEBUG tools (strace, gdbserver, sqlite3, ...) are kept:
# the factory is a user build, Source is userdebug by decision.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := pico_factory_system_set
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := pico_factory_system_set.txt
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_OVERRIDES_MODULES := \
    healthd \
    mediametrics \
    hid \
    schedtest \
    recovery-refresh \
    PrintSpooler \
    LiveWallpapersPicker
include $(BUILD_PREBUILT)
