DEVICE_PATH := device/pico/PICOA8110
PICO_STOCK_DIR := $(DEVICE_PATH)/stock

# CPU variants of the factory PICO OS 5.13.7 system image: it was built with the CAF QSSI
# board config (device/qcom/qssi: armv8-a/generic, armv7-a-neon/cortex-a9), as the soong
# variant names embedded in the factory system libraries show (android_arm64_armv8-a_core_static,
# android_arm_armv7-a-neon_cortex-a9_core_static). The kryo300/cortex-a75 values in the factory
# vendor default.prop belong to the separate kona vendor build.
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_VARIANT := generic
TARGET_CPU_ABI := arm64-v8a
TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv7-a-neon
TARGET_2ND_CPU_VARIANT := cortex-a9
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi
TARGET_USES_64_BIT_BINDER := true
USE_XML_AUDIO_POLICY_CONF := 1

# Audio policy manager of the factory PICO OS 5.13.7: /system/lib64/libaudiopolicymanager.so is
# the CAF AudioPolicyManagerCustom (vendor/qcom/opensource/audio/policy_hal, built with
# USE_CUSTOM_AUDIO_POLICY, as kona.mk sets) on top of libaudiopolicymanagerdefault.so. The
# policy_hal feature flags are the ones the factory binary proves: APMConfigHelper::
# APMConfigHelper() stores the compile-time bools 0x0100000100010101 at 0x2545c (HDMI_SPK,
# EXTN_FORMATS, PROXY_DEVICE on, COMPRESS_VOIP off, FM_POWER_OPT on, VOICE_CONCURRENCY and
# RECORD_PLAY_CONCURRENCY off, USE_XML_AUDIO_POLICY_CONF on) and the library links
# vendor.qti.hardware.audiohalext@1.0 / -utils (AHAL_EXT; interface in audiohalext/).
USE_CUSTOM_AUDIO_POLICY := 1
AUDIO_FEATURE_ENABLED_HDMI_SPK := true
AUDIO_FEATURE_ENABLED_EXTN_FORMATS := true
AUDIO_FEATURE_ENABLED_PROXY_DEVICE := true
AUDIO_FEATURE_ENABLED_FM_POWER_OPT := true
AUDIO_FEATURE_ENABLED_AHAL_EXT := true
# The factory audioserver NEEDs libvraudio.so and instantiates VRAudioServiceNative
# ("vendor.audio.vrservice"): frameworks/av/media/audioserver builds it with this flag, which
# the CAF qssi.mk sets (the only system user of the flag; libvraudio is a factory-libs prebuilt).
AUDIO_FEATURE_ENABLED_3D_AUDIO := true

TARGET_BOARD_PLATFORM := kona
TARGET_BOOTLOADER_BOARD_NAME := kona
TARGET_NO_BOOTLOADER := true

# First bring-up keeps the authenticated factory boot/kernel/DTB/DTBO.
TARGET_NO_KERNEL := true
BOARD_BOOT_HEADER_VERSION := 2
BOARD_KERNEL_PAGESIZE := 4096
BOARD_USES_RECOVERY_AS_BOOT := false

# Android 10 always packs a system-as-root image. False preserves the required
# first-stage ramdisk and matches the factory configuration.
BOARD_BUILD_SYSTEM_ROOT_IMAGE := false
BOARD_USES_METADATA_PARTITION := true

# The factory fstab encrypts userdata with hardware-wrapped ICE keys
# (fileencryption=ice,wrappedkey). vold is built as on the factory system
# (CodeLinaro LA.UM.8.12.c3-64900): CONFIG_HW_DISK_ENCRYPTION and
# CONFIG_HW_DISK_ENCRYPT_PERF, linked with the factory libcryptfs_hw
# (device/pico/PICOA8110/cryptfs_hw).
TARGET_HW_DISK_ENCRYPTION := true
TARGET_HW_DISK_ENCRYPTION_PERF := true
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 5704732672

TARGET_COPY_OUT_VENDOR := vendor
TARGET_COPY_OUT_PRODUCT := product
TARGET_COPY_OUT_ODM := odm
BOARD_PREBUILT_VENDORIMAGE := $(PICO_STOCK_DIR)/vendor.img
BOARD_PREBUILT_PRODUCTIMAGE := $(PICO_STOCK_DIR)/product.img
BOARD_PREBUILT_ODMIMAGE := $(PICO_STOCK_DIR)/odm.img

BOARD_SUPER_PARTITION_SIZE := 8589934592
BOARD_SUPER_PARTITION_GROUPS := qti_dynamic_partitions
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 8585740288
BOARD_QTI_DYNAMIC_PARTITIONS_PARTITION_LIST := system vendor product odm
BOARD_SUPER_PARTITION_ALIGNMENT := 524288
BOARD_BUILD_SUPER_IMAGE_BY_DEFAULT := false

BOARD_VNDK_VERSION := current
BOARD_PROPERTY_OVERRIDES_SPLIT_ENABLED := true

# Release AVB/APK signing and VR runtime integration are not configured here.
# Do not infer a flashing workflow from the two slots in LP metadata.

# Platform SELinux policy. The factory vendor/product policies are compiled by
# init against this plat policy and reference its public types through the
# 29.0 mapping. As in QSSI, the QTI system policy comes from CodeLinaro
# device/qcom/sepolicy (LA.UM.8.12.c3-64900-sm8250.0, generic + qva);
# Source-only additions (picofacialdatadaemon) are in sepolicy-source.
# PICO/OEM policy reconstructed from the factory 5.13.7 plat_sepolicy.cil is
# in the device tree (tools/reconstruct-factory-sepolicy.py,
# tools/check-sepolicy.py).
BOARD_PLAT_PUBLIC_SEPOLICY_DIR :=     device/qcom/sepolicy/generic/public     device/qcom/sepolicy/qva/public     $(DEVICE_PATH)/sepolicy/public
BOARD_PLAT_PRIVATE_SEPOLICY_DIR :=     device/qcom/sepolicy/generic/private     device/qcom/sepolicy/qva/private     $(DEVICE_PATH)/sepolicy/private     $(DEVICE_PATH)/sepolicy-source/private

# CAF (LA.UM.8.12) build: allow the duplicate rules and phony targets of the QTI makefiles
# (as device/qcom/qssi does) and keep the factory root layout: no /firmware, /dsp, /bt_firmware,
# /persist links from vendor/qcom/build/tasks/generate_extra_images.mk. Ignored by the r47 tree.
BUILD_BROKEN_DUP_RULES := true
BUILD_BROKEN_PHONY_TARGETS := true
TARGET_MOUNT_POINTS_SYMLINKS := false
# Marks the PICO system-only build for the CAF task makefiles patched on the picomisu branches.
PICO_SYSTEM_BUILD := true

# CFI on, as on the factory PICO OS 5.13.7 system image: 83 of its system ELFs define __cfi_check
# (audioserver, libaudioflinger, keystore, mediaextractor, libmtp, libstagefright_omx, bluetooth, QMI...;
# libstagefright itself is built without CFI). It was off for the porting test builds (user decision
# 2026-10-01) and is re-enabled from Source 2.17.

# The factory /system/bin/mediadrmserver is a 64-bit (CFI) executable.
TARGET_ENABLE_MEDIADRM_64 := true
