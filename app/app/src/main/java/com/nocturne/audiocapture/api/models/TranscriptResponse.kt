package com.nocturne.audiocapture.api.models

data class WordEntry(
    val word: String,
    val start_s: Double,
    val end_s: Double,
    val confidence: Double,
    val speaker: String,
    val low_confidence: Boolean
)

data class Entity(val name: String, val type: String)
data class Marker(val offset_s: Double, val label: String)

data class TranscriptResponse(
    val id: String,
    val words: List<WordEntry>,
    val summary: String?,
    val entities: List<Entity>,
    val action_items: List<String>,
    val markers: List<Marker>,
    val sentiment: String? = null
)
