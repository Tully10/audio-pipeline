package com.nocturne.audiocapture.api.models

data class AskRequest(val q: String)
data class AskResponse(val answer: String, val sources: List<AskSource>)
data class AskSource(val session_id: String, val timestamp_s: Double, val quote: String)
