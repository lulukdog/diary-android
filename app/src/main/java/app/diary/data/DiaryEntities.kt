package app.diary.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "entries",
    indices = [Index("dayKey")],
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
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
