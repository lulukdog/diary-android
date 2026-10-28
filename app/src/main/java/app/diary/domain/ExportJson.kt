package app.diary.domain

data class ExportDocument(
    val exportedAt: String,
    val photosIncluded: Boolean,
    val versionName: String,
    val versionCode: Int,
    val days: List<ExportDay>,
)

data class ExportDay(
    val day: String,
    val entries: List<ExportEntry>,
    val summary: ExportSummary?,
)

data class ExportEntry(
    val id: Long,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
    val photos: List<ExportPhoto>,
)

data class ExportPhoto(
    val id: Long,
    val fileName: String,
    val caption: String,
    val createdAt: String,
)

data class ExportSummary(
    val text: String,
    val generatedAt: String,
    val sourceHash: String,
)

fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

fun ExportDocument.toJson(): String = buildString {
    append("{\n")
    append("  \"schema\": \"diary.export.v1\",\n")
    append("  \"exportedAt\": ${jsonString(exportedAt)},\n")
    append("  \"timezone\": \"Asia/Shanghai\",\n")
    append("  \"photosIncluded\": $photosIncluded,\n")
    append("  \"app\": {")
    append("\"id\": \"app.diary\", ")
    append("\"versionName\": ${jsonString(versionName)}, ")
    append("\"versionCode\": $versionCode")
    append("},\n")
    append("  \"days\": [\n")
    days.forEachIndexed { index, day ->
        append(day.toJson(indent = "    "))
        if (index != days.lastIndex) append(',')
        append('\n')
    }
    append("  ]\n")
    append("}\n")
}

fun ExportDay.toJson(indent: String = "  "): String {
    val inner = "$indent  "
    return buildString {
        append(indent).append("{\n")
        append(inner).append("\"day\": ").append(jsonString(day)).append(",\n")
        append(inner).append("\"entries\": [")
        if (entries.isEmpty()) {
            append("],\n")
        } else {
            append('\n')
            entries.forEachIndexed { index, entry ->
                append(entry.toJson("$inner  "))
                if (index != entries.lastIndex) append(',')
                append('\n')
            }
            append(inner).append("],\n")
        }
        append(inner).append("\"summary\": ")
        if (summary == null) {
            append("null\n")
        } else {
            append(summary.toJson()).append('\n')
        }
        append(indent).append('}')
    }
}

private fun ExportEntry.toJson(indent: String): String {
    val inner = "$indent  "
    return buildString {
        append(indent).append("{\n")
        append(inner).append("\"id\": ").append(id).append(",\n")
        append(inner).append("\"body\": ").append(jsonString(body)).append(",\n")
        append(inner).append("\"createdAt\": ").append(jsonString(createdAt)).append(",\n")
        append(inner).append("\"updatedAt\": ").append(jsonString(updatedAt)).append(",\n")
        append(inner).append("\"photos\": [")
        if (photos.isEmpty()) {
            append("]\n")
        } else {
            append('\n')
            photos.forEachIndexed { index, photo ->
                append(photo.toJson("$inner  "))
                if (index != photos.lastIndex) append(',')
                append('\n')
            }
            append(inner).append("]\n")
        }
        append(indent).append('}')
    }
}

private fun ExportPhoto.toJson(indent: String): String {
    val inner = "$indent  "
    return buildString {
        append(indent).append("{\n")
        append(inner).append("\"id\": ").append(id).append(",\n")
        append(inner).append("\"fileName\": ").append(jsonString(fileName)).append(",\n")
        append(inner).append("\"caption\": ").append(jsonString(caption)).append(",\n")
        append(inner).append("\"createdAt\": ").append(jsonString(createdAt)).append('\n')
        append(indent).append('}')
    }
}

private fun ExportSummary.toJson(): String = buildString {
    append('{')
    append("\"text\": ").append(jsonString(text)).append(", ")
    append("\"generatedAt\": ").append(jsonString(generatedAt)).append(", ")
    append("\"sourceHash\": ").append(jsonString(sourceHash))
    append('}')
}
