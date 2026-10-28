package app.diary.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "entries",
    indices = [Index("dayKey"), Index(value = ["remoteId"], unique = true)],
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 服务器缓存行对应的日记本条目 id。手机上原来的日记不写这个字段。 */
    val remoteId: Long? = null,
    /** 从日记本拉下来的缓存。手机上自己写的是 false。 */
    @ColumnInfo(defaultValue = "0") val cached: Boolean = false,
    /** 原文已经全部传到日记本。再跑一次上传会跳过。 */
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    /** 文字那一次 POST 返回的 id。有值就不再传这段文字。 */
    val textRemoteId: Long? = null,
    /** 日记本收下这则时归到的日期。 */
    val syncedServerDay: String? = null,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class DiaryPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val fileName: String,
    val caption: String,
    val createdAt: Long,
    /** 这张图在日记本上的条目 id。有值就不再传。 */
    val remoteId: Long? = null,
)

@Entity(tableName = "summaries")
data class DaySummaryEntity(
    @PrimaryKey val dayKey: String,
    val text: String,
    val generatedAt: Long,
    val sourceHash: String,
)

data class EntryWithPhotos(
    @Embedded val entry: DiaryEntry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val photos: List<DiaryPhoto>,
)

data class PendingPhoto(
    val fileName: String,
    val caption: String,
    val existingId: Long? = null,
    val freshlyImported: Boolean = false,
)

/** 从日记本拉下来、要写进本地缓存的一条。 */
data class MirroredEntry(
    val remoteId: Long,
    val body: String,
    val createdAt: Long,
    val photoFileName: String?,
    val photoCaption: String,
)
