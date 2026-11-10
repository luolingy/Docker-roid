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

echo ">> 打开容器所需配置（见 kernel/docker-roid.config）"
cat ../docker-roid.config >> .config 2>/dev/null || true

make ARCH=arm64 olddefconfig
echo ">> 编译（$(nproc) 并行）"
make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- -j"$JOBS" Image

cp arch/arm64/boot/Image "$OUT/Image"
ls -lh "$OUT/Image"
echo ">> 完成：$OUT/Image（记得 strip/压到 ~6MB，勿含 debug 符号）"
