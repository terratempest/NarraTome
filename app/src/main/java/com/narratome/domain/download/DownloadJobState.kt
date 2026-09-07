package com.narratome.domain.download

object DownloadJobState {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED_NETWORK = "PAUSED_NETWORK"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}
