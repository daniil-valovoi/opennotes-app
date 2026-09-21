package dev.opennotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opennotes.data.BlockType
import dev.opennotes.data.ListItemJson
import dev.opennotes.data.NoteCard
import dev.opennotes.data.NotesRepository
import dev.opennotes.data.TodoJson
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    repository: NotesRepository,
    onCreate: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val notes by repository.noteCards.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember { mutableStateOf<NoteCard?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = remember(notes, query) {
        val needle = query.trim()
        if (needle.isEmpty()) notes
        else notes.filter {
            it.note.title.contains(needle, ignoreCase = true) ||
                it.snippet().contains(needle, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenNotes") },
                actions = {
                    TextButton(onClick = onImport) {
                        Text("Import")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (notes.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (notes.isEmpty()) "No notes yet" else "No matches",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (notes.isEmpty()) {
                                "Tap + for a new note, or import a Samsung Notes archive."
                            } else {
                                "Try a different search."
                            },
                            modifier = Modifier.padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.note.id }) { card ->
                        NoteRow(
                            card = card,
                            onClick = { onOpenNote(card.note.id) },
                            onDelete = { pendingDelete = card },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete note?") },
            text = { Text("\"${card.note.title.ifBlank { "Untitled" }}\" and its images will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        repository.deleteNoteAsync(card.note.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    card: NoteCard,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDelete),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            val thumbnail = card.note.thumbnailPath
            if (thumbnail != null) {
                LocalImage(
                    path = thumbnail,
                    modifier = Modifier
                        .size(56.dp, 72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp, 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.snippet(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(card.note.modifiedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun NoteCard.snippet(): String = when (blockType) {
    BlockType.TEXT -> blockText.orEmpty().replace('\n', ' ').trim()
    BlockType.TODO -> TodoJson.decode(blockItems).firstOrNull()
        ?.let { "${if (it.checked) "\u2611" else "\u2610"} ${it.text}" }
        .orEmpty()
    BlockType.BULLET_LIST -> ListItemJson.decode(blockItems).firstOrNull()
        ?.let { "• ${it.text}" }
        .orEmpty()
    BlockType.NUMBERED_LIST -> ListItemJson.decode(blockItems).firstOrNull()
        ?.let { "1. ${it.text}" }
        .orEmpty()
    BlockType.IMAGE -> "Image"
    else -> ""
}
