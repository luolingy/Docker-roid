#!/bin/bash
# make-min-jnilibs.sh — 从完整 Termux 库集中提取 QEMU 真正需要的最小依赖闭包，
# 重写 SONAME/NEEDED 为 lib*.so，输出到 jniLibs。
# 用法: bash make-min-jnilibs.sh <libs_dir> <qemu_bin> <dst>
set -euo pipefail
LIBS="${1:?libs}"; QEMU="${2:?qemu}"; DST="${3:?dst}"
rm -rf "$DST"; mkdir -p "$DST"

# 1) 建 basename -> 实际文件（优先 .so，其次 .so.N）
declare -A HAVE
for f in "$LIBS"/*; do
  [ -f "$f" ] || continue
  b=$(basename "$f")
  case "$b" in *.so|*.so.*) HAVE["$b"]="$f";; esac
done

# 2) 从 qemu 开始，按 NEEDED 递归收集闭包（跳过 bionic 系统库）
BIONIC="libc.so|libm.so|libdl.so|liblog.so|libstdc++.so|libandroid.so|libz.so"
declare -A NEED   # neededName -> 实际文件
declare -A SEEN
queue=("$QEMU")
while [ ${#queue[@]} -gt 0 ]; do
  cur="${queue[0]}"; queue=("${queue[@]:1}")
  [ -f "$cur" ] || continue
  while read -r nm; do
    [ -z "$nm" ] && continue
    echo "$nm" | grep -qE "^($BIONIC)$" && continue
    [ -n "${SEEN[$nm]:-}" ] && continue
    SEEN["$nm"]=1
    # 找该 name 对应的实际文件：精确名 -> 或同名去版本
    if [ -n "${HAVE[$nm]:-}" ]; then
      NEED["$nm"]="${HAVE[$nm]}"
      queue+=("${HAVE[$nm]}")
    else
      base=$(echo "$nm" | sed -E 's/^(lib.*)\.so(\..*)?$/\1/')
      # 尝试 base.so / base.so.*
      cand=""
      [ -n "${HAVE[$base.so]:-}" ] && cand="${HAVE[$base.so]}"
      if [ -z "$cand" ]; then
        for k in "${!HAVE[@]}"; do case "$k" in "$base".so.*) cand="${HAVE[$k]}"; break;; esac; done
      fi
      if [ -n "$cand" ]; then NEED["$nm"]="$cand"; queue+=("$cand")
      else echo "  [MISS] $nm"; fi
    fi
  done < <(patchelf --print-needed "$cur" 2>/dev/null || true)
done

echo ">> 闭包库数: ${#NEED[@]}"

# 3) 复制为最终名 libXXX.so，并建立 旧NEEDED名 -> 最终名 映射
declare -A FINAL   # oldNed -> finalBase
for nm in "${!NEED[@]}"; do
  base=$(echo "$nm" | sed -E 's/^(lib.*)\.so(\..*)?$/\1/')
  FINAL["$nm"]="${base}.so"
done

# 复制每个最终名（去重）
declare -A COPIED
for nm in "${!NEED[@]}"; do
  fb="${FINAL[$nm]}"
  [ -n "${COPIED[$fb]:-}" ] && continue
  COPIED["$fb"]=1
  cp -f "${NEED[$nm]}" "$DST/$fb"
  patchelf --set-soname "$fb" "$DST/$fb" 2>/dev/null || true
done

# 4) 重写所有 NEEDED 引用
for f in "$DST"/*.so; do
  for nm in "${!FINAL[@]}"; do
    patchelf --replace-needed "$nm" "${FINAL[$nm]}" "$f" 2>/dev/null || true
  done
done

# 5) QEMU 本体
cp -f "$QEMU" "$DST/libqemu-system-aarch64.so"
chmod +x "$DST/libqemu-system-aarch64.so"
for nm in "${!FINAL[@]}"; do
  patchelf --replace-needed "$nm" "${FINAL[$nm]}" "$DST/libqemu-system-aarch64.so" 2>/dev/null || true
done

echo ">> jniLibs 文件数: $(ls -1 "$DST" | wc -l)"
echo ">> QEMU 剩余 NEEDED:"
patchelf --print-needed "$DST/libqemu-system-aarch64.so"
