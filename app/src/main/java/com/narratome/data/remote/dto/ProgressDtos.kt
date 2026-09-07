package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MediaProgressPatchDto(
    val currentTime: Double? = null,
    val progress: Double? = null,
    val duration: Double? = null,
    val isFinished: Boolean? = null,
    val hideFromContinueListening: Boolean? = null,
    val finishedAt: Long? = null,
    val startedAt: Long? = null,
)
