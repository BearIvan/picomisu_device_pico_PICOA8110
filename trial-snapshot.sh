#!/system/bin/sh
# Trial-only diagnostics (source-trial-01c+): state snapshots on the unencrypted /cache,
# readable from the RAM-booted offline recovery when USB ADB does not come up.
# Also re-enables USB ADB the way the developer option does (Settings.Global.adb_enabled).
dir=/cache/trial
mkdir -p $dir
elapsed=0
for at in 60 180 420; do
    sleep $((at - elapsed))
    elapsed=$at
    out=$dir/snap-$at
    mkdir -p $out
    getprop > $out/getprop.txt 2>&1
    ps -A -o USER,PID,PPID,S,TIME,NAME > $out/ps.txt 2>&1
    dmesg > $out/dmesg.txt 2>&1
    cat /proc/uptime > $out/uptime.txt 2>&1
    service list > $out/services.txt 2>&1
    settings put global adb_enabled 1 > $out/adb-enable.txt 2>&1
    # XRShell writes panel touches to its uinput device with the target display as
    # EV_MSC/MSC_SERIAL; the factory libinputreader routes them, the Source one does not.
    # Until that is ported, switch XRShell to InputManager.injectInputEvent with the display id.
    am broadcast -a com.pvr.uinput.enable --ez enableUInput false > $out/uinput-disable.txt 2>&1
    for name in SurfaceFlinger window activity usb adb package; do
        case $name in
            window) args=displays ;;
            activity) args=activities ;;
            package) args=--checkin ;;
            *) args= ;;
        esac
        timeout 30 dumpsys -t 20 $name $args > $out/dumpsys-$name.txt 2>&1
    done
    ls -la /data/tombstones /data/anr > $out/tombstones.txt 2>&1
    sync
done
