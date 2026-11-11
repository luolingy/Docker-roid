#!/bin/bash
# build-jnilibs.sh — 在 CI 上：解析 Termux QEMU 完整依赖闭包 → 下载 → 提取
# → make-min-jnilibs.sh 生成 jniLibs 到 app/src/main/jniLibs/arm64-v8a
# 用法: bash build-jnilibs.sh <project-root>
set -euo pipefail
ROOT="${1:?project root}"
BASE="https://packages.termux.dev/apt/termux-main"
WORK="$(pwd)/jnilibs-work"
mkdir -p "$WORK/debs" "$WORK/libs" "$WORK/extract"

echo ">> 1. 下载 Packages 索引"
curl -sL "$BASE/dists/stable/main/binary-aarch64/Packages.gz" -o "$WORK/Packages.gz"
gunzip -f "$WORK/Packages.gz"

echo ">> 2. 递归解析 qemu-system-aarch64-headless 依赖闭包"
python3 - "$WORK/Packages" "$WORK/debs.list" <<'PY'
import sys, re
pkgs = {}
cur = None
buf = {}
def flush():
    if 'Package' in buf:
        pkgs[buf['Package']] = buf.copy()
with open(sys.argv[1], encoding='utf-8', errors='replace') as f:
    for line in f:
        line = line.rstrip('\n')
        if not line.strip():
            flush(); buf = {}; continue
        if ': ' in line:
            k, v = line.split(': ', 1); buf[k] = v
flush()
seen = set(); order = []; stack = ['qemu-system-aarch64-headless']
while stack:
    n = stack.pop()
    if n in seen or n not in pkgs: continue
    seen.add(n); order.append(n)
    dep = pkgs[n].get('Depends', '')
    for d in dep.split(','):
        d = re.sub(r'\(.*\)', '', d).strip().split(' ')[0]
        if d: stack.append(d)
with open(sys.argv[2], 'w') as out:
    for n in order:
        out.write(pkgs[n].get('Filename','') + '\n')
print(f"closure packages: {len(order)}")
PY

echo ">> 3. 下载并解包"
cd "$WORK"
while read -r rel; do
  [ -z "$rel" ] && continue
  name=$(basename "$rel" .deb)
  [ -f "debs/$name.deb" ] || curl -sL "$BASE/$rel" -o "debs/$name.deb"
  d="extract/$name"; mkdir -p "$d"
  ( cd "$d" && ar x "../../debs/$name.deb" 2>/dev/null; for t in data.tar.*; do [ -f "$t" ] && tar xf "$t" 2>/dev/null; done ) || true
done < debs.list

echo ">> 4. 收集 .so"
find extract -type f \( -name '*.so' -o -name '*.so.*' \) -print0 2>/dev/null | while IFS= read -r -d '' f; do cp -f "$f" libs/ 2>/dev/null || true; done
echo "libs: $(ls -1 libs | wc -l)"

echo ">> 5. 主二进制"
QEMU_BIN=$(find extract -path '*qemu-system-aarch64-headless*/bin/qemu-system-aarch64' -type f | head -1)
[ -n "$QEMU_BIN" ] || { echo "!! qemu binary not found"; exit 1; }

echo ">> 6. 生成最小 jniLibs"
capstone_ok=1
bash "$ROOT/build-qemu/make-min-jnilibs.sh" "$WORK/libs" "$QEMU_BIN" "$ROOT/app/src/main/jniLibs/arm64-v8a"

echo ">> 完成 jniLibs：$(ls -1 "$ROOT/app/src/main/jniLibs/arm64-v8a" | wc -l) 个"
