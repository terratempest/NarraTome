package com.narratome.domain.bookmark

/**
 * Threshold (seconds) for treating two bookmark positions as the same when comparing
 * local vs server rows in UI or future conflict prompts.
 */
object BookmarkConflictRules {
    const val SAME_POSITION_THRESHOLD_SEC = 3
}
