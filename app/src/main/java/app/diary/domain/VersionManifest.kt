package app.diary.domain

data class RemoteRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
)

sealed class UpdateDecision {
    data object UpToDate : UpdateDecision()
    data class Available(val release: RemoteRelease) : UpdateDecision()
    data class Invalid(val reason: String) : UpdateDecision()
}

object VersionManifest {
    fun parse(json: String): RemoteRelease {
        val text = json.trim().removePrefix("\uFEFF")
        return RemoteRelease(
            versionCode = intField(text, "versionCode"),
            versionName = stringField(text, "versionName"),
            apkUrl = stringField(text, "apkUrl"),
            changelog = stringFieldOrEmpty(text, "changelog"),
        )
    }

    fun decide(remote: RemoteRelease, localVersionCode: Int): UpdateDecision {
        val url = remote.apkUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return UpdateDecision.Invalid("更新清单里的 apkUrl 需要是 http 或 https 地址")
        }
        if (remote.versionCode > localVersionCode) {
            return UpdateDecision.Available(remote.copy(apkUrl = url))
        }
        return UpdateDecision.UpToDate
    }

    private fun intField(json: String, key: String): Int {
        val match = Regex(""""$key"\s*:\s*(\d+)""").find(json)
            ?: throw IllegalArgumentException("更新清单缺少 $key")
        return match.groupValues[1].toInt()
    }

    private fun stringField(json: String, key: String): String {
        val match = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)
            ?: throw IllegalArgumentException("更新清单缺少 $key")
        return unescape(match.groupValues[1])
    }

    private fun stringFieldOrEmpty(json: String, key: String): String =
        runCatching { stringField(json, key) }.getOrDefault("")

    private fun unescape(raw: String): String = buildString {
        var index = 0
        while (index < raw.length) {
            val current = raw[index]
            if (current != '\\' || index + 1 >= raw.length) {
                append(current)
                index += 1
                continue
            }
            when (val next = raw[index + 1]) {
                '\\', '"' -> {
                    append(next)
                    index += 2
                }
                'n' -> {
                    append('\n')
                    index += 2
                }
                'r' -> {
                    append('\r')
                    index += 2
                }
                't' -> {
                    append('\t')
                    index += 2
                }
                'u' -> {
                    if (index + 6 <= raw.length) {
                        val hex = raw.substring(index + 2, index + 6)
                        append(hex.toInt(16).toChar())
                        index += 6
                    } else {
                        append(next)
                        index += 2
                    }
                }
                else -> {
                    append(next)
                    index += 2
                }
            }
        }
    }
}
