@file:Suppress("DEPRECATION")

package app.diary.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.diary.BuildConfig
import java.security.SecureRandom

class AppSettings(context: Context) {
    private val plain: SharedPreferences =
        context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? = openSecure(context)

    val secureAvailable: Boolean = secure != null

    fun resolveApiKey(): String {
        val stored = secure?.getString(KEY_API, null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        return BuildConfig.DEEPSEEK_API_KEY.trim()
    }

    fun hasUserKey(): Boolean = !secure?.getString(KEY_API, null).isNullOrBlank()

    fun hasBuildKey(): Boolean = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()

    fun setApiKey(value: String) {
        val prefs = secure ?: error("这台设备的加密存储不可用")
        prefs.edit().putString(KEY_API, value.trim()).commit()
    }

    fun clearApiKey() {
        secure?.edit()?.remove(KEY_API)?.commit()
    }

    /** 从未保存，或保存的是空字符串时，用构建时注入的地址（默认是本仓库的 version.json）。 */
    fun updateUrl(): String {
        val saved = plain.getString(KEY_URL, null)?.trim().orEmpty()
        if (saved.isEmpty()) return BuildConfig.UPDATE_MANIFEST_URL.trim()
        return saved
    }

    fun setUpdateUrl(value: String) {
        val trimmed = value.trim()
        val editor = plain.edit()
        if (trimmed.isEmpty()) editor.remove(KEY_URL) else editor.putString(KEY_URL, trimmed)
        editor.apply()
    }

    fun localApiEnabled(): Boolean = plain.getBoolean(KEY_LOCAL, false)

    fun setLocalApiEnabled(enabled: Boolean) {
        plain.edit().putBoolean(KEY_LOCAL, enabled).apply()
    }

    fun token(): String = secure?.getString(KEY_TOKEN, null).orEmpty()

    fun ensureToken(): String {
        val prefs = secure ?: error("这台设备的加密存储不可用")
        val existing = prefs.getString(KEY_TOKEN, null).orEmpty()
        if (existing.isNotBlank()) return existing
        val created = newToken()
        prefs.edit().putString(KEY_TOKEN, created).commit()
        return created
    }

    fun regenerateToken(): String {
        val prefs = secure ?: error("这台设备的加密存储不可用")
        val created = newToken()
        prefs.edit().putString(KEY_TOKEN, created).commit()
        return created
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    @Suppress("DEPRECATION")
    private fun openSecure(context: Context): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "diary_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val KEY_API = "deepseek_api_key"
        private const val KEY_TOKEN = "local_api_token"
        private const val KEY_URL = "update_manifest_url"
        private const val KEY_LOCAL = "local_api_enabled"
    }
}
