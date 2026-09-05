package com.nocturne.audiocapture.api.models

data class TodayStatus(
    val recording: Boolean,
    val sessions_today: Int,
    val people_today: Int,
    val queue_depth: Int,
    val last_transcript_at: String?
)
