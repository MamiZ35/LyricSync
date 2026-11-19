package com.lyricsync.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "cached_lyrics",
    indices = [
        Index(value = ["artistName", "trackName", "targetLang"], name = "idx_cached_lyrics_track_lang"),
        Index(value = ["updatedAt"], name = "idx_cached_lyrics_updated_at")
    ]
)
data class CachedLyricsEntity(
    @PrimaryKey val cacheKey: String,
    val artistName: String,
    val trackName: String,
    val sourceLang: String,
    val targetLang: String,
    val isSynced: Boolean,
    val linesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CachedSongSummary(
    val cacheKey: String,
    val artistName: String,
    val trackName: String,
    val sourceLang: String,
    val targetLang: String,
    val isSynced: Boolean,
    val updatedAt: Long
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): CachedLyricsEntity?

    @Query(
        "SELECT * FROM cached_lyrics " +
            "WHERE artistName = :artistName AND trackName = :trackName AND targetLang = :targetLang " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatestByTrackAndLang(
        artistName: String,
        trackName: String,
        targetLang: String
    ): CachedLyricsEntity?

    @Query("UPDATE cached_lyrics SET updatedAt = :timestamp WHERE cacheKey = :cacheKey")
    suspend fun touch(cacheKey: String, timestamp: Long = System.currentTimeMillis())

    @Query(
        "SELECT cacheKey, artistName, trackName, sourceLang, targetLang, isSynced, updatedAt " +
            "FROM cached_lyrics ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getRecentSummaries(limit: Int = 50): List<CachedSongSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CachedLyricsEntity)

    @Query(
        "DELETE FROM cached_lyrics WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM cached_lyrics ORDER BY updatedAt DESC LIMIT :maxEntries)"
    )
    suspend fun pruneLeastRecentlyUsed(maxEntries: Int = 500)

    @Query("SELECT COUNT(*) FROM cached_lyrics")
    suspend fun count(): Int

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAll()
}

@Database(entities = [CachedLyricsEntity::class], version = 2, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_cached_lyrics_track_lang` " +
                        "ON `cached_lyrics` (`artistName`, `trackName`, `targetLang`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_cached_lyrics_updated_at` " +
                        "ON `cached_lyrics` (`updatedAt`)"
                )
            }
        }

        fun getInstance(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics_cache.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
