# ============================================================
# build-qemu.sh — 用 Android NDK 交叉编译 qemu-system-aarch64（bionic ABI）
# 目标：arm64-v8a Android 可执行的 QEMU（TCG + virt + SLIRP）
# 用法：NDK_HOME=/path/to/ndk bash build-qemu.sh
# ============================================================
set -euo pipefail

QEMU_VER="${QEMU_VER:-8.2.6}"      # 8.2 LTS 对 NDK 编译兼容性较好
NDK_HOME="${NDK_HOME:?请设置 NDK_HOME}"
API="${API:-26}"                    # minSdk 26
OUT="$(pwd)/out"
JOBS="${JOBS:-$(nproc)}"

TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
CXX="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
STRIP="$TOOLCHAIN/bin/llvm-strip"
NM="$TOOLCHAIN/bin/llvm-nm"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"

[ -x "$CC" ] || { echo "!! NDK clang 不存在: $CC"; exit 1; }

# QEMU configure 通过 --cc/--cxx + --cpu 推导 ar/nm 等，不认 --ar/--nm/--ranlib/--strip
# 用环境变量方式传入这些工具
export AR="$AR" NM="$NM" RANLIB="$RANLIB" STRIP="$STRIP"

mkdir -p "$OUT"
cd "$OUT"
if [ ! -d "qemu-${QEMU_VER}" ]; then
  echo ">> 下载 QEMU $QEMU_VER"
  wget -q "https://download.qemu.org/qemu-${QEMU_VER}.tar.xz" -O qemu.tar.xz
  tar -xf qemu.tar.xz
fi
cd "qemu-${QEMU_VER}"

echo ">> configure（NDK 交叉，aarch64-softmmu + TCG + SLIRP）"
# 16KB 页对齐（Android 13+/16KB 设备强制）
export LDFLAGS="-Wl,-z,max-page-size=16384"
./configure \
  --target-list=aarch64-softmmu \
  --cc="$CC" --cxx="$CXX" --cpu=aarch64 \
  --disable-werror \
  --without-default-features \
  --enable-tcg --enable-slirp --enable-fdt=builtin \
  --disable-docs --disable-tools --disable-guest-agent --disable-capstone \
  --disable-plugins --disable-user \
  --extra-cflags="-O2" \
  --extra-ldflags="-Wl,-z,max-page-size=16384"

echo ">> make（$(nproc) 并行）"
make -j"$JOBS" qemu-system-aarch64

echo ">> strip"
"$STRIP" --strip-unneeded qemu-system-aarch64 || true

cp qemu-system-aarch64 "$OUT/qemu-system-aarch64"
ls -lh "$OUT/qemu-system-aarch64"
echo ">> 完成：$OUT/qemu-system-aarch64"
