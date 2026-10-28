package app.diary.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.os.Build
import java.io.File

/**
 * 把相册或相机得到的位图收成普通 sRGB ARGB_8888，再显示或压成 JPEG。
 * vivo 的 ImageDecoder / BitmapFactory 会给出硬件位图、RGBA_F16、Display P3 或增益图，
 * 这些交给 Compose 或 JPEG 编码时会变成黑块。
 */
internal object PhotoBitmaps {
    private val srgb: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

    fun decodeForView(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val decoded = BitmapFactory.decodeFile(file.absolutePath, viewOptions()) ?: return null
        return try {
            immutableCopy(bake(decoded))
        } catch (_: Throwable) {
            if (!decoded.isRecycled) runCatching { decoded.recycle() }
            null
        }
    }

    fun bake(source: Bitmap): Bitmap {
        if (source.isRecycled) throw PhotoImportException(PhotoImport.Failure.DECODE)
        val software = copyOffHardware(source)
        if (!PhotoImport.needsSrgbBake(software.config?.name, isSrgb(software), hasGainmap(software))) {
            return software
        }
        val width = software.width
        val height = software.height
        if (width <= 0 || height <= 0) {
            recycleQuietly(software)
            throw PhotoImportException(PhotoImport.Failure.DECODE)
        }
        val baked = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true, srgb)
        } catch (_: Throwable) {
            recycleQuietly(software)
            throw PhotoImportException(PhotoImport.Failure.DECODE)
        }
        try {
            Canvas(baked).drawBitmap(software, 0f, 0f, null)
        } catch (_: Throwable) {
            recycleQuietly(baked)
            recycleQuietly(software)
            throw PhotoImportException(PhotoImport.Failure.DECODE)
        }
        clearGainmap(baked)
        if (baked != software) recycleQuietly(software)
        return baked
    }

    private fun immutableCopy(bitmap: Bitmap): Bitmap {
        if (isPlain(bitmap) && !bitmap.isMutable) return bitmap
        val frozen = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        if (frozen == null || frozen == bitmap || !isPlain(frozen)) {
            if (frozen != null && frozen != bitmap) recycleQuietly(frozen)
            return bitmap
        }
        clearGainmap(frozen)
        recycleQuietly(bitmap)
        return frozen
    }

    private fun isPlain(bitmap: Bitmap): Boolean =
        bitmap.config == Bitmap.Config.ARGB_8888 && isSrgb(bitmap) && !hasGainmap(bitmap)

    private fun copyOffHardware(source: Bitmap): Bitmap {
        if (source.config != Bitmap.Config.HARDWARE) return source
        val copy = runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        recycleQuietly(source)
        return copy ?: throw PhotoImportException(PhotoImport.Failure.DECODE)
    }

    private fun isSrgb(bitmap: Bitmap): Boolean = bitmap.colorSpace == srgb

    private fun hasGainmap(bitmap: Bitmap): Boolean =
        Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()

    private fun clearGainmap(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()) {
            bitmap.setGainmap(null)
        }
    }

    private fun recycleQuietly(bitmap: Bitmap) {
        if (!bitmap.isRecycled) runCatching { bitmap.recycle() }
    }

    private fun viewOptions(): BitmapFactory.Options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        // 可变位图不能是 HARDWARE。vivo 会忽略 inPreferredConfig，仍返回硬件位图，Compose 画出来是黑的。
        inMutable = true
        inScaled = false
    }
}
