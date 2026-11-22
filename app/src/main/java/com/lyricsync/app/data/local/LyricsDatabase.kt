package com.lyricsync.app.data.local

import android.content.Context
import androidx.room.ColumnInfo
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
        Index(
            value = ["artistName", "trackName", "targetLang", "updatedAt"],
            name = "idx_cached_lyrics_track_lang"
        ),
        Index(
            value = ["isFavorite", "updatedAt"],
            name = "idx_cached_lyrics_fav_updated_at"
        ),
        Index(
            value = ["updatedAt"],
            name = "idx_cached_lyrics_updated_at"
        )
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
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val syncOffsetMs: Long = 0L
)

data class CachedSongSummary(
    val cacheKey: String,
    val artistName: String,
    val trackName: String,
    val sourceLang: String,
    val targetLang: String,
    val isSynced: Boolean,
    val updatedAt: Long,
    val isFavorite: Boolean
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): CachedLyricsEntity?

    @Query(
        "SELECT * FROM cached_lyrics " +
            "WHERE artistName = :artistName COLLATE NOCASE AND trackName = :trackName COLLATE NOCASE AND targetLang = :targetLang " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatestByTrackAndLang(
        artistName: String,
        trackName: String,
        targetLang: String
    ): CachedLyricsEntity?

    @Query("UPDATE cached_lyrics SET updatedAt = :timestamp WHERE cacheKey = :cacheKey")
    suspend fun touch(cacheKey: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cached_lyrics SET isFavorite = :isFavorite WHERE cacheKey = :cacheKey")
    suspend fun setFavorite(cacheKey: String, isFavorite: Boolean)

    @Query(
        "UPDATE cached_lyrics SET syncOffsetMs = :offsetMs " +
            "WHERE artistName = :artistName COLLATE NOCASE AND trackName = :trackName COLLATE NOCASE"
    )
    suspend fun updateSyncOffsetForTrack(artistName: String, trackName: String, offsetMs: Long)

    @Query(
        "SELECT cacheKey, artistName, trackName, sourceLang, targetLang, isSynced, updatedAt, isFavorite " +
            "FROM cached_lyrics ORDER BY isFavorite DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun getRecentSummaries(limit: Int = 100): List<CachedSongSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CachedLyricsEntity)

    @Query(
        "DELETE FROM cached_lyrics WHERE isFavorite = 0 AND cacheKey NOT IN " +
            "(SELECT cacheKey FROM cached_lyrics ORDER BY updatedAt DESC LIMIT :maxEntries)"
    )
    suspend fun pruneLeastRecentlyUsed(maxEntries: Int = 500)

    @Query("SELECT COUNT(*) FROM cached_lyrics")
    suspend fun count(): Int

    @Query("DELETE FROM cached_lyrics WHERE isFavorite = 0")
    suspend fun clearNonFavorites()

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAll()
}

@Database(entities = [CachedLyricsEntity::class], version = 4, exportSchema = false)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `cached_lyrics` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `cached_lyrics` ADD COLUMN `syncOffsetMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("DROP INDEX IF EXISTS `idx_cached_lyrics_track_lang`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_cached_lyrics_track_lang` " +
                        "ON `cached_lyrics` (`artistName`, `trackName`, `targetLang`, `updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_cached_lyrics_fav_updated_at` " +
                        "ON `cached_lyrics` (`isFavorite`, `updatedAt`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
