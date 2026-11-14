#!/system/bin/sh
cd /data/user/0/com.midsancode.podroid || exit 1
APK=$(pm path com.midsancode.podroid | sed 's/^package://')
LIB=$(dirname "$APK")/lib/arm64
export LD_LIBRARY_PATH="$LIB"
rm -f files/t.out
"$LIB/libqemu-system-aarch64.so" \
  -L files/qemu/share/qemu -machine virt -cpu max -smp 2 -m 1024 -nographic \
  -kernel files/vm-bundle/0.1.0/Image \
  -initrd files/test-initrd.cpio.gz \
  -append "console=ttyAMA0 rdinit=/init root=/dev/vda rootfstype=squashfs ro" \
  -drive file=files/vm-bundle/0.1.0/rootfs.squashfs,if=virtio,format=raw,readonly=on \
  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 \
  < /dev/null > files/t.out 2>&1
