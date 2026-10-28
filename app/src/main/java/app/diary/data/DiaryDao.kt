package app.diary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM entries WHERE dayKey = :day ORDER BY createdAt ASC")
    fun observeDay(day: String): Flow<List<EntryWithPhotos>>

    @Query("SELECT dayKey FROM entries")
    fun observeDayKeys(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entryWithPhotos(id: Long): EntryWithPhotos?

    @Transaction
    @Query("SELECT * FROM entries WHERE dayKey = :day ORDER BY createdAt ASC")
    suspend fun entriesWithPhotos(day: String): List<EntryWithPhotos>

    @Transaction
    @Query("SELECT * FROM entries ORDER BY dayKey ASC, createdAt ASC")
    suspend fun allEntriesWithPhotos(): List<EntryWithPhotos>

    @Query("SELECT * FROM summaries")
    suspend fun allSummaries(): List<DaySummaryEntity>

    @Query("SELECT * FROM summaries WHERE dayKey = :day LIMIT 1")
    fun observeSummary(day: String): Flow<List<DaySummaryEntity>>

    @Query("SELECT fileName FROM photos")
    suspend fun allPhotoFileNames(): List<String>

    @Query("SELECT * FROM photos WHERE entryId = :entryId")
    suspend fun photosFor(entryId: Long): List<DiaryPhoto>

    @Insert
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Update
    suspend fun updateEntry(entry: DiaryEntry)

    @Insert
    suspend fun insertPhotos(photos: List<DiaryPhoto>)

    @Query("UPDATE photos SET caption = :caption WHERE id = :id")
    suspend fun updateCaption(id: Long, caption: String)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<Long>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE dayKey = :day")
    suspend fun countEntries(day: String): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: DaySummaryEntity)

    @Query("DELETE FROM summaries WHERE dayKey = :day")
    suspend fun deleteSummary(day: String)

    @Transaction
    @Query(
        "SELECT * FROM entries WHERE cached = 0 AND synced = 0 ORDER BY dayKey ASC, createdAt ASC",
    )
    suspend fun unsyncedWithPhotos(): List<EntryWithPhotos>

    @Query(
        "UPDATE entries SET textRemoteId = :serverId, syncedServerDay = :serverDay WHERE id = :id",
    )
    suspend fun markTextUploaded(id: Long, serverId: Long, serverDay: String)

    @Query("UPDATE photos SET remoteId = :serverId WHERE id = :id")
    suspend fun markPhotoUploaded(id: Long, serverId: Long)

    @Query("UPDATE entries SET synced = 1, syncedServerDay = :serverDay WHERE id = :id")
    suspend fun markEntryUploaded(id: Long, serverDay: String)
}
