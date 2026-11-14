#!/system/bin/sh
cd /data/user/0/com.midsancode.docklet || exit 1
APK=$(pm path com.midsancode.docklet | sed 's/^package://')
LIB=$(dirname "$APK")/lib/arm64
export LD_LIBRARY_PATH="$LIB"
rm -f files/q.in files/q.out; mkfifo files/q.in
( sleep 24
  echo "mount -t proc proc /proc; mount -t sysfs sysfs /sys; mount -t devtmpfs devtmpfs /dev"
  sleep 2
  echo "echo ===A===; ls -ld /bin /lib /usr/bin; echo ===B===; ls -l /bin/busybox /bin/sh /bin/cat 2>&1"
  sleep 3
  echo "echo ===C===; ls /bin | wc -l; echo ===D===; ls -l /usr/bin/podman"
  sleep 2
  echo "echo ===PROBE2-DONE==="
  sleep 15 ) > files/q.in &
"$LIB/libqemu-system-aarch64.so" -L files/qemu/share/qemu -machine virt -cpu max -smp 2 -m 1024 -nographic \
  -kernel files/vm-bundle/0.1.0/Image -append "console=ttyAMA0 root=/dev/vda rootfstype=squashfs ro init=/bin/sh" \
  -drive file=files/vm-bundle/0.1.0/rootfs.squashfs,if=virtio,format=raw,readonly=on \
  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 < files/q.in > files/q.out 2>&1
