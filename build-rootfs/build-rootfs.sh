# ============================================================
# build-rootfs.sh — 构建 Alpine + Podman/crun 的 squashfs 底座
# 均衡档目标：rootfs.squashfs ≈ 25–35MB（xz 压缩）
# 需在 x86_64 Linux 上用 qemu-user 或原生 arm64 上执行
# ============================================================
set -euo pipefail
set -x   # trace 每条命令，GHA 日志可见

ALPINE_VER="${ALPINE_VER:-3.20}"
ARCH="aarch64"
ROOT="${ROOT:-$(pwd)/out}/rootfs"
OUT="$(pwd)/out"
ROOTFS_IMG="$OUT/rootfs.squashfs"

echo ">> 准备 Alpine $ALPINE_VER ($ARCH) minirootfs"
mkdir -p "$ROOT" "$OUT"
cd "$ROOT"
if [ ! -f alpine-minirootfs.tar.gz ]; then
  # 注意：ALPINE_VER 已是 "3.20" 形式，目录就是 v3.20，不要再剥后缀
  BASE_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER}/releases/${ARCH}"
  # 主源：latest-releases.yaml（机器可读，比解析 HTML 稳）
  LATEST=$(wget -qO- "$BASE_URL/latest-releases.yaml" 2>/dev/null \
    | awk -v br="$ALPINE_VER" '/^date:/ {date=$2} $1=="branch:" && $2==br {seen=1} seen && $1=="file:" && $2 ~ /alpine-minirootfs/ {print $2; exit}')
  # 回退：镜像站目录列表
  if [ -z "$LATEST" ]; then
    echo "!! cdn latest-releases.yaml 不可达，尝试镜像站"
    MIRROR_URL="https://mirrors.tuna.tsinghua.edu.cn/alpine/v${ALPINE_VER}/releases/${ARCH}"
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
https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER}/main
https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER}/community
EOF

# 用 chroot + qemu-user 静态（在非 arm64 宿主）
cp /usr/bin/qemu-aarch64-static "$ROOT/usr/bin/" 2>/dev/null || true
# GHA runner 需 sudo 才能 chroot；qemu-user-static 的 binfmt 让 aarch64 透明执行
# 挂载 resolv.conf 保证 chroot 内 apk 有 DNS；退出时清理
cp /etc/resolv.conf "$ROOT/etc/resolv.conf"
sudo mount --bind /proc "$ROOT/proc" 2>/dev/null || true
sudo chroot "$ROOT" /bin/sh -c '
  set -e
  apk update
  # 容器运行所需 + openrc 初始化系统
  apk add --no-cache podman crun iptables ip6tables fuse-overlayfs slirp4netns \
      ca-certificates tzdata nftables podman-compose python3 openrc
  # 精简：删文档/缓存
  rm -rf /var/cache/apk/* /usr/share/man/* /usr/share/doc/*
' || { echo "!! chroot apk 安装失败"; sudo umount "$ROOT/proc" 2>/dev/null || true; exit 1; }
sudo umount "$ROOT/proc" 2>/dev/null || true
rm -f "$ROOT/usr/bin/qemu-aarch64-static" "$ROOT/etc/resolv.conf"

echo ">> 安装 Docklet 启动服务（OpenRC）"
# 把 guest 脚本装为 OpenRC 服务 + 相关运行脚本
mkdir -p "$ROOT/etc/init.d" "$ROOT/etc/runlevels/default" "$ROOT/usr/local/bin"
cp build-rootfs/guest/etc/init.d/docklet-boot "$ROOT/etc/init.d/docklet" 2>/dev/null || \
  cp guest/etc/init.d/docklet-boot "$ROOT/etc/init.d/docklet" 2>/dev/null || true
if [ -f "$ROOT/etc/init.d/docklet" ]; then
  chmod +x "$ROOT/etc/init.d/docklet"
  # 注册到 default 运行级
  ln -sf /etc/init.d/docklet "$ROOT/etc/runlevels/default/docklet" 2>/dev/null || true
fi
# 确保必要运行级 softlink（OpenRC 首次 boot）
for svc in boot sysinit default; do mkdir -p "$ROOT/etc/runlevels/$svc"; done
# 用户态 shell/核心服务
ln -sf /etc/init.d/hostname "$ROOT/etc/runlevels/boot/hostname" 2>/dev/null || true

echo ">> 生成 squashfs (xz)"
mksquashfs "$ROOT" "$ROOTFS_IMG" -comp xz -noappend -b 256K
ls -lh "$ROOTFS_IMG"
echo ">> 完成：$ROOTFS_IMG"
