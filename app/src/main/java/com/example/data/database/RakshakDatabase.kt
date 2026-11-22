package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScanHistoryEntity::class,
        VaultItemEntity::class,
        QuarantineEntity::class,
        TrustedContactEntity::class,
        IncidentEntity::class,
        FeedbackEntity::class,
        TrustedAllowlistEntity::class,
        CollectedMessageScanEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RakshakDatabase : RoomDatabase() {
    abstract fun rakshakDao(): RakshakDao

    companion object {
        @Volatile
        private var INSTANCE: RakshakDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collected_message_scans (
                        id TEXT NOT NULL PRIMARY KEY,
                        sender TEXT NOT NULL,
                        sourceChannel TEXT NOT NULL,
                        sourceApp TEXT NOT NULL,
                        receivedAt INTEGER NOT NULL,
                        scannedAt INTEGER NOT NULL,
                        encryptedBody TEXT NOT NULL,
                        ivBase64 TEXT NOT NULL,
                        snippetPreview TEXT NOT NULL,
                        riskScore INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        uncertain INTEGER NOT NULL,
                        categoryName TEXT NOT NULL,
                        shortExplanation TEXT NOT NULL,
                        matchedSignalsCsv TEXT NOT NULL,
                        isRisky INTEGER NOT NULL,
                        alertTriggered INTEGER NOT NULL,
                        isReviewed INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): RakshakDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RakshakDatabase::class.java,
                    "rakshakai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

