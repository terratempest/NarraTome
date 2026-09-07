package com.narratome.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.displayTitle
import com.narratome.domain.model.sortedByStart
import com.narratome.util.formatTime

@Composable
fun ChapterListDialog(
    chapters: List<BookChapter>,
    onDismiss: () -> Unit,
    onPick: (BookChapter) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 480.dp),
            ) {
                Text("Chapters", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (chapters.isEmpty()) {
                    Text(
                        "No chapters for this book.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val sorted = remember(chapters) { chapters.sortedByStart() }
                    LazyColumn {
                        itemsIndexed(sorted, key = { index, ch -> "${index}_${ch.startSec}" }) { index, ch ->
                            val titleLine = ch.displayTitle(index)
                            val timeLine = buildString {
                                append(formatTime(ch.startSec))
                                if (ch.endSec > ch.startSec) {
                                    append(" – ")
                                    append(formatTime(ch.endSec))
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(ch)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = titleLine,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = timeLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun BookmarkListDialog(
    bookmarks: List<BookmarkEntity>,
    onDismiss: () -> Unit,
    onPick: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
    onAddBookmark: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 480.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        onDismiss()
                        onAddBookmark()
                    }) {
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks yet. Tap Add to create one at the current position.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(bookmarks, key = { _, b -> b.timeSec }) { _, b ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(b)
                                        onDismiss()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        b.title.ifBlank { formatTime(b.timeSec) },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        formatTime(b.timeSec),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDelete(b) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete bookmark",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun CreateBookmarkDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    LaunchedEffect(defaultTitle) { title = defaultTitle }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New bookmark") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = title.trim()
                    if (t.isNotEmpty()) onConfirm(t)
                },
                enabled = title.trim().isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
