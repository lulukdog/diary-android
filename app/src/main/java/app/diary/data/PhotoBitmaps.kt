package app.diary.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

/**
 * 1.1.2 只看 config 和色域。vivo 上 `inMutable` 的 BitmapFactory 会交出一张
 * 自称 sRGB ARGB_8888、像素却是空的位图，压成 JPEG 再交给 Compose 就是黑块。
 * 这里用 getPixels 把像素抄进新的 ARGB_8888。抄出来是黑的就换下一种解码。
 */
internal object PhotoBitmaps {
    private class Tried(val bitmap: Bitmap?, val decoded: Boolean)

    fun decodeStored(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            decodeImported(file, thumbnail = null)
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeImported(file: File, thumbnail: Bitmap?): Bitmap {
        val kind = file.inputStream().use { PhotoImport.kindOf(PhotoImport.readPrefix(it)) }
        val degrees = readDegrees(file)
        var decoded = false
        var chosen: Bitmap? = null
        for (source in PhotoImport.sourceOrder(kind)) {
            val tried = when (source) {
                PhotoImport.Source.FACTORY -> decodeFactory(file, degrees)
                PhotoImport.Source.DECODER -> decodeWithImageDecoder(file)
                PhotoImport.Source.THUMBNAIL -> {
                    if (thumbnail == null || thumbnail.isRecycled) {
                        Tried(null, false)
                    } else {
                        Tried(scalePlain(thumbnail), true)
                    }
                }
            }
            decoded = decoded || tried.decoded
            if (tried.bitmap != null) {
                chosen = tried.bitmap
                break
            }
        }
        if (chosen != null) return chosen
        if (decoded) throw PhotoImportException(PhotoImport.Failure.BLANK, kind)
        throw PhotoImportException(PhotoImport.failureForDecode(kind), kind)
    }

    /**
     * 把位图的像素抄成普通 ARGB。硬件位图、空缓冲、几乎全黑都返回 null。
     * 调用方仍持有 [source]。
     */
    fun plainCopy(source: Bitmap): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            source
        }
        return try {
            val width = readable.width
            val height = readable.height
            val pixels = IntArray(width * height)
            readable.getPixels(pixels, 0, width, 0, 0, width, height)
            if (PhotoImport.isNearlyBlank(PhotoImport.sampleGrid(pixels, width, height))) {
                null
            } else {
                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            null
        } finally {
            if (readable != source) recycle(readable)
        }
    }

    fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }
    }

    private fun decodeFactory(file: File, degrees: Int): Tried {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodePath(file, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            decodeFd(file, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Tried(null, false)
        val sample = PhotoImport.sampleSize(bounds.outWidth, bounds.outHeight)
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val specs = listOf(
            Bitmap.Config.ARGB_8888 to null,
            Bitmap.Config.ARGB_8888 to srgb,
            Bitmap.Config.RGB_565 to null,
        )
        var decoded = false
        for ((config, colorSpace) in specs) {
            val pathRaw = decodePath(file, options(sample, config, colorSpace))
            if (pathRaw != null) {
                decoded = true
                val plain = finishPlain(pathRaw, degrees)
                if (plain != null) return Tried(plain, true)
            }
            val fdRaw = decodeFd(file, options(sample, config, colorSpace))
            if (fdRaw != null) {
                decoded = true
                val plain = finishPlain(fdRaw, degrees)
                if (plain != null) return Tried(plain, true)
            }
        }
        return Tried(null, decoded)
    }

    private fun decodeWithImageDecoder(file: File): Tried {
        if (Build.VERSION.SDK_INT < 28) return Tried(null, false)
        val bitmap = try {
            decodeImageDecoder(file)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            null
        } ?: return Tried(null, false)
        val plain = finishPlain(bitmap, degrees = 0)
        return Tried(plain, true)
    }

    @RequiresApi(28)
    private fun decodeImageDecoder(file: File): Bitmap {
        val source = ImageDecoder.createSource(file)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val sample = PhotoImport.sampleSize(info.size.width, info.size.height)
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
    }

    private fun finishPlain(raw: Bitmap, degrees: Int): Bitmap? {
        val copied = try {
            plainCopy(raw)
        } catch (error: OutOfMemoryError) {
            recycle(raw)
            throw error
        }
        recycle(raw)
        if (copied == null) return null
        return scalePlain(rotatePlain(copied, degrees))
    }

    private fun rotatePlain(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            return bitmap
        }
        if (rotated == bitmap) return bitmap
        val plain = try {
            plainCopy(rotated)
        } catch (error: OutOfMemoryError) {
            recycle(rotated)
            throw error
        }
        recycle(rotated)
        if (plain == null) return bitmap
        recycle(bitmap)
        return plain
    }

    private fun scalePlain(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= PhotoImport.MAX_SIDE || bitmap.isRecycled) return bitmap
        val scale = PhotoImport.MAX_SIDE.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            return bitmap
        }
        if (scaled == bitmap) return bitmap
        val plain = try {
            plainCopy(scaled)
        } catch (error: OutOfMemoryError) {
            recycle(scaled)
            throw error
        }
        recycle(scaled)
        if (plain == null) return bitmap
        recycle(bitmap)
        return plain
    }

    private fun readDegrees(file: File): Int {
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return PhotoImport.degrees(orientation)
    }

    private fun options(sample: Int, config: Bitmap.Config, colorSpace: ColorSpace?): BitmapFactory.Options {
        return BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
            inPreferredConfig = config
            if (colorSpace != null) inPreferredColorSpace = colorSpace
        }
    }

    private fun decodePath(file: File, options: BitmapFactory.Options): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeFd(file: File, options: BitmapFactory.Options): Bitmap? {
        return try {
            FileInputStream(file).use { input ->
                BitmapFactory.decodeFileDescriptor(input.fd, null, options)
            }
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }
}
