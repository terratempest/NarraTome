package com.narratome.domain.model

enum class SyncConflictPolicy {
    ALWAYS_ASK,
    PREFER_SERVER,
    PREFER_LOCAL,
}
