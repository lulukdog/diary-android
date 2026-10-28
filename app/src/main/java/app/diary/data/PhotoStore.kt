package app.diary.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

class PhotoStore(context: Context) {
    private val directory: File = File(context.filesDir, "photos").apply { mkdirs() }
    private val scratch: File = File(context.cacheDir, "photo-import").apply { mkdirs() }

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

    fun writeBytes(name: String, bytes: ByteArray) {
        file(name).writeBytes(bytes)
    }

    /**
     * 在收到相册结果的这一刻打开。vivo 的授权有时只在这次回调里有效，
     * 不能等进了后台再重新 open。
     */
    fun open(context: Context, uri: Uri): InputStream {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return openStream(context.contentResolver, uri)
    }

    fun import(context: Context, uri: Uri): String {
        val stream = open(context, uri)
        stream.use { return importStream(it) }
    }

    fun importStream(stream: InputStream): String {
        val temp = File.createTempFile("import-", ".img", scratch)
        try {
            copyFully(stream, temp)
            val bitmap = try {
                decodeFile(temp)
            } catch (error: OutOfMemoryError) {
                throw PhotoImportException(PhotoImport.Failure.TOO_BIG)
            }
            return try {
                write(bitmap)
            } catch (error: OutOfMemoryError) {
                throw PhotoImportException(PhotoImport.Failure.TOO_BIG)
            }
        } finally {
            temp.delete()
        }
    }

    private fun copyFully(stream: InputStream, dest: File) {
        try {
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > PhotoImport.MAX_BYTES) {
                        throw PhotoImportException(PhotoImport.Failure.TOO_BIG)
                    }
                    output.write(buffer, 0, read)
                }
            }
        } catch (error: PhotoImportException) {
            throw error
        } catch (_: IOException) {
            if (dest.length() > 0L) {
                throw PhotoImportException(PhotoImport.Failure.INCOMPLETE)
            }
            throw PhotoImportException(PhotoImport.Failure.UNREADABLE)
        }
        if (dest.length() == 0L) {
            throw PhotoImportException(PhotoImport.Failure.UNREADABLE)
        }
    }

    private fun write(bitmap: Bitmap): String {
        val software = PhotoBitmaps.bake(bitmap)
        val name = UUID.randomUUID().toString() + ".jpg"
        try {
            FileOutputStream(file(name)).use { output ->
                if (!software.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw PhotoImportException(PhotoImport.Failure.SAVE)
                }
            }
            return name
        } catch (error: Exception) {
            file(name).delete()
            throw error
        } finally {
            software.recycle()
        }
    }

    private fun decodeFile(file: File): Bitmap {
        val kind = file.inputStream().use { PhotoImport.kindOf(PhotoImport.readPrefix(it)) }
        val decoded = if (PhotoImport.bitmapFactoryFirst(kind)) {
            decodeWithBitmapFactory(file) ?: decodeWithImageDecoderOrNull(file)
        } else {
            decodeWithImageDecoderOrNull(file) ?: decodeWithBitmapFactory(file)
        }
        if (decoded == null) {
            throw PhotoImportException(PhotoImport.failureForDecode(kind), kind)
        }
        return decoded
    }

    private fun decodeWithImageDecoderOrNull(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT < 28) return null
        return runCatching { decodeWithImageDecoder(file) }.getOrNull()
    }

    @RequiresApi(28)
    private fun decodeWithImageDecoder(file: File): Bitmap {
        val source = ImageDecoder.createSource(file)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            if (width <= 0 || height <= 0) return@decodeBitmap
            val (targetWidth, targetHeight) = PhotoImport.targetSize(width, height)
            if (targetWidth != width || targetHeight != height) {
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }
        return prepare(bitmap, degrees = 0)
    }

    private fun decodeWithBitmapFactory(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { input ->
            BitmapFactory.decodeFileDescriptor(input.fd, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoImport.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val decoded = FileInputStream(file).use { input ->
            BitmapFactory.decodeFileDescriptor(input.fd, null, options)
        } ?: return null
        return prepare(decoded, readDegrees(file))
    }

    private fun prepare(bitmap: Bitmap, degrees: Int): Bitmap {
        val baked = PhotoBitmaps.bake(bitmap)
        return PhotoBitmaps.bake(scaleDown(rotate(baked, degrees)))
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

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height).toFloat()
        if (longest <= PhotoImport.MAX_SIDE) return source
        val scale = PhotoImport.MAX_SIDE / longest
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != source) source.recycle()
        return scaled
    }

    private fun openStream(resolver: ContentResolver, uri: Uri): InputStream {
        var denied = false
        fun attempt(block: () -> InputStream?): InputStream? {
            return try {
                block()
            } catch (_: SecurityException) {
                denied = true
                null
            } catch (_: Exception) {
                null
            }
        }

        attempt { resolver.openInputStream(uri) }?.let { return it }
        attempt { typedStream(resolver, uri) }?.let { return it }
        attempt { assetStream(resolver, uri) }?.let { return it }
        attempt { descriptorStream(resolver, uri) }?.let { return it }
        if (denied) throw PhotoImportException(PhotoImport.Failure.PERMISSION)
        throw PhotoImportException(PhotoImport.Failure.UNREADABLE)
    }

    private fun typedStream(resolver: ContentResolver, uri: Uri): InputStream? {
        val descriptor = resolver.openTypedAssetFileDescriptor(uri, "image/*", null) ?: return null
        return try {
            descriptor.createInputStream()
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    private fun assetStream(resolver: ContentResolver, uri: Uri): InputStream? {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: return null
        return try {
            descriptor.createInputStream()
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    private fun descriptorStream(resolver: ContentResolver, uri: Uri): InputStream? {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return null
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    }
}
