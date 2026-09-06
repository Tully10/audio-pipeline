package com.nocturne.audiocapture.api.models

data class Session(
    val id: String,
    val started_at: String,
    val ended_at: String?,
    val duration_s: Int,
    val speaker_count: Int,
    val summary: String?,
    val people: List<String>,
    val status: String,
    val sentiment: String? = null
)
