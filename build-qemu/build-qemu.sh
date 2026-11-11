# ============================================================
# build-qemu.sh — 从 Termux 仓库组装可在普通 Android 运行的 QEMU
#
# QEMU 硬依赖 glib/pixman 等，Android bionic 没有；Termux 维护了完整
# aarch64 Android 版（/system/bin/linker64 + bionic libc）。
# 本脚本拉取 qemu-system-aarch64-headless + 全部运行时依赖 deb，解出
# 所有 .so 与二进制，打成 tar。App 侧解到 filesDir/qemu 并设 LD_LIBRARY_PATH。
# ============================================================
set -euo pipefail

BASE="https://packages.termux.dev/apt/termux-main"
POOL="$BASE/pool/main"
OUT="$(pwd)/out"
STAGE="$OUT/qemu-bundle"
DL="$OUT/dl"
mkdir -p "$OUT" "$STAGE/bin" "$STAGE/lib" "$DL"

# 包名 -> pool 路径（来自 Packages 索引的 Filename 字段，已验证）
declare -A DEBS=(
  [qemu-system-aarch64-headless]="q/qemu-system-aarch64-headless/qemu-system-aarch64-headless_1%3A11.1.3_aarch64.deb"
  [alsa-lib]="a/alsa-lib/alsa-lib_1.2.16.1_aarch64.deb"
  [dtc]="d/dtc/dtc_1.8.1_aarch64.deb"
  [glib]="g/glib/glib_2.90.1_aarch64.deb"
  [jack2]="j/jack2/jack2_1.9.22-2_aarch64.deb"
  [libbz2]="libb/libbz2/libbz2_1.0.8-8_aarch64.deb"
  [libcurl]="libc/libcurl/libcurl_8.23.0_aarch64.deb"
  [libdw]="libd/libdw/libdw_0.197_aarch64.deb"
  [libgmp]="libg/libgmp/libgmp_6.3.0-2_aarch64.deb"
  [libgnutls]="libg/libgnutls/libgnutls_3.8.13-1_aarch64.deb"
  [libiconv]="libi/libiconv/libiconv_1.19.1_aarch64.deb"
  [libjpeg-turbo]="libj/libjpeg-turbo/libjpeg-turbo_3.2.1_aarch64.deb"
  [liblzo]="libl/liblzo/liblzo_2.10-5_aarch64.deb"
  [libnettle]="libn/libnettle/libnettle_4.0+really3.10.2_aarch64.deb"
  [libnfs]="libn/libnfs/libnfs_8.0.0_aarch64.deb"
  [libpixman]="libp/libpixman/libpixman_0.46.4-1_aarch64.deb"
  [libpng]="libp/libpng/libpng_1.6.59_aarch64.deb"
  [libslirp]="libs/libslirp/libslirp_4.9.7_aarch64.deb"
  [libspice-server]="libs/libspice-server/libspice-server_0.16.0-1_aarch64.deb"
  [libssh]="libs/libssh/libssh_0.12.2_aarch64.deb"
  [libusb]="libu/libusb/libusb_1.0.30_aarch64.deb"
  [libusbredir]="libu/libusbredir/libusbredir_0.15.0_aarch64.deb"
  [ncurses]="n/ncurses/ncurses_6.6.20260307+really6.5.20250830_aarch64.deb"
  [pulseaudio]="p/pulseaudio/pulseaudio_17.0-5_aarch64.deb"
  [qemu-common]="q/qemu-common/qemu-common_1%3A11.1.3_aarch64.deb"
  [zlib]="z/zlib/zlib_1.4_aarch64.deb"
  [zstd]="z/zstd/zstd_1.5.7-1_aarch64.deb"
)

fetch_extract() {
  local pkg="$1" rel="$2"
  local deb="$DL/$pkg.deb"
  [ -f "$deb" ] || wget -q "$POOL/$rel" -O "$deb" || { echo "  !! $pkg 下载失败"; return 1; }
  local dir="$DL/x_$pkg"
  rm -rf "$dir"; mkdir -p "$dir"; ( cd "$dir"
    ar x "$deb" 2>/dev/null || true
    for d in data.tar.*; do [ -f "$d" ] && tar xf "$d" 2>/dev/null || true; done )
  return 0
}

echo ">> 下载并解包 ${#DEBS[@]} 个包"
for pkg in "${!DEBS[@]}"; do
  echo "  -- $pkg"
  fetch_extract "$pkg" "${DEBS[$pkg]}" || true
done

echo ">> 收集二进制与 .so"
# 主二进制
MAIN="$DL/x_qemu-system-aarch64-headless/data/data/com.termux/files/usr/bin/qemu-system-aarch64"
[ -f "$MAIN" ] || { echo "!! 主二进制未找到: $MAIN"; exit 1; }
cp "$MAIN" "$STAGE/bin/qemu-system-aarch64"; chmod +x "$STAGE/bin/qemu-system-aarch64"

# 所有包里的 .so（含 .so.X.Y）
find "$DL" -path '*/usr/lib/*' \( -name '*.so' -o -name '*.so.*' \) -type f 2>/dev/null | while read -r f; do
  cp -a "$f" "$STAGE/lib/" 2>/dev/null || true
done

# qemu-common 可能含资源文件
for f in "$DL"/x_qemu-common/data/data/com.termux/files/usr/share/qemu/*; do
  [ -e "$f" ] || continue
  mkdir -p "$STAGE/share/qemu"; cp -a "$f" "$STAGE/share/qemu/" 2>/dev/null || true
done

echo ">> bundle 概况：bin=$(ls -1 "$STAGE/bin" | wc -l)  lib=$(ls -1 "$STAGE/lib" 2>/dev/null | wc -l)"
du -sh "$STAGE" || true

echo ">> 打包"
tar czf "$OUT/qemu-bundle.tar.gz" -C "$STAGE" .
ls -lh "$OUT/qemu-bundle.tar.gz"
echo ">> 完成"
