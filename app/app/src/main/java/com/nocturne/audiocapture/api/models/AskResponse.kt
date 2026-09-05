package com.nocturne.audiocapture.api.models

data class AskRequest(val question: String)
data class AskResponse(val answer: String, val sources: List<AskSource>)
data class AskSource(val session_id: String, val started_at: String, val excerpt: String)
