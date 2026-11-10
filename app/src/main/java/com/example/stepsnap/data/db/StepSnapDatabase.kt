package com.example.stepsnap.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.stepsnap.data.db.dao.DailyRecordDao
import com.example.stepsnap.data.db.dao.PhotoDao
import com.example.stepsnap.data.db.dao.PoiDao
import com.example.stepsnap.data.db.entity.DailyRecordEntity
import com.example.stepsnap.data.db.entity.PhotoEntity
import com.example.stepsnap.data.db.entity.PoiEntity
import com.example.stepsnap.data.db.entity.PoiPhotoEntity

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `poi_photos` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `poiId` INTEGER NOT NULL,
                `path` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`poiId`) REFERENCES `pois`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_poi_photos_poiId` ON `poi_photos` (`poiId`)"
        )
        db.execSQL(
            """
            INSERT INTO poi_photos (poiId, path, createdAt)
            SELECT id, photoPath, createdAt FROM pois
            WHERE photoPath IS NOT NULL AND photoPath != ''
            """.trimIndent()
        )
    }
}

@Database(
    entities = [DailyRecordEntity::class, PoiEntity::class, PhotoEntity::class, PoiPhotoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class StepSnapDatabase : RoomDatabase() {
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun poiDao(): PoiDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var instance: StepSnapDatabase? = null

        fun getInstance(context: Context): StepSnapDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepSnapDatabase::class.java,
                    "stepsnap_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
        }
    }
}
