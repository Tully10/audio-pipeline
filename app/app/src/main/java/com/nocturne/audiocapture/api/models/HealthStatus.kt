package com.nocturne.audiocapture.api.models

data class HealthStatus(
    val status: String,
    val upload_queue_depth: Int,
    val whisper_worker_status: String,
    val last_transcript_at: String?,
    val disk_free_gb: Double,
    val bedrock_last_call_status: String
)
