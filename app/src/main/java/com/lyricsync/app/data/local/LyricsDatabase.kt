package com.lyricsync.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_lyrics")
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

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CachedLyricsEntity)

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAll()
}

@Database(entities = [CachedLyricsEntity::class], version = 1, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        fun getInstance(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics_cache.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
