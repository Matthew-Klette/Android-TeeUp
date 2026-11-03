package com.teeup.android.data.offline

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
import kotlinx.coroutines.flow.Flow

/**
 * The last successful body of one GET request, so screens can still show data offline
 * (Part 1 design, "Offline mode with sync": tee times, profiles, past and upcoming rounds).
 * [key] is "<firebaseUid>|<path>", so one golfer never sees another's cached rounds on a
 * shared phone.
 */
@Entity(tableName = "cached_responses")
data class CachedResponseEntity(
    @PrimaryKey val key: String,
    val body: String,
    val cachedAtMillis: Long
)

/**
 * A finished round's scorecard that couldn't reach the API yet (the "outbox"). It stays here,
 * shown as "pending sync", until [ScorecardSyncWorker] uploads it once the phone is back online.
 * [entries] uses RoundProgressCodec's "hole:strokes:putts;..." encoding.
 */
@Entity(tableName = "pending_scorecards")
data class PendingScorecardEntity(
    @PrimaryKey val teeTimeId: String,
    val ownerUid: String,
    val courseName: String,
    val entries: String,
    val totalHoles: Int,
    val queuedAtMillis: Long,
    val attempts: Int = 0,
    /** The server's reason when it rejected the upload (a 4xx); null while it's just waiting. */
    val lastError: String? = null
)

@Dao
interface CachedResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CachedResponseEntity)

    @Query("SELECT * FROM cached_responses WHERE `key` = :key LIMIT 1")
    fun find(key: String): CachedResponseEntity?

    @Query("DELETE FROM cached_responses")
    fun clear()
}

@Dao
interface PendingScorecardDao {
    /** REPLACE: finishing the same round offline twice keeps only the latest scores
     *  (last write wins, as the Part 1 conflict rule says). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingScorecardEntity)

    @Query("SELECT * FROM pending_scorecards WHERE ownerUid = :uid ORDER BY queuedAtMillis")
    suspend fun forOwner(uid: String): List<PendingScorecardEntity>

    @Query("SELECT * FROM pending_scorecards WHERE ownerUid = :uid ORDER BY queuedAtMillis")
    fun observeForOwner(uid: String): Flow<List<PendingScorecardEntity>>

    @Query("SELECT teeTimeId FROM pending_scorecards WHERE ownerUid = :uid")
    fun teeTimeIdsForOwner(uid: String): List<String>

    @Query("DELETE FROM pending_scorecards WHERE teeTimeId = :teeTimeId")
    suspend fun delete(teeTimeId: String)

    @Query("UPDATE pending_scorecards SET attempts = attempts + 1, lastError = :error WHERE teeTimeId = :teeTimeId")
    suspend fun recordAttempt(teeTimeId: String, error: String?)
}

/**
 * TeeUp's on-device Room database (Android Developers, 2026a). A single instance per process,
 * created lazily on first use.
 */
@Database(entities = [CachedResponseEntity::class, PendingScorecardEntity::class], version = 1, exportSchema = false)
abstract class TeeUpDatabase : RoomDatabase() {
    abstract fun cachedResponses(): CachedResponseDao
    abstract fun pendingScorecards(): PendingScorecardDao

    companion object {
        @Volatile private var instance: TeeUpDatabase? = null

        fun get(context: Context): TeeUpDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TeeUpDatabase::class.java, "teeup.db")
                // Everything in here is a cache or a queue the server can rebuild, so a schema
                // change may start fresh rather than needing hand-written migrations.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}

/*
References:

Android Developers (2026a). Save data in a local database using Room. [online] Available at: <https://developer.android.com/training/data-storage/room> [Accessed 3 Nov. 2026].
*/
