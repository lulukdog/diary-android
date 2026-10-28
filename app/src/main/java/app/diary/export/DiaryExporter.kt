package app.diary.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.diary.data.DiaryRepository
import app.diary.data.PhotoStore
import app.diary.domain.toJson
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiaryExporter(
    private val context: Context,
    private val repository: DiaryRepository,
    private val photos: PhotoStore,
) {
    suspend fun exportZip(): File {
        val document = repository.exportDocument(photosIncluded = true)
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(directory, "diary-export.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("diary-export.json"))
            zip.write(document.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val names = document.days
                .flatMap { day -> day.entries }
                .flatMap { entry -> entry.photos }
                .map { photo -> photo.fileName }
                .distinct()
            names.forEach { name ->
                val file = runCatching { photos.file(name) }.getOrNull() ?: return@forEach
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry("photos/$name"))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }

    fun share(zipFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "日记导出")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("diary-export.zip", uri)
        }
        val chooser = Intent.createChooser(send, "导出日记").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
