package dev.opennotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query(
        """
        SELECT n.*, b.type AS blockType, b.text AS blockText, b.itemsJson AS blockItems
        FROM notes n
        LEFT JOIN blocks b ON b.id = (
            SELECT id FROM blocks
            WHERE noteId = n.id AND (
                (type = 'TEXT' AND text IS NOT NULL AND TRIM(text) <> '') OR
                (type IN ('TODO', 'BULLET_LIST', 'NUMBERED_LIST') AND itemsJson IS NOT NULL AND itemsJson <> '[]') OR
                type = 'IMAGE'
            )
            ORDER BY position
            LIMIT 1
        )
        ORDER BY n.modifiedAt DESC
        """,
    )
    fun observeNoteCards(): Flow<List<NoteCard>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeNote(noteId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNote(noteId: Long): NoteEntity?

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY position")
    fun observeBlocks(noteId: Long): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY position")
    suspend fun getBlocks(noteId: Long): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE id = :blockId")
    suspend fun getBlock(blockId: Long): BlockEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET modifiedAt = :timestamp WHERE id = :noteId")
    suspend fun touchNote(noteId: Long, timestamp: Long)

    @Query("UPDATE notes SET title = :title, modifiedAt = :timestamp WHERE id = :noteId")
    suspend fun updateTitle(noteId: Long, title: String, timestamp: Long)

    @Query("UPDATE notes SET thumbnailPath = :path WHERE id = :noteId")
    suspend fun updateThumbnail(noteId: Long, path: String?)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    @Insert
    suspend fun insertBlock(block: BlockEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<BlockEntity>)

    @Update
    suspend fun updateBlock(block: BlockEntity)

    @Query("UPDATE blocks SET text = :text WHERE id = :blockId")
    suspend fun updateBlockText(blockId: Long, text: String)

    @Query("UPDATE blocks SET itemsJson = :itemsJson WHERE id = :blockId")
    suspend fun updateBlockItems(blockId: Long, itemsJson: String?)

    @Query("UPDATE blocks SET position = :position WHERE id = :blockId")
    suspend fun updateBlockPosition(blockId: Long, position: Int)

    @Query("UPDATE blocks SET type = :type WHERE id = :blockId")
    suspend fun updateBlockType(blockId: Long, type: String)

    @Delete
    suspend fun deleteBlock(block: BlockEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM blocks WHERE noteId = :noteId")
    suspend fun maxPosition(noteId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM blocks")
    suspend fun maxGlobalPosition(): Int
}
