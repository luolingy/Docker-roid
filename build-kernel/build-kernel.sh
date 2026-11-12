# ============================================================
# build-kernel.sh — 裁剪 arm64 guest 内核（均衡档目标 Image ≈ 6MB）
# 从 tinyconfig 起步，只开容器所需，剥符号表
# ============================================================
set -euo pipefail

KVER="${KVER:-6.12}"
OUT="$(pwd)/out"
JOBS="${JOBS:-$(nproc)}"

echo ">> 获取内核源码 $KVER"
mkdir -p "$OUT"
if [ ! -d "$OUT/linux" ]; then
  wget -q "https://cdn.kernel.org/pub/linux/kernel/v${KVER%%.*}.x/linux-${KVER}.tar.xz" -O "$OUT/linux.tar.xz"
  tar -xf "$OUT/linux.tar.xz" -C "$OUT"
  mv "$OUT/linux-${KVER}" "$OUT/linux"
fi
cd "$OUT/linux"

echo ">> 从 tinyconfig 起步"
make ARCH=arm64 tinyconfig

echo ">> 打开容器所需配置（见 kernel/Docklet.config）"
cat ../Docklet.config >> .config 2>/dev/null || true

make ARCH=arm64 olddefconfig

echo ">> 校验关键配置是否生效（缺一即失败，防止产出假内核）"
MISSING=0
while read -r req; do
  [ -z "$req" ] && continue
  if ! grep -q "^${req}=y$" .config; then
    echo "  !! 缺失: $req"
    MISSING=$((MISSING+1))
  fi
done <<'EOF'
CONFIG_ARM64
CONFIG_OVERLAY_FS
CONFIG_EXT4_FS
CONFIG_SQUASHFS
CONFIG_SQUASHFS_XZ
CONFIG_NAMESPACES
CONFIG_PID_NS
CONFIG_NET_NS
CONFIG_USER_NS
CONFIG_CGROUPS
CONFIG_MEMCG
CONFIG_VIRTIO
CONFIG_VIRTIO_PCI
CONFIG_VIRTIO_BLK
CONFIG_VIRTIO_NET
CONFIG_VIRTIO_CONSOLE
CONFIG_NETFILTER
CONFIG_NF_TABLES
CONFIG_NF_TABLES_INET
CONFIG_NFT_NAT
CONFIG_NF_NAT
CONFIG_NF_CONNTRACK
CONFIG_NETFILTER_XTABLES
CONFIG_NETFILTER_XT_TARGET_MASQUERADE
CONFIG_IP_NF_NAT
CONFIG_VETH
CONFIG_TUN
CONFIG_BRIDGE
CONFIG_ZRAM
CONFIG_DEVTMPFS
CONFIG_PROC_FS
CONFIG_SYSFS
CONFIG_TMPFS
CONFIG_UNIX
CONFIG_INET
CONFIG_PACKET
EOF
if [ "$MISSING" -gt 0 ]; then
  echo "!! $MISSING 个关键配置未生效（tinyconfig 依赖未满足）。改用 defconfig 裁剪模式重试。"
  echo ">> 降级方案：基于 defconfig 关闭无关大项"
  make ARCH=arm64 defconfig
  ./scripts/config --file .config \
    --enable OVERLAY_FS --enable SQUASHFS --enable SQUASHFS_XZ \
    --enable USER_NS --enable MEMCG --enable ZRAM \
    --enable TMPFS --enable SHMEM --enable TMPFS_POSIX_ACL \
    --enable DEVTMPFS --enable DEVTMPFS_MOUNT --enable PROC_FS --enable SYSFS \
    --enable VIRTIO_CONSOLE --enable TUN --enable VETH \
    --enable BINFMT_MISC --enable BINFMT_ELF \
    --enable NETFILTER --enable NETFILTER_ADVANCED \
    --enable NF_TABLES --enable NF_TABLES_INET --enable NF_TABLES_NETDEV \
    --enable NFT_NAT --enable NFT_MASQ --enable NFT_COMPAT \
    --enable NF_NAT --enable NF_CONNTRACK \
    --enable NETFILTER_XTABLES --enable NETFILTER_XT_TARGET_MASQUERADE \
    --enable NETFILTER_XT_MATCH_CONNTRACK --enable NETFILTER_XT_MATCH_ADDRTYPE \
    --enable IP_NF_IPTABLES --enable IP_NF_FILTER --enable IP_NF_NAT \
    --enable IP_NF_TARGET_MASQUERADE --enable IP_NF_MANGLE \
    --enable IP6_NF_IPTABLES --enable BRIDGE --enable BRIDGE_NETFILTER \
    --enable NET_SCHED --enable NET_CLS_ACT --enable NET_ACT_MIRRED --enable DUMMY \
    --disable DEBUG_INFO --disable KALLSYMS --disable FTRACE \
    --disable DRM --disable SOUND --disable SND \
    --disable WLAN --disable WIRELESS --disable BLUETOOTH \
    --disable USB_SUPPORT --disable INPUT_TOUCHSCREEN \
    --disable FB --disable VIDEO_DEV
  make ARCH=arm64 olddefconfig
  # 复验
  STILL=0
  while read -r req; do
    [ -z "$req" ] && continue
    grep -q "^${req}=y$" .config || { echo "  !! 仍缺失: $req"; STILL=$((STILL+1)); }
  done <<'EOF'
CONFIG_OVERLAY_FS
CONFIG_EXT4_FS
CONFIG_SQUASHFS
CONFIG_TMPFS
CONFIG_SHMEM
CONFIG_DEVTMPFS
CONFIG_VIRTIO
CONFIG_VIRTIO_BLK
CONFIG_VIRTIO_NET
CONFIG_VETH
CONFIG_TUN
CONFIG_NETFILTER
CONFIG_NF_TABLES
CONFIG_NF_TABLES_INET
CONFIG_NFT_NAT
CONFIG_NETFILTER_XT_TARGET_MASQUERADE
CONFIG_BINFMT_MISC
CONFIG_MEMCG
CONFIG_USER_NS
EOF
  [ "$STILL" -gt 0 ] && { echo "!! 复验仍失败 $STILL 项，中止"; exit 1; }
fi

echo ">> 编译（$(nproc) 并行）"
make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- -j"$JOBS" Image

cp arch/arm64/boot/Image "$OUT/Image"
ls -lh "$OUT/Image"
echo ">> 完成：$OUT/Image（记得 strip/压到 ~6MB，勿含 debug 符号）"
