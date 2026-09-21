package dev.opennotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opennotes.data.BlockEntity
import dev.opennotes.data.BlockType
import dev.opennotes.data.ListItem
import dev.opennotes.data.NotesRepository
import dev.opennotes.data.TodoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repository: NotesRepository,
    private val noteId: Long,
) : ViewModel() {
    val note = repository.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val blocks = repository.observeBlocks(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _titleDraft = MutableStateFlow<String?>(null)
    val titleDraft: StateFlow<String?> = _titleDraft.asStateFlow()

    /** Text typed per block, kept separate so a database refresh cannot move the cursor. */
    private val _textDrafts = MutableStateFlow<Map<Long, String>>(emptyMap())
    val textDrafts: StateFlow<Map<Long, String>> = _textDrafts.asStateFlow()

    fun updateTitle(title: String) {
        _titleDraft.value = title
        viewModelScope.launch { repository.updateTitle(noteId, title) }
    }

    fun updateText(blockId: Long, text: String) {
        _textDrafts.update { it + (blockId to text) }
        viewModelScope.launch { repository.updateBlockText(blockId, text) }
    }

    fun updateTodoItems(blockId: Long, items: List<TodoItem>) {
        viewModelScope.launch { repository.updateBlockItems(blockId, items) }
    }

    fun updateListItems(blockId: Long, items: List<ListItem>) {
        viewModelScope.launch { repository.updateListItems(blockId, items) }
    }

    fun updateBlockType(blockId: Long, type: String) {
        viewModelScope.launch { repository.updateBlockType(blockId, type) }
    }

    fun updateTextSize(blockId: Long, textSize: Int) {
        viewModelScope.launch { repository.updateBlockTextSize(blockId, textSize) }
    }

    fun addBlock(type: String) {
        viewModelScope.launch { repository.addBlock(noteId, type) }
    }

    fun moveBlock(blockId: Long, direction: Int) {
        viewModelScope.launch { repository.moveBlock(noteId, blockId, direction) }
    }

    fun deleteBlock(block: BlockEntity) {
        _textDrafts.update { it - block.id }
        viewModelScope.launch { repository.deleteBlock(noteId, block) }
    }

    fun addDoodle() = Unit

    companion object {
        fun factory(repository: NotesRepository, noteId: Long): () -> EditorViewModel =
            { EditorViewModel(repository, noteId) }

        val blockTypes = listOf(
            BlockType.TEXT,
            BlockType.TODO,
            BlockType.BULLET_LIST,
            BlockType.NUMBERED_LIST,
        )
    }
}
