#!/bin/bash
# prep-jnilibs.sh — Termux QEMU + 依赖库 -> Android jniLibs（lib*.so 规则）
set -euo pipefail

SRC="${1:?src dir}"
DST="${2:?dst dir}"
rm -rf "$DST"; mkdir -p "$DST"

# 收集所有库的"基名"（libXXX），映射到最终 libXXX.so
# 例: libglib-2.0.so.0.9000.1 / libglib-2.0.so -> 基名 libglib-2.0 -> libglib-2.0.so
declare -A BASE2FINAL
for f in "$SRC"/lib/*; do
  [ -f "$f" ] || continue
  b=$(basename "$f")
  case "$b" in
    *.so|*.so.*)
      base=$(echo "$b" | sed -E 's/^(lib.*)\.so(\..*)?$/\1/')
      BASE2FINAL["$base"]="${base}.so"
      ;;
  esac
done

# 复制（每个基名选一个文件：优先无版本后缀，否则任一）
for base in "${!BASE2FINAL[@]}"; do
  src_file=""
  if [ -f "$SRC/lib/${base}.so" ]; then src_file="$SRC/lib/${base}.so"
  else src_file=$(ls "$SRC/lib/${base}.so."* 2>/dev/null | head -1 || true); fi
  [ -n "$src_file" ] && [ -f "$src_file" ] && cp -f "$src_file" "$DST/${BASE2FINAL[$base]}"
done

# 生成所有可能的引用形式 -> 最终名，用于 replace-needed
# 对每个基名 base：可能被引用为 base.so、base.so.N、base.so.N.M...
declare -a REPLACES
for base in "${!BASE2FINAL[@]}"; do
  final="${BASE2FINAL[$base]}"
  REPLACES+=("${base}.so" "$final")
  # 收集该基名在实际 NEEDED 里出现的所有变体，稍后统一替换
done

# 收集所有 NEEDED 出现过的名字（从所有库和 qemu）
collect_needed() {
  for f in "$DST"/*.so "$SRC/bin/qemu-system-aarch64"; do
    [ -f "$f" ] || continue
    patchelf --print-needed "$f" 2>/dev/null || true
  done | sort -u
}

# 建 旧名->最终名 的精确替换表（用 NEEDED 实际出现的名字）
declare -A NEEDMAP
while read -r nm; do
  [ -z "$nm" ] && continue
  case "$nm" in
    libc.so|libm.so|libdl.so|liblog.so|libz.so|libstdc++.so) continue;;  # bionic 系统库，不动
  esac
  # 找它属于哪个基名
  base=$(echo "$nm" | sed -E 's/^(lib.*)\.so(\..*)?$/\1/')
  if [ -n "${BASE2FINAL[$base]:-}" ]; then
    NEEDMAP["$nm"]="${BASE2FINAL[$base]}"
  fi
done < <(collect_needed)

echo ">> 需要替换的 NEEDED 条目: ${#NEEDMAP[@]}"

# 执行替换
for f in "$DST"/*.so; do
  [ -f "$f" ] || continue
  for old in "${!NEEDMAP[@]}"; do
    patchelf --replace-needed "$old" "${NEEDMAP[$old]}" "$f" 2>/dev/null || true
  done
done

# QEMU 主二进制
cp -f "$SRC/bin/qemu-system-aarch64" "$DST/libqemu-system-aarch64.so"
chmod +x "$DST/libqemu-system-aarch64.so"
for old in "${!NEEDMAP[@]}"; do
  patchelf --replace-needed "$old" "${NEEDMAP[$old]}" "$DST/libqemu-system-aarch64.so" 2>/dev/null || true
done

echo ">> 生成 $(ls -1 "$DST" | wc -l) 个文件"
echo ">> QEMU NEEDED:"; patchelf --print-needed "$DST/libqemu-system-aarch64.so"
