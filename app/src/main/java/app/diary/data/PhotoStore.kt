package app.diary.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

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
    fun open(context: Context, uri: Uri): OpenedPhoto {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val thumbnail = readThumbnail(context, uri)
        try {
            return OpenedPhoto(openStream(context.contentResolver, uri), thumbnail)
        } catch (error: Throwable) {
            PhotoBitmaps.recycle(thumbnail)
            throw error
        }
    }

    fun import(context: Context, uri: Uri): String {
        val opened = open(context, uri)
        opened.stream.use { return importStream(it, opened.thumbnail) }
    }

    fun importStream(stream: InputStream, thumbnail: Bitmap? = null): String {
        val temp = File.createTempFile("import-", ".img", scratch)
        try {
            copyFully(stream, temp)
            val bitmap = try {
                PhotoBitmaps.decodeImported(temp, thumbnail)
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
            PhotoBitmaps.recycle(thumbnail)
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
        val name = UUID.randomUUID().toString() + ".jpg"
        val saved = file(name)
        try {
            FileOutputStream(saved).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw PhotoImportException(PhotoImport.Failure.SAVE)
                }
            }
            val check = PhotoBitmaps.decodeStored(saved)
            if (check == null) {
                saved.delete()
                throw PhotoImportException(PhotoImport.Failure.BLANK)
            }
            PhotoBitmaps.recycle(check)
            return name
        } catch (error: Exception) {
            saved.delete()
            throw error
        } finally {
            PhotoBitmaps.recycle(bitmap)
        }
    }

    private fun readThumbnail(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        val raw = try {
            context.contentResolver.loadThumbnail(uri, Size(PhotoImport.MAX_SIDE, PhotoImport.MAX_SIDE), null)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (_: Throwable) {
            return null
        }
        val plain = try {
            PhotoBitmaps.plainCopy(raw)
        } catch (error: OutOfMemoryError) {
            PhotoBitmaps.recycle(raw)
            throw error
        }
        if (plain !== raw) PhotoBitmaps.recycle(raw)
        return plain
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

class OpenedPhoto(
    val stream: InputStream,
    val thumbnail: Bitmap?,
)
