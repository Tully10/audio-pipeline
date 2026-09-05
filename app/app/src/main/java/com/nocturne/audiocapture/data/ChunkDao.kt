package com.nocturne.audiocapture.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChunkDao {
    @Insert
    suspend fun insert(chunk: ChunkEntity): Long

    @Query("SELECT * FROM chunks WHERE status = 'PENDING' ORDER BY chunkSeq ASC")
    suspend fun getPending(): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks WHERE status IN ('PENDING', 'UPLOADING')")
    suspend fun getPendingCount(): Int

    @Query("UPDATE chunks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE chunks SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM chunks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
