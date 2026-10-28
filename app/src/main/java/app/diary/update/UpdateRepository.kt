package app.diary.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.diary.domain.RemoteRelease
import app.diary.domain.UpdateDecision
import app.diary.domain.VersionManifest
import app.diary.settings.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class UpdateCheck {
    data object NoUrl : UpdateCheck()
    data object UpToDate : UpdateCheck()
    data class Available(val release: RemoteRelease) : UpdateCheck()
    data class Failed(val message: String) : UpdateCheck()
}

class UpdateRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: AppSettings,
    private val localVersionCode: Int,
) {
    fun check(): UpdateCheck {
        val url = settings.updateUrl()
        if (url.isBlank()) return UpdateCheck.NoUrl
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return UpdateCheck.Failed("更新地址需要以 http:// 或 https:// 开头")
        }
        return try {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return UpdateCheck.Failed("更新清单返回 ${response.code}")
                }
                val remote = try {
                    VersionManifest.parse(body)
                } catch (error: IllegalArgumentException) {
                    return UpdateCheck.Failed(error.message ?: "更新清单无法解析")
                }
                when (val decision = VersionManifest.decide(remote, localVersionCode)) {
                    UpdateDecision.UpToDate -> UpdateCheck.UpToDate
                    is UpdateDecision.Available -> UpdateCheck.Available(decision.release)
                    is UpdateDecision.Invalid -> UpdateCheck.Failed(decision.reason)
                }
            }
        } catch (_: Exception) {
            UpdateCheck.Failed("没有拿到更新清单")
        }
    }

    fun download(release: RemoteRelease, onProgress: (Int) -> Unit): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val destination = File(directory, "diary-update.apk")
        if (destination.exists()) destination.delete()
        val request = Request.Builder().url(release.apkUrl).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("安装包下载失败（${response.code}）")
                val body = response.body ?: throw IOException("安装包是空的")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var readTotal = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            readTotal += count
                            if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw if (error is IOException) error else IOException("安装包下载失败")
        }
        val header = ByteArray(2)
        destination.inputStream().use { input ->
            val read = input.read(header)
            if (read < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                destination.delete()
                throw IOException("下载到的文件不是 APK")
            }
        }
        onProgress(100)
        return destination
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("diary.apk", uri)
        }
        context.startActivity(intent)
    }
}
