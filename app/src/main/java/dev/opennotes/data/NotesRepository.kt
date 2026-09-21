package dev.opennotes.data

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single owner of note storage: database rows plus the per-note media directory
 * (`filesDir/notes/<id>/`).
 */
class NotesRepository(
    private val dao: NotesDao,
    private val filesDir: File,
) {
    val noteCards: Flow<List<NoteCard>> = dao.observeNoteCards()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget delete for UI callbacks that are not coroutine scopes. */
    fun deleteNoteAsync(noteId: Long) {
        scope.launch { deleteNote(noteId) }
    }

    fun observeNote(noteId: Long): Flow<NoteEntity?> = dao.observeNote(noteId)

    fun observeBlocks(noteId: Long): Flow<List<BlockEntity>> = dao.observeBlocks(noteId)

    suspend fun getNote(noteId: Long): NoteEntity? = dao.getNote(noteId)

    suspend fun createNote(): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertNote(
            NoteEntity(title = "", createdAt = now, modifiedAt = now, source = NoteSource.USER),
        )
        dao.insertBlock(
            BlockEntity(noteId = id, position = 0, type = BlockType.TEXT, text = ""),
        )
        return id
    }

    suspend fun deleteNote(noteId: Long) {
        dao.deleteNote(noteId)
        noteDir(noteId).deleteRecursively()
    }

    suspend fun updateTitle(noteId: Long, title: String) {
        dao.updateTitle(noteId, title, System.currentTimeMillis())
    }

    suspend fun updateBlockText(blockId: Long, text: String) {
        dao.updateBlockText(blockId, text)
        dao.getBlock(blockId)?.let { dao.touchNote(it.noteId, System.currentTimeMillis()) }
    }

    suspend fun updateBlockItems(blockId: Long, items: List<TodoItem>) {
        dao.updateBlockItems(blockId, TodoJson.encode(items))
        dao.getBlock(blockId)?.let { dao.touchNote(it.noteId, System.currentTimeMillis()) }
    }

    suspend fun updateListItems(blockId: Long, items: List<ListItem>) {
        dao.updateBlockItems(blockId, ListItemJson.encode(items))
        dao.getBlock(blockId)?.let { dao.touchNote(it.noteId, System.currentTimeMillis()) }
    }

    suspend fun updateBlockType(blockId: Long, type: String) {
        dao.updateBlockType(blockId, type)
        dao.getBlock(blockId)?.let { dao.touchNote(it.noteId, System.currentTimeMillis()) }
    }

    suspend fun updateBlockTextSize(blockId: Long, textSize: Int) {
        dao.updateBlockItems(blockId, TextSizeJson.encode(textSize))
        dao.getBlock(blockId)?.let { dao.touchNote(it.noteId, System.currentTimeMillis()) }
    }

    suspend fun addBlock(noteId: Long, type: String) {
        val position = dao.maxPosition(noteId) + 1
        val block = when (type) {
            BlockType.TEXT -> BlockEntity(noteId = noteId, position = position, type = type, text = "")
            BlockType.TODO -> BlockEntity(
                noteId = noteId,
                position = position,
                type = type,
                itemsJson = TodoJson.encode(listOf(TodoItem("", false))),
            )
            BlockType.BULLET_LIST, BlockType.NUMBERED_LIST -> BlockEntity(
                noteId = noteId,
                position = position,
                type = type,
                itemsJson = ListItemJson.encode(listOf(ListItem(""))),
            )
            else -> return
        }
        dao.insertBlock(block)
        dao.touchNote(noteId, System.currentTimeMillis())
    }

    suspend fun addImageBlock(noteId: Long, bitmap: Bitmap): Long {
        val dir = noteDir(noteId)
        val file = File(dir, "image-${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val position = dao.maxPosition(noteId) + 1
        val blockId = dao.insertBlock(
            BlockEntity(
                noteId = noteId,
                position = position,
                type = BlockType.IMAGE,
                filePath = file.absolutePath,
                width = bitmap.width,
                height = bitmap.height,
            ),
        )
        dao.touchNote(noteId, System.currentTimeMillis())
        if (dao.getNote(noteId)?.thumbnailPath == null) {
            dao.updateThumbnail(noteId, file.absolutePath)
        }
        return blockId
    }

    suspend fun moveBlock(noteId: Long, blockId: Long, direction: Int) {
        val blocks = dao.getBlocks(noteId)
        val index = blocks.indexOfFirst { it.id == blockId }
        val target = index + direction
        if (index < 0 || target !in blocks.indices) return
        val first = blocks[index]
        val second = blocks[target]
        dao.updateBlockPosition(first.id, second.position)
        dao.updateBlockPosition(second.id, first.position)
        dao.touchNote(noteId, System.currentTimeMillis())
    }

    suspend fun deleteBlock(noteId: Long, block: BlockEntity) {
        dao.deleteBlock(block)
        block.filePath?.let { path ->
            // Doodles and imported page images are owned by the block; only remove
            // files that live inside the note directory.
            val file = File(path)
            if (file.absolutePath.startsWith(noteDir(noteId).absolutePath)) {
                file.delete()
            }
        }
        dao.touchNote(noteId, System.currentTimeMillis())
    }

    /** Directory holding assets for a note, created on demand. */
    fun noteDir(noteId: Long): File = File(filesDir, "notes/$noteId").apply { mkdirs() }

    /** Create the note row first so assets can be written into its directory. */
    suspend fun insertNoteShell(title: String, createdAt: Long, modifiedAt: Long): Long =
        dao.insertNote(
            NoteEntity(
                title = title,
                createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
                modifiedAt = if (modifiedAt > 0) modifiedAt else System.currentTimeMillis(),
                source = NoteSource.SDOCX,
            ),
        )

    suspend fun insertBlocks(noteId: Long, blocks: List<BlockEntity>) {
        dao.insertBlocks(blocks.mapIndexed { index, block -> block.copy(id = 0, noteId = noteId, position = index) })
    }

    suspend fun setThumbnail(noteId: Long, path: String?) {
        dao.updateThumbnail(noteId, path)
    }
}
