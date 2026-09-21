package dev.opennotes.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a note came from. Imported notes keep the original archive for re-import. */
object NoteSource {
    const val USER = "USER"
    const val SDOCX = "SDOCX"
}

/** Block kinds. Stored as text so the database stays readable. */
object BlockType {
    const val TEXT = "TEXT"
    const val TODO = "TODO"
    const val IMAGE = "IMAGE"
    const val BULLET_LIST = "BULLET_LIST"
    const val NUMBERED_LIST = "NUMBERED_LIST"
}

/** Font sizes available for TEXT blocks; stored as an int so the DB stays simple. */
object TextSize {
    const val SMALL = 0
    const val NORMAL = 1
    const val LARGE = 2

    val all = listOf(SMALL, NORMAL, LARGE)

    fun label(size: Int): String = when (size) {
        SMALL -> "Small"
        LARGE -> "Large"
        else -> "Normal"
    }

    fun sp(size: Int): Int = when (size) {
        SMALL -> 14
        LARGE -> 22
        else -> 16
    }
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val source: String = NoteSource.USER,
    val thumbnailPath: String? = null,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val position: Int,
    val type: String,
    /** TEXT block body. */
    val text: String? = null,
    /** TODO items: `[{"text":"...","checked":false}]`; TEXT style: `{"size":2}`. */
    val itemsJson: String? = null,
    /** IMAGE block file on disk. */
    val filePath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/** A note plus the first meaningful block, used for the list snippet. */
data class NoteCard(
    @Embedded val note: NoteEntity,
    val blockType: String?,
    val blockText: String?,
    val blockItems: String?,
)
