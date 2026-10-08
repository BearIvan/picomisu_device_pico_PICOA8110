#!/system/bin/sh
# Picomisu Source: log the first Android boot after a Source update to the cache partition.
#
# After the 2.14->2.16 and 2.16->2.17 updates (2026-10-02) the first boot showed the logo for a
# long time, then a black screen, until a hard reset; the second boot was fine. Such a boot never
# gets far enough to leave logs on /data, so this keeps logcat (all buffers, kernel included) on
# the cache partition and syncs it every 5 s, so it survives the hard reset. Runs from post-fs
# (source_updaterd.rc) once per update: the updater recovery leaves "ok <version>" in status.

D=/mnt/source-ota-cache/source-ota
status=$(cat $D/status 2>/dev/null)
case "$status" in
    "ok "*) ;;
    *) exit 0 ;;
esac
L=$D/firstboot-${status#ok }
[ -e $L.started ] && exit 0

cat /proc/uptime > $L.started
sync
# logcat exits when logd drops its readers during boot (2.18: after 23 s); start it again into
# the next file, each one dumps the buffers again from the start.
n=0
pid=0
i=0
while [ $i -lt 120 ]; do
    if [ $pid = 0 ] || ! kill -0 $pid 2>/dev/null; then
        n=$((n + 1))
        logcat -b all -v monotonic -f $L.$n.logcat -r 8192 -n 2 &
        pid=$!
    fi
    sleep 5
    sync
    i=$((i + 1))
done
kill $pid
sync
