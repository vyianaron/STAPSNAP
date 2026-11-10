package com.example.daily_walk_journey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.daily_walk_journey.data.db.dao.AchievementDao
import com.example.daily_walk_journey.data.db.dao.CheckpointDao
import com.example.daily_walk_journey.data.db.entity.Achievement
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import com.example.daily_walk_journey.data.db.dao.PoiDao
import com.example.daily_walk_journey.data.db.dao.RoutePointDao
import com.example.daily_walk_journey.data.db.entity.Poi
import com.example.daily_walk_journey.data.db.entity.RoutePoint

@Database(
    entities = [Checkpoint::class, Poi::class, Achievement::class, RoutePoint::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checkpointDao(): CheckpointDao
    abstract fun poiDao(): PoiDao
    abstract fun achievementDao(): AchievementDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE checkpoints ADD COLUMN note TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pois (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        note TEXT,
                        rating REAL NOT NULL,
                        photoPath TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        date TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        unlockedAt INTEGER NOT NULL,
                        seenAt INTEGER
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS route_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        date TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_route_points_date_timestamp " +
                        "ON route_points (date, timestamp)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stepsnap_database"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
