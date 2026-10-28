package app.diary.data

/**
 * 相册导入里与 Android 图形栈无关的判断：文件头、采样和给用户看的原因。
 * vivo 相册经常给出不可 seek 的 content URI，BitmapFactory 只能 mark 1024 字节，
 * 手机 JPEG 的 EXIF 和 HEIC 都会因此解不出来。先看文件头，再决定失败时怎么说。
 */
internal object PhotoImport {
    const val MAX_SIDE = 1600
    const val MAX_BYTES = 80L * 1024 * 1024

    enum class Kind { JPEG, PNG, WEBP, GIF, HEIF, UNKNOWN }

    enum class Failure {
        PERMISSION,
        UNREADABLE,
        INCOMPLETE,
        UNSUPPORTED,
        DECODE,
        TOO_BIG,
        SAVE,
    }

    fun kindOf(header: ByteArray): Kind {
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return Kind.JPEG
        }
        if (header.size >= 4 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return Kind.PNG
        }
        if (header.size >= 6 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte()
        ) {
            return Kind.GIF
        }
        if (header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return Kind.WEBP
        }
        if (isHeif(header)) return Kind.HEIF
        return Kind.UNKNOWN
    }

    fun readPrefix(input: java.io.InputStream, count: Int = 32): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == count) buffer else buffer.copyOf(offset)
    }

    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        if (width <= 0 || height <= 0) return sample
        while (width / sample > maxSide * 2 || height / sample > maxSide * 2) {
            val next = sample shl 1
            if (next <= sample) break
            sample = next
        }
        return sample
    }

    fun targetSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest.toDouble()
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** ExifInterface 的方向常量。只旋转，不处理镜像，和原先的相册方向一致。 */
    fun degrees(orientation: Int): Int = when (orientation) {
        6, 5 -> 90
        3, 4 -> 180
        8, 7 -> 270
        else -> 0
    }

    fun failureForDecode(kind: Kind): Failure = when (kind) {
        Kind.JPEG, Kind.PNG, Kind.WEBP, Kind.GIF -> Failure.DECODE
        Kind.HEIF, Kind.UNKNOWN -> Failure.UNSUPPORTED
    }

    /**
     * 文件已经完整落在本地。JPEG、PNG、WebP、GIF 先用 BitmapFactory。
     * vivo（API 28+）上 ImageDecoder 的软件位图经常是 RGBA_F16、Display P3 或带增益图，
     * 再压成 JPEG 会变成黑图。HEIC 只能交给 ImageDecoder。
     */
    fun bitmapFactoryFirst(kind: Kind): Boolean = kind != Kind.HEIF

    /**
     * 只有普通的 sRGB ARGB_8888、且没有增益图，才能直接交给 JPEG 编码和 Compose。
     * 硬件位图、RGBA_F16、广色域、Ultra HDR 增益图在 vivo 上会画成黑块。
     */
    fun needsSrgbBake(configName: String?, isSrgb: Boolean, hasGainmap: Boolean): Boolean {
        if (hasGainmap) return true
        if (configName != "ARGB_8888") return true
        if (!isSrgb) return true
        return false
    }

    fun message(failure: Failure, kind: Kind = Kind.UNKNOWN): String = when (failure) {
        Failure.PERMISSION -> "没有读取这张图片的权限"
        Failure.UNREADABLE -> "这张图片没有读出来"
        Failure.INCOMPLETE -> "这张图片没有读完。如果它在云端，先在相册里打开一次再试。"
        Failure.TOO_BIG -> "图片太大，这台设备内存不够"
        Failure.SAVE -> "图片没有保存成功"
        Failure.DECODE -> "这张图片没有读出来"
        Failure.UNSUPPORTED -> when (kind) {
            Kind.HEIF -> "这张是 HEIC，这台设备解不了"
            else -> "这种图片格式暂时读不了"
        }
    }

    private fun isHeif(header: ByteArray): Boolean {
        if (header.size < 12) return false
        if (header[4] != 'f'.code.toByte() ||
            header[5] != 't'.code.toByte() ||
            header[6] != 'y'.code.toByte() ||
            header[7] != 'p'.code.toByte()
        ) {
            return false
        }
        val major = brandAt(header, 8) ?: return false
        if (major == "avif" || major == "avis") return false
        if (major in HEIF_BRANDS) return true
        var offset = 16
        while (offset + 4 <= header.size) {
            val brand = brandAt(header, offset) ?: break
            if (brand in HEIF_COMPAT) return true
            offset += 4
        }
        return false
    }

    private fun brandAt(header: ByteArray, offset: Int): String? {
        if (offset + 4 > header.size) return null
        return String(header, offset, 4, Charsets.ISO_8859_1).lowercase()
    }

    private val HEIF_BRANDS = setOf(
        "heic", "heix", "hevc", "hevx", "heim", "heis", "heif", "mif1", "msf1",
    )
    private val HEIF_COMPAT = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "heif")
}

internal class PhotoImportException(
    val failure: PhotoImport.Failure,
    val kind: PhotoImport.Kind = PhotoImport.Kind.UNKNOWN,
) : java.io.IOException(PhotoImport.message(failure, kind))
