package app.diary.data

import app.diary.domain.DayClock
import app.diary.domain.ExportDay
import app.diary.domain.ExportDocument
import app.diary.domain.ExportEntry
import app.diary.domain.ExportPhoto
import app.diary.domain.ExportSummary
import app.diary.domain.SummarySourceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiaryRepository(
    private val dao: DiaryDao,
    private val photos: PhotoStore,
    private val versionName: String,
    private val versionCode: Int,
) {
    fun observeDay(dayKey: String): Flow<List<EntryWithPhotos>> = dao.observeDay(dayKey)

    fun observeMarkedDays(): Flow<Set<String>> =
        dao.observeDayKeys().map { keys -> keys.toSet() }

    fun observeSummary(dayKey: String): Flow<DaySummaryEntity?> =
        dao.observeSummary(dayKey).map { rows -> rows.firstOrNull() }

    suspend fun entry(id: Long): EntryWithPhotos? = dao.entryWithPhotos(id)

    suspend fun entriesForDay(dayKey: String): List<EntryWithPhotos> = dao.entriesWithPhotos(dayKey)

    suspend fun addEntry(dayKey: String, body: String, pending: List<PendingPhoto>): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertEntry(
            DiaryEntry(
                dayKey = dayKey,
                body = body.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (pending.isNotEmpty()) {
            dao.insertPhotos(
                pending.map { photo ->
                    DiaryPhoto(
                        entryId = id,
                        fileName = photo.fileName,
                        caption = photo.caption.trim(),
                        createdAt = now,
                    )
                },
            )
        }
        return id
    }

    suspend fun updateEntry(id: Long, body: String, pending: List<PendingPhoto>) {
        val current = dao.entryWithPhotos(id) ?: error("找不到这则日记")
        val now = System.currentTimeMillis()
        dao.updateEntry(current.entry.copy(body = body.trim(), updatedAt = now))
        val keep = pending.mapNotNull { it.existingId }.toSet()
        val removed = current.photos.filter { it.id !in keep }
        if (removed.isNotEmpty()) {
            dao.deletePhotosByIds(removed.map { it.id })
            removed.forEach { photos.delete(it.fileName) }
        }
        pending.filter { it.existingId != null }.forEach { photo ->
            dao.updateCaption(photo.existingId!!, photo.caption.trim())
        }
        val fresh = pending.filter { it.existingId == null }
        if (fresh.isNotEmpty()) {
            dao.insertPhotos(
                fresh.map { photo ->
                    DiaryPhoto(
                        entryId = id,
                        fileName = photo.fileName,
                        caption = photo.caption.trim(),
                        createdAt = now,
                    )
                },
            )
        }
    }

    suspend fun mirrorRemoteDay(day: String, incoming: List<MirroredEntry>) {
        val current = dao.entriesWithPhotos(day)
        val keep = incoming.map { it.remoteId }.toSet()
        for (row in current) {
            if (!row.entry.cached) continue
            val remote = row.entry.remoteId ?: continue
            if (remote !in keep) dropCached(row)
        }
        val byRemote = current
            .filter { it.entry.cached && it.entry.remoteId != null && it.entry.remoteId in keep }
            .associateBy { it.entry.remoteId }
        for (item in incoming) {
            val existing = byRemote[item.remoteId]
            if (existing == null) {
                val id = dao.insertEntry(
                    DiaryEntry(
                        dayKey = day,
                        body = item.body,
                        createdAt = item.createdAt,
                        updatedAt = item.createdAt,
                        remoteId = item.remoteId,
                        cached = true,
                    ),
                )
                if (item.photoFileName != null) {
                    dao.insertPhotos(
                        listOf(
                            DiaryPhoto(
                                entryId = id,
                                fileName = item.photoFileName,
                                caption = item.photoCaption,
                                createdAt = item.createdAt,
                            ),
                        ),
                    )
                }
                continue
            }
            dao.updateEntry(
                existing.entry.copy(
                    body = item.body,
                    dayKey = day,
                    updatedAt = item.createdAt,
                    remoteId = item.remoteId,
                    cached = true,
                ),
            )
            val photo = existing.photos.firstOrNull()
            if (item.photoFileName == null) {
                if (existing.photos.isNotEmpty()) {
                    dao.deletePhotosByIds(existing.photos.map { it.id })
                    existing.photos.forEach { photos.delete(it.fileName) }
                }
            } else if (photo == null) {
                dao.insertPhotos(
                    listOf(
                        DiaryPhoto(
                            entryId = existing.entry.id,
                            fileName = item.photoFileName,
                            caption = item.photoCaption,
                            createdAt = item.createdAt,
                        ),
                    ),
                )
            } else if (photo.fileName != item.photoFileName || photo.caption != item.photoCaption) {
                if (photo.fileName != item.photoFileName) {
                    dao.deletePhotosByIds(listOf(photo.id))
                    if (!photo.fileName.startsWith("jr-")) photos.delete(photo.fileName)
                    dao.insertPhotos(
                        listOf(
                            DiaryPhoto(
                                entryId = existing.entry.id,
                                fileName = item.photoFileName,
                                caption = item.photoCaption,
                                createdAt = item.createdAt,
                            ),
                        ),
                    )
                } else {
                    dao.updateCaption(photo.id, item.photoCaption)
                }
            }
        }
    }

    suspend fun unsyncedOriginals(): List<EntryWithPhotos> = dao.unsyncedWithPhotos()

    suspend fun markTextUploaded(id: Long, serverId: Long, serverDay: String) {
        dao.markTextUploaded(id, serverId, serverDay)
    }

    suspend fun markPhotoUploaded(id: Long, serverId: Long) {
        dao.markPhotoUploaded(id, serverId)
    }

    suspend fun markEntryUploaded(id: Long, serverDay: String) {
        dao.markEntryUploaded(id, serverDay)
    }

    private suspend fun dropCached(row: EntryWithPhotos) {
        val files = row.photos.map { it.fileName }
        dao.deleteEntry(row.entry.id)
        files.forEach { photos.delete(it) }
    }

    suspend fun deleteEntry(id: Long) {
        val current = dao.entryWithPhotos(id) ?: return
        val files = current.photos.map { it.fileName }
        val day = current.entry.dayKey
        dao.deleteEntry(id)
        files.forEach { photos.delete(it) }
        if (dao.countEntries(day) == 0) {
            dao.deleteSummary(day)
        }
    }

    suspend fun saveSummary(dayKey: String, text: String, sourceHash: String) {
        dao.upsertSummary(
            DaySummaryEntity(
                dayKey = dayKey,
                text = text.trim(),
                generatedAt = System.currentTimeMillis(),
                sourceHash = sourceHash,
            ),
        )
    }

    suspend fun exportDocument(photosIncluded: Boolean): ExportDocument {
        val entries = dao.allEntriesWithPhotos()
        val summaries = dao.allSummaries().associateBy { it.dayKey }
        val grouped = entries.groupBy { it.entry.dayKey }
        val days = (grouped.keys + summaries.keys).distinct().sorted().map { day ->
            toExportDay(day, grouped[day].orEmpty(), summaries[day])
        }
        return ExportDocument(
            exportedAt = DayClock.isoOffset(System.currentTimeMillis()),
            photosIncluded = photosIncluded,
            versionName = versionName,
            versionCode = versionCode,
            days = days,
        )
    }

    suspend fun exportDay(dayKey: String): ExportDay {
        val summary = dao.allSummaries().firstOrNull { it.dayKey == dayKey }
        return toExportDay(dayKey, dao.entriesWithPhotos(dayKey), summary)
    }

    suspend fun markedDays(): List<String> =
        dao.allEntriesWithPhotos().map { it.entry.dayKey }.distinct().sortedDescending()

    suspend fun sweepStaleFiles(nowMillis: Long = System.currentTimeMillis()) {
        val referenced = dao.allPhotoFileNames().toSet()
        val cutoff = nowMillis - 60_000
        photos.listFiles().forEach { file ->
            if (file.name == ".nomedia") return@forEach
            if (file.name in referenced) return@forEach
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    private fun toExportDay(
        day: String,
        entries: List<EntryWithPhotos>,
        summary: DaySummaryEntity?,
    ): ExportDay = ExportDay(
        day = day,
        entries = entries.map { row ->
            ExportEntry(
                id = row.entry.id,
                body = row.entry.body,
                createdAt = DayClock.isoOffset(row.entry.createdAt),
                updatedAt = DayClock.isoOffset(row.entry.updatedAt),
                photos = row.photos.map { photo ->
                    ExportPhoto(
                        id = photo.id,
                        fileName = photo.fileName,
                        caption = photo.caption,
                        createdAt = DayClock.isoOffset(photo.createdAt),
                    )
                },
            )
        },
        summary = summary?.let {
            ExportSummary(
                text = it.text,
                generatedAt = DayClock.isoOffset(it.generatedAt),
                sourceHash = it.sourceHash,
            )
        },
    )
}

fun EntryWithPhotos.toSummarySource(): SummarySourceEntry =
    SummarySourceEntry(
        body = entry.body,
        captions = photos.map { it.caption },
    )
