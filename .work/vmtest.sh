#!/system/bin/sh
cd /data/user/0/com.midsancode.docklet || exit 1
APK=$(pm path com.midsancode.docklet | sed 's/^package://')
LIB=$(dirname "$APK")/lib/arm64
export LD_LIBRARY_PATH="$LIB"
B=files/vm-bundle/0.1.0

[ -f "$B/data.ext4" ] || truncate -s 1024M "$B/data.ext4"

rm -f files/v.out
"$LIB/libqemu-system-aarch64.so" \
  -L files/qemu/share/qemu -machine virt -cpu max -smp 2 -m 1024 -nographic \
  -kernel "$B/Image" \
  -initrd files/initrd.img \
  -append "console=ttyAMA0 rdinit=/init docklet.dns=223.5.5.5,119.29.29.29 docklet.disk=/dev/vdb $1" \
  -drive file="$B/rootfs.squashfs",if=virtio,format=raw,readonly=on \
  -drive file="$B/data.ext4",if=virtio,format=raw \
  -netdev user,id=net0,hostfwd=tcp::2375-:2375 \
  -device virtio-net-pci,netdev=net0 \
  < /dev/null > files/v.out 2>&1
