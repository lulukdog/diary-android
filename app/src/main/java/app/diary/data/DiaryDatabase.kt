package app.diary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DiaryEntry::class, DiaryPhoto::class, DaySummaryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `remoteId` INTEGER")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `cached` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `synced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `textRemoteId` INTEGER")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `syncedServerDay` TEXT")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `remoteId` INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_remoteId` ON `entries` (`remoteId`)",
                )
            }
        }

        fun create(context: Context): DiaryDatabase =
            Room.databaseBuilder(context, DiaryDatabase::class.java, "diary.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
