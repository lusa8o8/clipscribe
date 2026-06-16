package com.example.storage

import java.util.UUID

data class TranscriptEntity(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 45
)
