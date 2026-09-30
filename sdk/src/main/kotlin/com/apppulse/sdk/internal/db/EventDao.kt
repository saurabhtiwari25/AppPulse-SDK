package com.apppulse.sdk.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room Data Access Object for analytics events.
 *
 * All methods are suspend functions — they must be called from a coroutine
 * and will run on whatever dispatcher the caller provides (typically Dispatchers.IO).
 */
@Dao
internal interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AnalyticsEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<AnalyticsEventEntity>)

    /** Returns events with the given [status], ordered oldest-first, up to [limit]. */
    @Query("SELECT * FROM analytics_events WHERE status = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<AnalyticsEventEntity>

    @Query("UPDATE analytics_events SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE analytics_events SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusBatch(ids: List<String>, status: String)

    /** Increments retry count and sets status in one atomic operation. */
    @Query("UPDATE analytics_events SET retryCount = retryCount + 1, status = :status WHERE id = :id")
    suspend fun incrementRetryAndUpdateStatus(id: String, status: String)

    @Query("SELECT retryCount FROM analytics_events WHERE id = :id")
    suspend fun getRetryCount(id: String): Int?

    @Query("DELETE FROM analytics_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM analytics_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM analytics_events WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    @Query("SELECT COUNT(*) FROM analytics_events WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM analytics_events")
    suspend fun countAll(): Int

    @Query("DELETE FROM analytics_events")
    suspend fun deleteAll()
}
