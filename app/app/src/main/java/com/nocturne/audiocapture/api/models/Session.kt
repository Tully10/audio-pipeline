package com.nocturne.audiocapture.api.models

data class Session(
    val id: String,
    val started_at: String,
    val duration_seconds: Int,
    val summary: String?,
    val people_count: Int,
    val entities: List<String> = emptyList()
)
