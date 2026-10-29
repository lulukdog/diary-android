package app.diary.domain

object UpdateUrls {
    const val DEFAULT_MANIFEST =
        "https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json"

    fun resolve(baked: String?): String {
        val trimmed = baked?.trim().orEmpty()
        return if (trimmed.isEmpty()) DEFAULT_MANIFEST else trimmed
    }
}
