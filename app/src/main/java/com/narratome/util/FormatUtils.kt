package com.narratome.util

import java.util.Locale

fun formatTime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun formatTimeLong(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) {
        String.format(Locale.US, "%dh %dm", hrs, mins)
    } else {
        String.format(Locale.US, "%dm", mins)
    }
}

fun formatTime(seconds: Double): String = formatTime(seconds.toLong())
fun formatTimeLong(seconds: Double): String = formatTimeLong(seconds.toLong())

/** Human-readable file size (binary KB / MB / GB). */
fun formatByteCount(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
