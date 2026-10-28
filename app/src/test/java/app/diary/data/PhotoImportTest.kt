package app.diary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoImportTest {
    @Test
    fun recognizesCommonAlbumFormats() {
        assertEquals(PhotoImport.Kind.JPEG, PhotoImport.kindOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())))
        assertEquals(
            PhotoImport.Kind.PNG,
            PhotoImport.kindOf(
                byteArrayOf(
                    0x89.toByte(),
                    0x50.toByte(),
                    0x4E.toByte(),
                    0x47.toByte(),
                    0x0D.toByte(),
                    0x0A.toByte(),
                    0x1A.toByte(),
                    0x0A.toByte(),
                ),
            ),
        )
        assertEquals(PhotoImport.Kind.GIF, PhotoImport.kindOf("GIF89a".encodeToByteArray()))
        assertEquals(PhotoImport.Kind.WEBP, PhotoImport.kindOf(riff("WEBP")))
        assertEquals(PhotoImport.Kind.HEIF, PhotoImport.kindOf(ftyp("heic")))
        assertEquals(PhotoImport.Kind.HEIF, PhotoImport.kindOf(ftyp("mif1", "heic")))
        assertEquals(PhotoImport.Kind.UNKNOWN, PhotoImport.kindOf(ftyp("avif")))
        assertEquals(PhotoImport.Kind.UNKNOWN, PhotoImport.kindOf(byteArrayOf(0x00, 0x01)))
    }

    @Test
    fun sampleSizeMatchesPreviousBoundsLoop() {
        assertEquals(1, PhotoImport.sampleSize(1600, 1200))
        assertEquals(2, PhotoImport.sampleSize(4000, 3000))
        assertEquals(4, PhotoImport.sampleSize(10000, 8000))
        assertEquals(1, PhotoImport.sampleSize(0, 100))
    }

    @Test
    fun targetSizeKeepsLongestSideAtMost1600() {
        assertEquals(1600 to 1200, PhotoImport.targetSize(3200, 2400))
        assertEquals(800 to 600, PhotoImport.targetSize(800, 600))
        assertEquals(1 to 1, PhotoImport.targetSize(0, 0))
    }

    @Test
    fun exifDegreesFollowPreviousMapping() {
        assertEquals(90, PhotoImport.degrees(6))
        assertEquals(90, PhotoImport.degrees(5))
        assertEquals(180, PhotoImport.degrees(3))
        assertEquals(180, PhotoImport.degrees(4))
        assertEquals(270, PhotoImport.degrees(8))
        assertEquals(270, PhotoImport.degrees(7))
        assertEquals(0, PhotoImport.degrees(1))
    }

    @Test
    fun jpegTriesBitmapFactoryBeforeThumbnailAndBlankPixelsAreRejected() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x00)
        assertEquals(PhotoImport.Kind.JPEG, PhotoImport.kindOf(jpeg))
        assertEquals(
            listOf(PhotoImport.Source.FACTORY, PhotoImport.Source.DECODER, PhotoImport.Source.THUMBNAIL),
            PhotoImport.sourceOrder(PhotoImport.Kind.JPEG),
        )
        assertEquals(PhotoImport.Source.DECODER, PhotoImport.sourceOrder(PhotoImport.Kind.HEIF).first())
        assertEquals(PhotoImport.Source.THUMBNAIL, PhotoImport.sourceOrder(PhotoImport.Kind.PNG).last())
        assertTrue(PhotoImport.isNearlyBlank(IntArray(0)))
        assertTrue(PhotoImport.isNearlyBlank(intArrayOf(0xFF000000.toInt(), 0xFF010101.toInt())))
        assertFalse(PhotoImport.isNearlyBlank(intArrayOf(0xFF000000.toInt(), 0xFF090909.toInt())))
        val pixels = IntArray(8 * 8)
        pixels[4 * 8 + 4] = 0xFFFF0000.toInt()
        assertFalse(PhotoImport.isNearlyBlank(PhotoImport.sampleGrid(pixels, 8, 8)))
        pixels.fill(0xFF000000.toInt())
        assertTrue(PhotoImport.isNearlyBlank(PhotoImport.sampleGrid(pixels, 8, 8)))
        assertTrue(PhotoImport.sampleGrid(IntArray(3), 8, 8).isEmpty())
    }

    @Test
    fun messagesDistinguishPermissionFormatAndCloud() {
        assertEquals("没有读取这张图片的权限", PhotoImport.message(PhotoImport.Failure.PERMISSION))
        assertEquals("这张图片没有读出来", PhotoImport.message(PhotoImport.Failure.UNREADABLE))
        assertEquals("这张图片没有读出来", PhotoImport.message(PhotoImport.Failure.DECODE))
        assertEquals(
            "这张图片没有读完。如果它在云端，先在相册里打开一次再试。",
            PhotoImport.message(PhotoImport.Failure.INCOMPLETE),
        )
        assertEquals(
            "这张是 HEIC，这台设备解不了",
            PhotoImport.message(PhotoImport.Failure.UNSUPPORTED, PhotoImport.Kind.HEIF),
        )
        assertEquals(
            "这种图片格式暂时读不了",
            PhotoImport.message(PhotoImport.Failure.UNSUPPORTED, PhotoImport.Kind.UNKNOWN),
        )
        assertEquals(PhotoImport.Failure.UNSUPPORTED, PhotoImport.failureForDecode(PhotoImport.Kind.HEIF))
        assertEquals(PhotoImport.Failure.DECODE, PhotoImport.failureForDecode(PhotoImport.Kind.JPEG))
        assertEquals("图片太大，这台设备内存不够", PhotoImport.message(PhotoImport.Failure.TOO_BIG))
        assertEquals(
            "这张图片读出来是黑的，请换一张或重试",
            PhotoImport.message(PhotoImport.Failure.BLANK),
        )
    }

    private fun riff(kind: String): ByteArray {
        val bytes = ByteArray(12)
        "RIFF".encodeToByteArray().copyInto(bytes, 0)
        kind.encodeToByteArray().copyInto(bytes, 8)
        return bytes
    }

    private fun ftyp(major: String, compat: String? = null): ByteArray {
        val bytes = ByteArray(32)
        "ftyp".encodeToByteArray().copyInto(bytes, 4)
        major.encodeToByteArray().copyInto(bytes, 8)
        compat?.encodeToByteArray()?.copyInto(bytes, 16)
        return bytes
    }
}
