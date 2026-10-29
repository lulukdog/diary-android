package app.diary.journal

import app.diary.data.DiaryRepository
import app.diary.data.EntryWithPhotos
import app.diary.data.MirroredEntry
import app.diary.data.PendingPhoto
import app.diary.data.PhotoStore
import app.diary.settings.AppSettings

sealed class JournalLink {
    data object Ready : JournalLink()
    data object MissingToken : JournalLink()
    data object Rejected : JournalLink()
    data class Failed(val message: String) : JournalLink()
}

data class UploadReport(
    val uploaded: Int,
    val failed: Int,
    val error: String?,
)

sealed class WriteOutcome {
    data class Done(val day: String) : WriteOutcome()
    data class Partial(
        val day: String?,
        val error: JournalException,
        val remainingBody: String,
        val remainingPhotos: List<PendingPhoto>,
    ) : WriteOutcome()
}

class JournalRepository(
    private val settings: AppSettings,
    private val client: JournalClient,
    private val diary: DiaryRepository,
    private val photos: PhotoStore,
) {
    fun configured(): Boolean = settings.hasJournalToken()

    suspend fun check(): JournalLink {
        if (!settings.hasJournalToken()) return JournalLink.MissingToken
        return try {
            val ok = client.me(settings.journalBaseUrl(), settings.journalToken())
            if (ok) JournalLink.Ready else JournalLink.Rejected
        } catch (error: JournalException) {
            JournalLink.Failed(error.message ?: "没有连上日记本")
        }
    }

    suspend fun load(day: String): JournalDay {
        val token = requireToken()
        val base = settings.journalBaseUrl()
        val raw = client.state(base, token, day)
        val entries = raw.entries.map { entry ->
            val fileName = cacheMedia(base, token, entry)
            if (fileName == null) entry else entry.copy(localFileName = fileName)
        }
        val ready = raw.copy(entries = entries)
        diary.mirrorRemoteDay(
            day,
            entries.map { entry ->
                MirroredEntry(
                    remoteId = entry.id,
                    body = entry.content,
                    createdAt = journalMillis(entry.ts) ?: System.currentTimeMillis(),
                    photoFileName = entry.localFileName,
                    photoCaption = if (entry.kind == "image") entry.content else "",
                )
            },
        )
        return ready
    }

    suspend fun write(body: String, pending: List<PendingPhoto>): WriteOutcome {
        val token = requireToken()
        val base = settings.journalBaseUrl()
        var filed: String? = null
        var sentBody = false
        val sent = LinkedHashSet<String>()
        try {
            if (body.isNotBlank()) {
                filed = client.createText(base, token, body).day
                sentBody = true
            }
            for (photo in pending) {
                val bytes = photos.file(photo.fileName).readBytes()
                val data = imageDataUrl(bytes)
                filed = client.createImage(base, token, data, photo.caption).day
                sent += photo.fileName
            }
            val day = filed ?: throw JournalException("空的话就不记了。", code = 400)
            return WriteOutcome.Done(day)
        } catch (error: JournalException) {
            if (!sentBody && sent.isEmpty()) throw error
            return WriteOutcome.Partial(
                day = filed,
                error = error,
                remainingBody = if (sentBody) "" else body,
                remainingPhotos = pending.filter { it.fileName !in sent },
            )
        }
    }

    suspend fun uploadUnsynced(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): UploadReport {
        var uploaded = 0
        var failed = 0
        var error: String? = null
        val gaveUp = HashSet<Long>()
        repeat(8) {
            val pending = diary.unsyncedOriginals().filter { it.entry.id !in gaveUp }
            if (pending.isEmpty()) return UploadReport(uploaded, failed, error)
            val total = uploaded + failed + pending.size
            for ((index, row) in pending.withIndex()) {
                onProgress(uploaded + failed + index, total)
                try {
                    uploadOne(row)
                    uploaded++
                } catch (thrown: JournalException) {
                    failed++
                    error = thrown.message
                    gaveUp += row.entry.id
                    if (thrown.code == 401) {
                        onProgress(uploaded + failed, total)
                        return UploadReport(uploaded, failed, error)
                    }
                }
            }
            if (failed > 0) return UploadReport(uploaded, failed, error)
        }
        return UploadReport(uploaded, failed, error)
    }

    suspend fun ask(text: String): JournalReply {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw JournalException("你想问什么？先说一句。")
        return client.ask(settings.journalBaseUrl(), requireToken(), trimmed)
    }

    suspend fun answerOnboarding(key: String, answer: String?, skipped: Boolean) {
        client.answerOnboarding(settings.journalBaseUrl(), requireToken(), key, answer, skipped)
    }

    private suspend fun uploadOne(row: EntryWithPhotos) {
        val parts = uploadParts(
            row.entry.body,
            row.entry.textRemoteId,
            row.photos.map { photo -> photo.id to photo.remoteId },
        )
        if (parts.finished) {
            diary.markEntryUploaded(row.entry.id, row.entry.syncedServerDay ?: row.entry.dayKey)
            return
        }
        val token = requireToken()
        val base = settings.journalBaseUrl()
        var serverDay = row.entry.syncedServerDay
        if (parts.sendText) {
            val created = client.createText(base, token, row.entry.body)
            serverDay = created.day
            diary.markTextUploaded(row.entry.id, created.id, created.day)
        }
        val pendingIds = parts.pendingPhotoIds.toSet()
        for (photo in row.photos) {
            if (photo.id !in pendingIds) continue
            val file = photos.file(photo.fileName)
            if (!file.isFile || file.length() == 0L) {
                throw JournalException("有一张图片不在这台手机上了")
            }
            val created = client.createImage(base, token, imageDataUrl(file.readBytes()), photo.caption)
            serverDay = created.day
            diary.markPhotoUploaded(photo.id, created.id)
        }
        diary.markEntryUploaded(row.entry.id, serverDay ?: row.entry.dayKey)
    }

    private fun cacheMedia(base: String, token: String, entry: JournalEntry): String? {
        val path = entry.mediaUrl ?: entry.imagePath ?: return null
        val name = journalPhotoName(entry.id, entry.imagePath ?: path)
        val existing = photos.file(name)
        if (existing.exists() && existing.length() > 0L) return name
        val bytes = client.media(base, token, path)
        photos.writeBytes(name, bytes)
        return name
    }

    private fun requireToken(): String {
        val token = settings.journalToken()
        if (token.isBlank()) throw JournalException("先在设置里连接日记本")
        return token
    }
}
