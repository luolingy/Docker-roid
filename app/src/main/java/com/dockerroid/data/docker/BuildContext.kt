package com.dockerroid.data.docker

import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 极简构建上下文打包：把单个 Dockerfile 打成 tar（Docker /build 需要 tar 上下文）。
 * 仅支持"纯 Dockerfile 无额外文件"的场景（FROM/RUN/COPY 无本地源）。
 */
object BuildContext {

    /** 把 Dockerfile 内容打成 tar 文件，返回该 tar。 */
    fun tarOfDockerfile(dir: File, dockerfileContent: String): File {
        val out = File(dir, "context.tar")
        val name = "Dockerfile"
        val data = dockerfileContent.toByteArray(Charsets.UTF_8)

        FileOutputStream(out).use { fos ->
            // 文件头
            val header = ByteArray(512)
            writeAscii(header, 0, name, 100)
            writeOctal(header, 100, 420, 8)            // mode 0644
            writeOctal(header, 108, 0, 8)              // uid
            writeOctal(header, 116, 0, 8)              // gid
            writeOctal(header, 124, data.size.toLong(), 12)  // size
            writeOctal(header, 136, 0, 12)             // mtime
            repeat(8) { header[148 + it] = ' '.code.toByte() } // checksum 占位
            header[156] = '0'.code.toByte()            // typeflag regular
            writeAscii(header, 257, "ustar", 6)
            header[262] = 0
            writeAscii(header, 263, "00", 2)
            // 计算 checksum
            repeat(8) { header[148 + it] = ' '.code.toByte() }
            var sum = 0
            for (b in header) sum += (b.toInt() and 0xFF)
            val ck = String.format("%06o", sum) + "\u0000 "
            writeAscii(header, 148, ck, 8)
            fos.write(header)
            fos.write(data)
            // padding to 512
            val pad = (512 - data.size % 512) % 512
            if (pad > 0) fos.write(ByteArray(pad))
            // 结束块
            fos.write(ByteArray(1024))
        }
        return out
    }

    private fun writeAscii(buf: ByteArray, off: Int, s: String, len: Int) {
        val b = s.toByteArray(Charsets.US_ASCII)
        for (i in 0 until minOf(len, b.size)) buf[off + i] = b[i]
    }

    private fun writeOctal(buf: ByteArray, off: Int, v: Long, len: Int) {
        val s = v.toString(8).padStart(len - 1, '0') + "\u0000"
        writeAscii(buf, off, s, len)
    }
}
