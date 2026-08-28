package com.iratrack.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun statusToString(value: CostStatus) = value.name
    @TypeConverter fun stringToStatus(value: String) = CostStatus.valueOf(value)
    @TypeConverter fun unitToString(value: UnitKind) = value.name
    @TypeConverter fun stringToUnit(value: String) = UnitKind.valueOf(value)
}

@Database(
    entities = [UsageRecord::class, ProviderState::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun providerStateDao(): ProviderStateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // v1 -> v2: usage_records had no way to deduplicate re-synchronized data other
        // than the autoincrement id (which is always unique), so every manual or
        // background sync silently duplicated the entire fetched range. This adds a
        // unique index on sourceId so OnConflictStrategy.IGNORE inserts actually work.
        // Existing installs may already contain duplicate rows from that bug, so this
        // migration removes them (keeping the lowest id per sourceId) before creating
        // the index, rather than dropping the user's local history.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM usage_records
                    WHERE sourceId IS NOT NULL
                    AND id NOT IN (
                        SELECT MIN(id) FROM usage_records
                        WHERE sourceId IS NOT NULL
                        GROUP BY sourceId
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_usage_records_sourceId ON usage_records(sourceId)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iratrack.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
