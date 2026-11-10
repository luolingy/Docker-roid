# ============================================================
# build-rootfs.sh — 构建 Alpine + Podman/crun 的 squashfs 底座
# 均衡档目标：rootfs.squashfs ≈ 25–35MB（xz 压缩）
# 需在 x86_64 Linux 上用 qemu-user 或原生 arm64 上执行
# ============================================================
set -euo pipefail

ALPINE_VER="${ALPINE_VER:-3.20}"
ARCH="aarch64"
ROOT="${ROOT:-$(pwd)/out}/rootfs"
OUT="$(pwd)/out"
ROOTFS_IMG="$OUT/rootfs.squashfs"

echo ">> 准备 Alpine $ALPINE_VER ($ARCH) minirootfs"
mkdir -p "$ROOT" "$OUT"
cd "$ROOT"
if [ ! -f alpine-minirootfs.tar.gz ]; then
  BASE_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER%.*}/releases/${ARCH}"
  # 主源：latest-releases.yaml（机器可读，比解析 HTML 稳）
  LATEST=$(wget -qO- "$BASE_URL/latest-releases.yaml" 2>/dev/null \
    | awk -v br="$ALPINE_VER" '/^date:/ {date=$2} $1=="branch:" && $2==br {seen=1} seen && $1=="file:" && $2 ~ /alpine-minirootfs/ {print $2; exit}')
  # 回退：镜像站目录列表
  if [ -z "$LATEST" ]; then
    echo "!! cdn latest-releases.yaml 不可达，尝试镜像站"
    MIRROR_URL="https://mirrors.tuna.tsinghua.edu.cn/alpine/v${ALPINE_VER%.*}/releases/${ARCH}"
    LATEST=$(wget -qO- "$MIRROR_URL/" 2>/dev/null \
      | grep -o "alpine-minirootfs-${ALPINE_VER//./\\.}\.[0-9]*-${ARCH}\.tar\.gz" | sort -uV | tail -1)
    BASE_URL="$MIRROR_URL"
  fi
  # 再回退：固定已知可用版本
  if [ -z "$LATEST" ]; then
    echo "!! 动态解析失败，回退固定版本 3.20.6"
    LATEST="alpine-minirootfs-3.20.6-${ARCH}.tar.gz"
  fi
  echo ">> 下载 $LATEST（来自 $BASE_URL）"
  wget --tries=3 -O alpine-minirootfs.tar.gz "$BASE_URL/$LATEST" || { echo "!! 下载失败"; exit 1; }
  ls -lh alpine-minirootfs.tar.gz
fi
tar -xzf alpine-minirootfs.tar.gz -C "$ROOT"

echo ">> 配置 apk 源 + 安装 Podman/crun + 精简"
cat > "$ROOT/etc/apk/repositories" <<EOF
https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER%.*}/main
https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER%.*}/community
EOF

# 用 chroot + qemu-user 静态（在非 arm64 宿主）
cp /usr/bin/qemu-aarch64-static "$ROOT/usr/bin/" 2>/dev/null || true
chroot "$ROOT" /bin/sh -c '
  set -e
  apk update
  # 只装容器所需：podman + crun + 网络工具
  apk add --no-cache podman crun iptables ip6tables fuse-overlayfs slirp4netns \
      ca-certificates tzdata nftables
  # 精简：删文档/缓存
  rm -rf /var/cache/apk/* /usr/share/man/* /usr/share/doc/*
'
rm -f "$ROOT/usr/bin/qemu-aarch64-static"

echo ">> 安装 docker-roid 启动脚本与服务（见 build-rootfs/guest/）"
cp -r guest/etc "$ROOT/" 2>/dev/null || echo "   (guest 脚本未放置，跳过)"

echo ">> 生成 squashfs (xz)"
mksquashfs "$ROOT" "$ROOTFS_IMG" -comp xz -noappend -b 256K
ls -lh "$ROOTFS_IMG"
echo ">> 完成：$ROOTFS_IMG"
