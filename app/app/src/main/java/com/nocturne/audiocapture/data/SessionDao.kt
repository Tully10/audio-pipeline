package com.nocturne.audiocapture.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions_cache ORDER BY startedAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions_cache WHERE cachedAt < :cutoff")
    suspend fun evictOlderThan(cutoff: Long)
}
