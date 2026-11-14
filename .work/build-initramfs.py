#!/usr/bin/env python3
"""构建 Docklet 的 initramfs（newc cpio + gzip）。

产物：app/src/main/assets/initrd.cpio.gz
内容：
  /init, /boot.sh          —— 引导脚本
  /bin/busybox             —— Alpine aarch64 静态 busybox（含软链）
  /sbin/mke2fs + /lib/*.so —— 首次启动格式化 ext4 数据盘（musl 动态链接）
  /etc/mke2fs.conf
"""
import gzip
import os
import stat
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
INITRAMFS = os.path.join(ROOT, "initramfs")
BUSYBOX = os.path.join(ROOT, "bb", "bin", "busybox.static")
APK = os.path.join(ROOT, "apk")
OUT = os.path.abspath(os.path.join(ROOT, "..", "app", "src", "main", "assets", "initrd.img"))

MAGIC = b"070701"


def _pad(b: bytes) -> bytes:
    return b + b"\0" * ((-len(b)) % 4)


def _header(name: str, mode: int, size: int, ino: int) -> bytes:
    namebytes = name.encode() + b"\0"
    fields = [ino, mode, 0, 0, 1, int(time.time()), size, 0, 0, 0, 0, len(namebytes), 0]
    return MAGIC + b"".join(b"%08X" % f for f in fields) + namebytes


class Builder:
    def __init__(self):
        self.buf = bytearray()
        self.ino = 1

    def _next(self):
        self.ino += 1
        return self.ino

    def dir(self, name):
        self.buf += _pad(_header(name, stat.S_IFDIR | 0o755, 0, self._next()))

    def file(self, name, data: bytes, mode=0o755):
        self.buf += _pad(_header(name, stat.S_IFREG | mode, len(data), self._next()))
        self.buf += _pad(data)

    def symlink(self, name, target: str):
        data = target.encode()
        self.buf += _pad(_header(name, stat.S_IFLNK | 0o777, len(data), self._next()))
        self.buf += _pad(data)

    def finish(self, out_path):
        self.buf += _pad(_header("TRAILER!!!", 0, 0, 0))
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with open(out_path, "wb") as fp, gzip.GzipFile(filename="", mode="wb", fileobj=fp, mtime=0) as gz:
            gz.write(bytes(self.buf))
        return len(self.buf), os.path.getsize(out_path)


def main():
    b = Builder()
    for d in ("/bin", "/sbin", "/lib", "/etc", "/dev", "/proc", "/sys", "/tmp", "/mnt"):
        b.dir(d)

    with open(BUSYBOX, "rb") as f:
        b.file("/bin/busybox", f.read())

    for applet in ("sh", "mount", "umount", "mkdir", "sed", "cat", "cp", "chmod",
                   "grep", "tr", "sleep", "blkid", "netstat", "switch_root", "echo",
                   "tail", "ls", "readlink"):
        b.symlink("/bin/" + applet, "busybox")

    # ext4 格式化工具（musl 动态链接）
    b.file("/sbin/mke2fs", open(os.path.join(APK, "sbin", "mke2fs"), "rb").read())
    b.symlink("/sbin/mkfs.ext4", "mke2fs")
    b.symlink("/sbin/mkfs.ext2", "mke2fs")

    libdir = os.path.join(APK, "lib")
    for entry in sorted(os.listdir(libdir)):
        full = os.path.join(libdir, entry)
        target = "/lib/" + entry
        if os.path.islink(full):
            b.symlink(target, os.readlink(full))
        else:
            b.file(target, open(full, "rb").read())

    # /usr/lib 里的额外依赖（libeconf 等）
    usrlib = os.path.join(APK, "usr", "lib")
    if os.path.isdir(usrlib):
        b.dir("/usr")
        b.dir("/usr/lib")
        for entry in sorted(os.listdir(usrlib)):
            full = os.path.join(usrlib, entry)
            target = "/usr/lib/" + entry
            if os.path.islink(full):
                b.symlink(target, os.readlink(full))
            else:
                b.file(target, open(full, "rb").read())

    conf = os.path.join(APK, "etc", "mke2fs.conf")
    if os.path.exists(conf):
        b.file("/etc/mke2fs.conf", open(conf, "rb").read(), 0o644)

    # 注意：必须归一化成 LF。若脚本带 CRLF，内核解析 shebang 会把解释器参数
    # 取成 "sh\r"，busybox 报 "sh: applet not found" 并以 127 退出 -> PID 1 死亡 -> panic。
    for name in ("init", "boot.sh"):
        data = open(os.path.join(INITRAMFS, name), "rb").read().replace(b"\r\n", b"\n")
        b.file("/" + name, data)

    raw, gz = b.finish(OUT)
    print("cpio=%d bytes, gz=%d bytes -> %s" % (raw, gz, OUT))


if __name__ == "__main__":
    main()
