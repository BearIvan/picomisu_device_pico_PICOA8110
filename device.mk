PRODUCT_SHIPPING_API_LEVEL := 29

# Factory ro.build.characteristics (vendor/product build.prop): selects the "device" product
# strings of framework-res ("Device is starting..."), as on PICO OS 5.13.7.
PRODUCT_CHARACTERISTICS := device,nosdcard

# Picomisu tiramisu logo; BootAnimation selects sharp5k for the INNOLUX5K panel
# and draws the same frame at the factory left/right eye centres.
PRODUCT_COPY_FILES += \
    device/pico/PICOA8110/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_SYSTEM)/media/bootanimation_sharp5k.zip

# Factory framework-res values that differ from the CAF defaults (overlay/README in config.xml).
DEVICE_PACKAGE_OVERLAYS += device/pico/PICOA8110/overlay

# Reuse factory LP metadata; the initial target is a standalone system image.
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true
PRODUCT_BUILD_SUPER_PARTITION := false

# The factory vendor/build.prop sets ro.apex.updatable=true, which overrides
# the system value (init loads /vendor/build.prop after /system/build.prop).
# apexd then activates only APEX files, so the Source APEXes must be packaged
# as .apex files like on the factory system, not flattened directories.
$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)

# Factory vendor/product/odm are selected in BoardConfig.mk.
# m systemimage produces the AOSP input, not the finished headset image.
# tools/assemble-vr-system.py applies the coherent factory layer declared in
# vr-layer.json to a separate staging tree and validates the final image.
# The full AOSP framework port and on-device VR validation remain separate work.

# Bluetooth: the Source image keeps the factory PICO/QTI stack (Bluetooth.apk,
# BluetoothExt.apk, libbluetooth_qti and its closure) listed in
# factory-bluetooth.json; tools/check-factory-bluetooth.py validates it against
# this build. The factory libraries need the HIDL base library on system.
PRODUCT_PACKAGES += android.hidl.base@1.0
# Hidden-API whitelist of the factory Bluetooth packages: on the CodeLinaro tree the factory
# file itself (device/qcom/common/qti_whitelist.xml, installed by device/qcom/qssi/common64.mk,
# byte-identical to the factory system/etc/sysconfig/qti_whitelist.xml); the AOSP tree has no
# QTI device tree and keeps the two Bluetooth entries in pico-bluetooth-hiddenapi.xml.
ifneq ($(wildcard device/qcom/common/qti_whitelist.xml),)
PRODUCT_COPY_FILES += \
    device/qcom/common/qti_whitelist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/qti_whitelist.xml
else
PRODUCT_COPY_FILES += \
    device/pico/PICOA8110/sysconfig/pico-bluetooth-hiddenapi.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/pico-bluetooth-hiddenapi.xml
endif

# Boot and system server class paths in the factory PICO OS 5.13.7 order
# (factory-bootclasspath.json). telephony-ext and qcom.fmradio are built
# from CodeLinaro LA.UM.8.12.c3-64900-sm8250.0; the other extra JARs are the
# factory binaries (factory-framework/Android.bp) checked against this build
# by tools/check-factory-component.py. The factory system server JARs
# sys-services and sysmonitor-services (Smartisan freezer, prefetch, memory,
# QuickBoot, monitoring) are carried before services.jar as on the factory;
# the services.jar Smartisan layer they link against is ported.
PRODUCT_BOOT_JARS_BEFORE_FRAMEWORK := \
    sysmonitor-framework \
    sys-framework \
    devicemiddlewareimpl \
    vrex-framework
PRODUCT_BOOT_JARS += \
    tcmiface \
    telephony-ext \
    qcom.fmradio \
    QPerformance \
    UxPerformance \
    WfdCommon
PRODUCT_SYSTEM_SERVER_JARS_BEFORE_SERVICES := \
    sys-services \
    sysmonitor-services \
    vrex-services
PRODUCT_PACKAGES += \
    sysmonitor-framework \
    sys-framework \
    devicemiddlewareimpl \
    vrex-framework \
    tcmiface \
    telephony-ext \
    qcom.fmradio \
    QPerformance \
    UxPerformance \
    WfdCommon \
    sys-services \
    sysmonitor-services \
    vrex-services
# Default lists of the Smartisan PeroptWhiteListParser (framework.jar), taken
# from the factory /system/etc by tools/install-device-tree.py: package flags,
# app info and the VR single-layer composition skip list.
PRODUCT_COPY_FILES += \
    device/pico/PICOA8110/factory-framework/etc/OptPackageWhiteList.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/OptPackageWhiteList.xml \
    device/pico/PICOA8110/factory-framework/etc/OptAppInfoWhiteList.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/OptAppInfoWhiteList.xml \
    device/pico/PICOA8110/factory-framework/etc/AppCompositionWhiteList.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/AppCompositionWhiteList.xml

# The factory image has the audio server libraries only in 64 bits (64-bit audioserver,
# no /system/lib/libaudioflinger.so, libaudiopolicy{service,manager,managerdefault}.so,
# libaaudioservice.so). Build the audio server modules for the primary ABI only, so that
# per-module builds do not install 32-bit copies the factory does not ship.
AUDIOSERVER_MULTILIB := 64

# Installed-set parity with the factory system image (factory-system-set/Android.mk).
PRODUCT_PACKAGES += pico_factory_system_set

# QTI mm-parser media extractor of the factory image (factory-libs/Android.bp).
PRODUCT_PACKAGES += libmmparserextractor

# Eye and face tracking data over UDP for VRCFaceTracking (fork of thoricelli/PicoFacialDataDaemon,
# external/picofacialdatadaemon); replaces the Magisk module. Not in the factory image.
PRODUCT_PACKAGES += picofacialdatadaemon

# Business ("ToB") eye tracking mode for pxreyetrackingservice only: per-eye gaze/pupil results
# without ro.pxr.externalfunc (which switches the whole headset to the business edition).
# eyetracking-tob/eyetracking_tob.cpp. Not in the factory image.
PRODUCT_PACKAGES += libpxreyetracking_tob pxreyetrackingservice_tob.rc

# In-headset Source Update: replaces the factory SystemUpdate2 (same package/components; the source
# release config excludes the factory APK), with source_updaterd and the Source OTA certificate.
PRODUCT_PACKAGES += SourceUpdate source-firstboot.sh
