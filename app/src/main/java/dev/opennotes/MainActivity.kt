package dev.opennotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.opennotes.data.NotesRepository
import dev.opennotes.importer.SdocxImporter
import dev.opennotes.ui.DoodleScreen
import dev.opennotes.ui.EditorScreen
import dev.opennotes.ui.ImageViewerScreen
import dev.opennotes.ui.ImportScreen
import dev.opennotes.ui.NotesListScreen
import dev.opennotes.ui.OpenNotesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpenNotesApp
        setContent {
            OpenNotesTheme {
                OpenNotesRoot(app.repository, app.importer)
            }
        }
    }
}

/**
 * Screen state is small enough to keep in plain saveable values: the app has no deep
 * navigation, only list -> editor -> doodle/viewer and the import screen.
 */
@Composable
private fun OpenNotesRoot(
    repository: NotesRepository,
    importer: SdocxImporter,
) {
    // 0 / "" mean "not showing"; note ids start at 1.
    var editorNoteId by rememberSaveable { mutableStateOf(0L) }
    var doodleNoteId by rememberSaveable { mutableStateOf(0L) }
    var viewerPath by rememberSaveable { mutableStateOf("") }
    var importing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when {
        viewerPath.isNotEmpty() -> {
            ImageViewerScreen(path = viewerPath, onClose = { viewerPath = "" })
        }

        doodleNoteId != 0L -> {
            DoodleScreen(
                noteId = doodleNoteId,
                repository = repository,
                onFinished = { doodleNoteId = 0L },
                onCancel = { doodleNoteId = 0L },
            )
        }

        editorNoteId != 0L -> {
            BackHandler { editorNoteId = 0L }
            EditorScreen(
                noteId = editorNoteId,
                repository = repository,
                onBack = { editorNoteId = 0L },
                onDoodle = { doodleNoteId = editorNoteId },
                onOpenImage = { viewerPath = it },
            )
        }

        importing -> {
            BackHandler { importing = false }
            ImportScreen(
                importer = importer,
                onBack = { importing = false },
                onOpenNote = { noteId ->
                    importing = false
                    editorNoteId = noteId
                },
            )
        }

        else -> {
            NotesListScreen(
                repository = repository,
                onCreate = {
                    scope.launch { editorNoteId = repository.createNote() }
                },
                onOpenNote = { editorNoteId = it },
                onImport = { importing = true },
            )
        }
    }
}
