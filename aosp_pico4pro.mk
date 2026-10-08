# Initial AOSP 10 hardware configuration. VR integration remains in progress.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)
$(call inherit-product, device/pico/PICOA8110/device.mk)

PRODUCT_NAME := aosp_pico4pro
PRODUCT_DEVICE := PICOA8110
PRODUCT_BRAND := Pico
PRODUCT_MANUFACTURER := Pico
PRODUCT_MODEL := A8110
PRODUCT_SYSTEM_NAME := Phoenix_ovs

# Factory locale set (PICO OS 5.13.7). The factory framework-res and the factory builds of AOSP
# apps (Shell, CertInstaller) keep exactly these 27 locales (aapt2 -c PRODUCT_LOCALES), without
# the en_XA/ar_XB pseudo-locales; zh_CN comes first (ro.product.locale=zh-CN). Replaces the
# full_base.mk (locales_full.mk) list. Also sets the system language list of the device.
PRODUCT_LOCALES := zh_CN cs_CZ da_DK de_DE el_GR en_GB en_US es_ES es_US fi_FI fr_FR it_IT \
    ja_JP ko_KR ms_MY nb_NO nl_NL pl_PL pt_BR pt_PT ro_RO ru_RU sv_SE th_TH tr_TR zh_HK zh_TW
