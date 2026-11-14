#!/usr/bin/env python3
"""构造 newc 格式 cpio（gzip 压缩），用于 QEMU -initrd。"""
import io
import os
import stat
import sys
import time
import gzip


def cpio_newc_entry(name: str, data: bytes, mode: int, ino: int) -> bytes:
    namebytes = name.encode() + b"\0"
    header = b"070701"
    fields = [
        ino,            # ino
        mode,           # mode
        0,              # uid
        0,              # gid
        1,              # nlink
        int(time.time()),  # mtime
        len(data),      # filesize
        0,              # devmajor
        0,              # devminor
        0,              # rdevmajor
        0,              # rdevminor
        len(namebytes),  # namesize
        0,              # check
    ]
    for f in fields:
        header += b"%08X" % f
    out = header + namebytes
    # 头部 + 文件名补齐到 4 字节
    pad = (-len(out)) % 4
    out += b"\0" * pad
    out += data
    pad = (-len(data)) % 4
    out += b"\0" * pad
    return out


def build(tree, out_path):
    """tree: [(name, data, mode)]，name 以 '/' 开头。"""
    buf = io.BytesIO()
    ino = 1
    for name, data, mode in tree:
        buf.write(cpio_newc_entry(name, data, mode, ino))
        ino += 1
    # trailer
    buf.write(cpio_newc_entry("TRAILER!!!", b"", 0, 0))
    raw = buf.getvalue()
    with gzip.GzipFile(filename="", mode="wb", fileobj=open(out_path, "wb"), mtime=0) as gz:
        gz.write(raw)
    return len(raw)


if __name__ == "__main__":
    # 简单自测：只放一个 /init
    data = b"#!/bin/sh\necho hello\n"
    build([("/init", data, 0o100755)], "test-initrd.cpio.gz")
    print("ok")
