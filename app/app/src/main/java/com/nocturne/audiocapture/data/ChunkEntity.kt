package com.nocturne.audiocapture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val chunkSeq: Int,
    val recordedAt: String,
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val markers: String = "[]"
)
