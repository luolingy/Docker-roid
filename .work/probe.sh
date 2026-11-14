#!/system/bin/sh
cd /data/user/0/com.midsancode.docklet || exit 1
APK=$(pm path com.midsancode.docklet | sed 's/^package://')
LIB=$(dirname "$APK")/lib/arm64
export LD_LIBRARY_PATH="$LIB"
rm -f files/p.in files/p.out
mkfifo files/p.in
(
  sleep 18
  echo "mount -t proc proc /proc; mount -t sysfs sysfs /sys; mount -t devtmpfs devtmpfs /dev; mount -t devpts devpts /dev/pts 2>/dev/null"
  sleep 2
  echo "echo ===1===; cat /proc/filesystems | tr '\n' ' '"
  sleep 2
  echo "echo ===2===; ls /sbin/mkfs* /usr/sbin/mkfs* 2>&1; busybox --list | grep -E 'mkfs|mdev' | tr '\n' ' '"
  sleep 2
  echo "echo ===3===; ls /usr/bin | grep -E 'qemu|fuse|slirp|newuid' | tr '\n' ' '"
  sleep 2
  echo "echo ===4===; ls -l /etc/resolv.conf; cat /etc/resolv.conf 2>&1"
  sleep 2
  echo "echo ===5===; ls /usr/libexec/podman 2>/dev/null | tr '\n' ' '; echo; cat /etc/containers/storage.conf 2>/dev/null | grep -vE '^#|^$' | head -15"
  sleep 3
  echo "echo ===6===; cat /proc/config.gz 2>/dev/null | head -c 0; ls /lib/modules 2>&1 | head -3"
  sleep 2
  echo "echo ===PROBE-DONE==="
  sleep 20
) > files/p.in &
"$LIB/libqemu-system-aarch64.so" \
  -L files/qemu/share/qemu -machine virt -cpu max -smp 2 -m 1024 -nographic \
  -kernel files/vm-bundle/0.1.0/Image \
  -append "console=ttyAMA0 root=/dev/vda rootfstype=squashfs ro init=/bin/sh" \
  -drive file=files/vm-bundle/0.1.0/rootfs.squashfs,if=virtio,format=raw,readonly=on \
  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 \
  < files/p.in > files/p.out 2>&1
