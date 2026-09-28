package com.narratome.player

import androidx.media3.common.Player
import com.narratome.domain.model.BookChapter

internal fun nextChapterSeekPositionMs(chapters: List<BookChapter>, currentPositionMs: Long): Long? {
    val currentPositionSec = currentPositionMs / 1000.0
    return chapters.sortedBy { it.startSec }
        .firstOrNull { it.startSec > currentPositionSec + 1.0 }
        ?.startSec
        ?.let { (it * 1000.0).toLong() }
}

internal fun previousChapterSeekPositionMs(chapters: List<BookChapter>, currentPositionMs: Long): Long? {
    if (chapters.isEmpty()) return null

    val currentPositionSec = currentPositionMs / 1000.0
    val sorted = chapters.sortedBy { it.startSec }
    val currentChapter = sorted.lastOrNull { it.startSec <= currentPositionSec + 0.1 } ?: return null
    val index = sorted.indexOf(currentChapter)
    val targetStartSec = when {
        currentPositionSec - currentChapter.startSec > 3.0 -> currentChapter.startSec
        index > 0 -> sorted[index - 1].startSec
        else -> 0.0
    }
    return (targetStartSec * 1000.0).toLong()
}

internal fun Player.Commands.withChapterNavigationCommands(): Player.Commands =
    buildUpon()
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .build()
