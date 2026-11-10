# ============================================================
# build-qemu.sh — 交叉编译裁剪版 qemu-system-aarch64（均衡档 ≈ 10MB）
# 仅保留：aarch64-softmmu + TCG + virt 机器
# ============================================================
set -euo pipefail

QEMU_VER="${QEMU_VER:-9.1.0}"
OUT="$(pwd)/out"
JOBS="${JOBS:-$(nproc)}"

mkdir -p "$OUT"
cd "$OUT"
if [ ! -d "qemu-${QEMU_VER}" ]; then
  wget -q "https://download.qemu.org/qemu-${QEMU_VER}.tar.xz"
  tar -xf "qemu-${QEMU_VER}.tar.xz"
fi
cd "qemu-${QEMU_VER}"

echo ">> configure（只保留 aarch64-softmmu，裁掉图形/音频/其他架构）"
./configure \
  --target-list=aarch64-softmmu \
  --cross-prefix=aarch64-linux-gnu- \
  --disable-werror \
  --without-default-features \
  --disable-gtk --disable-sdl --disable-opengl --disable-virglrenderer \
  --disable-curses --disable-vnc --disable-spice \
  --disable-audio --disable-alsa --disable-pulse --disable-coreaudio \
  --disable-docs --disable-tools --disable-guest-agent \
  --disable-capstone --disable-slirp-smbd

make -j"$JOBS" qemu-system-aarch64

echo ">> strip"
aarch64-linux-gnu-strip qemu-system-aarch64 \
  --strip-unneeded \
  -R .comment -R .note

# 注意：Android 13+/16KB 页设备需 16KB 对齐——需在链接时加
#   -Wl,-z,max-page-size=16384
# 若 strip 后仍有问题，改为在 configure 的 LDFLAGS 中加入该项重新链接。

cp qemu-system-aarch64 "$OUT/qemu-system-aarch64"
ls -lh "$OUT/qemu-system-aarch64"
echo ">> 完成：把 $OUT/qemu-system-aarch64 放进 app/src/main/assets/"
