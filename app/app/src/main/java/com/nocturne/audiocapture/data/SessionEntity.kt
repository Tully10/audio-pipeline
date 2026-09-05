package com.nocturne.audiocapture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions_cache")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: String,
    val durationSeconds: Int,
    val summary: String?,
    val peopleCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)
