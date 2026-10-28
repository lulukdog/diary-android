package app.diary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

class PhotoStore(context: Context) {
    private val directory: File = File(context.filesDir, "photos").apply { mkdirs() }

    init {
        File(directory, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    fun file(name: String): File {
        require(name.isNotBlank())
        require(!name.contains('/') && !name.contains('\\') && name != "." && name != "..") {
            "非法的图片名"
        }
        return File(directory, name)
    }

    fun listFiles(): List<File> = directory.listFiles()?.toList().orEmpty()

    fun delete(name: String) {
        runCatching { file(name).delete() }
    }

    fun import(context: Context, uri: Uri): String {
        try {
            val bitmap = decode(context, uri)
            return write(bitmap)
        } catch (error: OutOfMemoryError) {
            throw IOException("图片太大，这台设备内存不够")
        }
    }

    private fun write(bitmap: Bitmap): String {
        val name = UUID.randomUUID().toString() + ".jpg"
        FileOutputStream(file(name)).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                throw IOException("图片没有保存成功")
            }
        }
        bitmap.recycle()
        return name
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw IOException("这张图片没有读出来")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("这张图片没有读出来")
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("这张图片没有读出来")
        val oriented = rotate(decoded, readDegrees(context, uri))
        val longest = max(oriented.width, oriented.height).toFloat()
        if (longest <= MAX_SIDE) return oriented
        val scale = MAX_SIDE / longest
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != oriented) oriented.recycle()
        return scaled
    }

    private fun readDegrees(context: Context, uri: Uri): Int {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }

    companion object {
        const val MAX_SIDE = 1600
    }
}
