@file:Suppress("DEPRECATION")

package app.diary.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.diary.BuildConfig
import app.diary.domain.UpdateUrls
import app.diary.journal.JournalUrls
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

    /** 固定用构建时的清单地址。以前在设置里保存过的地址不再生效。 */
    fun updateUrl(): String = UpdateUrls.resolve(BuildConfig.UPDATE_MANIFEST_URL)

    fun setUpdateUrl(value: String) {
        val trimmed = value.trim()
        val editor = plain.edit()
        if (trimmed.isEmpty()) editor.remove(KEY_URL) else editor.putString(KEY_URL, trimmed)
        editor.apply()
    }

    /** 固定为日记本默认地址。以前保存过的自定义地址不再生效。 */
    fun journalBaseUrl(): String = JournalUrls.DEFAULT_BASE

    fun setJournalBaseUrl(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            plain.edit().remove(KEY_JOURNAL_URL).apply()
            return
        }
        val normalized = JournalUrls.normalizeBase(trimmed)
        plain.edit().putString(KEY_JOURNAL_URL, normalized).apply()
    }

    fun journalToken(): String = secure?.getString(KEY_JOURNAL_TOKEN, null)?.trim().orEmpty()

    fun hasJournalToken(): Boolean = journalToken().isNotEmpty()

    fun setJournalToken(value: String) {
        val prefs = secure ?: error("这台设备的加密存储不可用")
        val trimmed = value.trim()
        if (trimmed.isEmpty()) error("口令是空的")
        prefs.edit().putString(KEY_JOURNAL_TOKEN, trimmed).commit()
    }

    fun clearJournalToken() {
        secure?.edit()?.remove(KEY_JOURNAL_TOKEN)?.commit()
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
        private const val KEY_JOURNAL_URL = "journal_base_url"
        private const val KEY_JOURNAL_TOKEN = "journal_app_token"
    }
}
