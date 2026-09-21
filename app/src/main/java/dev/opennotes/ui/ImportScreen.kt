package dev.opennotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opennotes.importer.ImportOutcome
import dev.opennotes.importer.SdocxImporter
import kotlinx.coroutines.launch

/** Multi-select import of Samsung Notes archives through the system file picker. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    importer: SdocxImporter,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<ImportOutcome>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            results = emptyList()
            val imported = mutableListOf<ImportOutcome>()
            uris.forEachIndexed { index, uri ->
                progress = (index + 1) to uris.size
                imported += importer.import(uri)
            }
            results = imported
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "Select Samsung Notes .sdocx exports. The archive is parsed on this device and " +
                    "the original file is kept with the note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) },
            ) {
                Text("Choose .sdocx files")
            }

            if (busy) {
                Spacer(Modifier.height(20.dp))
                val (done, total) = progress
                Text(
                    "Importing $done of $total…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { result ->
                    ResultCard(result, onOpenNote)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ImportOutcome, onOpenNote: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            result.error != null -> result.error
                            result.success -> "Imported"
                            else -> "Not imported"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (result.success) {
                    TextButton(onClick = { result.noteId?.let(onOpenNote) }) {
                        Text("Open")
                    }
                }
            }
            for (warning in result.warnings) {
                Text(
                    "• $warning",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
