#!/system/bin/sh
# Source updater, recovery side (started by init in the updater recovery at boot).
#
# tools/source-ota.py stages a block delta package in /cache/source-ota from Android and
# reboots to recovery. Without a staged package ("ready" marker) nothing happens and the
# recovery stays the root offline recovery (ADB over USB) used by source-trial-install.py.
#
# Package (/cache/source-ota): update.conf (KEY=VALUE), chunks.list
# ("name offset_blocks count_blocks base_sha256 target_sha256 file_sha256"), gzip chunk files
# with the target blocks, vbmeta_system.img, vbmeta.img, ready.
#
# Order: verify everything without writing (package files, super LP metadata, every chunk
# region is either the base or already the target, full base system hash on a fresh apply);
# set the BCB to boot-recovery so a power loss comes back here and resumes; write the chunks
# (regions already at the target are skipped); full target system hash; vbmeta_system,
# vbmeta; clear the BCB; reboot to Android. A rejected package (nothing written) is renamed
# and the device reboots to the unchanged Android. A failure after writing leaves the BCB
# set and stays in this recovery (ADB over USB) for recovery by the operator.

D=/tmp/source-ota-cache/source-ota
CACHE_DEV=/dev/block/bootdevice/by-name/cache
SUPER=/dev/block/bootdevice/by-name/super
MISC=/dev/block/bootdevice/by-name/misc
MAPPER=source-ota-system

mkdir -p /tmp/source-ota-cache
if ! mount -t ext4 -o nosuid,nodev,noatime $CACHE_DEV /tmp/source-ota-cache; then
    log -t source-updater "cache mount failed"
    exit 0
fi
if [ ! -f $D/ready ]; then
    umount /tmp/source-ota-cache
    exit 0
fi

exec >> $D/apply.log 2>&1
say() { echo "$(date -u +%H:%M:%S) $*"; log -t source-updater "$*"; }
status() { echo "$*" > $D/status; sync; say "status: $*"; }
sha() { sha256sum "$1" | cut -d' ' -f1; }
region_sha() { dd if=$SYS bs=4096 skip=$1 count=$2 2>/dev/null | sha256sum | cut -d' ' -f1; }
conf() { grep "^$1=" $D/update.conf | head -n 1 | cut -d= -f2-; }
reboot_android() { sync; exec > /dev/null 2>&1; umount /tmp/source-ota-cache; setprop sys.powerctl reboot; sleep 60; }

reject() {
    # Nothing was written: keep the package for inspection, boot the unchanged Android.
    status "rejected: $*"
    mv $D/ready $D/rejected
    dmctl delete $MAPPER > /dev/null 2>&1
    reboot_android
    exit 1
}

fail() {
    # Something was written: stay here (BCB boot-recovery stays set), ADB over USB.
    status "failed: $*"
    say "left in the updater recovery; the BCB still selects recovery"
    exit 1
}

say "=== Source updater: $(conf VERSION) from $(conf BASE_VERSION) ==="
status "verifying"

# Package files.
for name in vbmeta_system vbmeta; do
    [ "$(sha $D/$name.img)" = "$(conf ${name}_sha256)" ] || reject "$name.img hash"
done
while read name off count base target file; do
    [ -f $D/$name ] && [ "$(sha $D/$name)" = "$file" ] || reject "chunk file $name"
done < $D/chunks.list

# Super: the LP metadata and the system extent the package was made for.
[ "$(blockdev --getsize64 $SUPER)" = "$(conf SUPER_BYTES)" ] || reject "super size"
[ "$(dd if=$SUPER bs=4096 count=256 2>/dev/null | sha256sum | cut -d' ' -f1)" = "$(conf SUPER_LP_SHA256)" ] || reject "super LP metadata changed"
SECTORS=$(conf SYSTEM_SECTORS)
START=$(conf SYSTEM_SUPER_START_SECTOR)
dmctl delete $MAPPER > /dev/null 2>&1
dmctl create $MAPPER linear 0 $SECTORS $SUPER $START || reject "dmctl create"
SYS=$(dmctl getpath $MAPPER)
for i in 1 2 3 4 5 6 7 8 9 10; do [ -b "$SYS" ] && break; sleep 0.3; done
[ "$(blockdev --getsize64 $SYS)" = "$(conf SYSTEM_BYTES)" ] || reject "mapped system size"

# Every region must be the base or already the target (resume after an interruption).
pending=0
while read name off count base target file; do
    now=$(region_sha $off $count)
    if [ "$now" = "$base" ]; then
        pending=$((pending + 1))
    elif [ "$now" != "$target" ]; then
        reject "system region $name ($off+$count) is neither base nor target"
    fi
done < $D/chunks.list
total=$(wc -l < $D/chunks.list)
say "regions to write: $pending of $total"
if [ "$pending" = "$total" ] && [ ! -f $D/writing ]; then
    say "hashing the base system"
    [ "$(sha $SYS)" = "$(conf BASE_SYSTEM_SHA256)" ] || reject "system is not the base $(conf BASE_VERSION)"
fi

# From here on the device must come back to this updater until the update is complete.
dd if=/dev/zero of=/tmp/bcb bs=2048 count=1 2>/dev/null
printf 'boot-recovery' | dd of=/tmp/bcb conv=notrunc 2>/dev/null
dd if=/tmp/bcb of=$MISC bs=2048 count=1 conv=notrunc 2>/dev/null
[ "$(dd if=$MISC bs=2048 count=1 2>/dev/null | sha256sum | cut -d' ' -f1)" = "$(sha /tmp/bcb)" ] || fail "BCB write"
touch $D/writing
status "writing"

n=0
while read name off count base target file; do
    n=$((n + 1))
    [ "$(region_sha $off $count)" = "$target" ] && continue
    zcat $D/$name > /tmp/chunk.bin || fail "unpack $name"
    [ "$(sha /tmp/chunk.bin)" = "$target" ] || fail "unpacked $name hash"
    dd if=/tmp/chunk.bin of=$SYS bs=4096 seek=$off conv=notrunc 2>/dev/null || fail "write $name"
    [ "$(region_sha $off $count)" = "$target" ] || fail "readback $name"
    rm /tmp/chunk.bin
    say "wrote $name ($n/$total)"
done < $D/chunks.list
sync

say "hashing the target system"
[ "$(sha $SYS)" = "$(conf SYSTEM_SHA256)" ] || fail "target system hash"
status "system written"

for name in vbmeta_system vbmeta; do
    part=/dev/block/bootdevice/by-name/$name
    [ "$(blockdev --getsize64 $part)" = "$(stat -c %s $D/$name.img)" ] || fail "$name size"
    if [ "$(sha $part)" != "$(conf ${name}_sha256)" ]; then
        dd if=$D/$name.img of=$part bs=4096 conv=notrunc 2>/dev/null || fail "write $name"
        [ "$(sha $part)" = "$(conf ${name}_sha256)" ] || fail "readback $name"
        say "wrote $name"
    fi
done
sync
dmctl delete $MAPPER

dd if=/dev/zero of=$MISC bs=2048 count=1 conv=notrunc 2>/dev/null
rm -f $D/ready $D/writing
status "ok $(conf VERSION)"
say "=== done, rebooting ==="
reboot_android
