package app.diary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiaryEntry::class, DiaryPhoto::class, DaySummaryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): DiaryDatabase =
            Room.databaseBuilder(context, DiaryDatabase::class.java, "diary.db")
                .build()
    }
}
