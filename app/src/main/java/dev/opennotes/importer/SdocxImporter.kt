package dev.opennotes.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dev.opennotes.data.BlockEntity
import dev.opennotes.data.BlockType
import dev.opennotes.data.NotesRepository
import dev.opennotes.data.TodoJson
import dev.opennotes.nativebridge.SdocxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

data class ImportOutcome(
    val title: String,
    val noteId: Long? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
) {
    val success: Boolean get() = noteId != null
}

/**
 * Reads a `.sdocx` from a SAF URI, parses it with the native bridge, renders ink pages
 * to PNG and writes the note into Room.
 *
 * The original archive is kept at `notes/<id>/original.sdocx` so a note can always be
 * re-imported after parser improvements.
 */
class SdocxImporter(
    private val context: Context,
    private val repository: NotesRepository,
) {
    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val fileName = displayName(uri)
        if (!SdocxBridge.isAvailable) {
            return@withContext ImportOutcome(
                title = fileName,
                error = "Native parser is not installed in this build",
            )
        }

        val bytes = try {
            readAll(uri)
        } catch (e: Exception) {
            return@withContext ImportOutcome(title = fileName, error = e.message ?: "cannot read file")
        }

        val parsed = try {
            ImportJson.parse(SdocxBridge.parse(bytes))
        } catch (e: IllegalArgumentException) {
            return@withContext ImportOutcome(title = fileName, error = e.message ?: "parse failed")
        } catch (e: Exception) {
            return@withContext ImportOutcome(title = fileName, error = e.message ?: "parse failed")
        }

        val title = parsed.title.ifBlank {
            fileName.removeSuffix(".sdocx").removeSuffix(".SDOCX").ifBlank { "Imported note" }
        }
        val warnings = parsed.warnings.toMutableList()
        var noteId: Long? = null
        try {
            noteId = repository.insertNoteShell(title, parsed.createdMs, parsed.modifiedMs)
            val dir = repository.noteDir(noteId)
            File(dir, "original.sdocx").writeBytes(bytes)

            val referenced = parsed.pages.flatMap { page -> page.media.map { it.name } }.toSet()
            val extracted = extractMedia(bytes, referenced, dir, warnings)

            val blocks = mutableListOf<BlockEntity>()
            for (page in parsed.pages) {
                if (page.text.isNotBlank()) {
                    blocks += BlockEntity(
                        noteId = 0,
                        position = 0,
                        type = BlockType.TEXT,
                        text = page.text,
                    )
                }
                if (page.todos.isNotEmpty()) {
                    blocks += BlockEntity(
                        noteId = 0,
                        position = 0,
                        type = BlockType.TODO,
                        itemsJson = TodoJson.encode(page.todos),
                    )
                }
                PageRenderer.render(page)?.let { bitmap ->
                    val file = File(dir, "page-${page.index}.png")
                    val width = bitmap.width
                    val height = bitmap.height
                    writePng(bitmap, file)
                    bitmap.recycle()
                    blocks += BlockEntity(
                        noteId = 0,
                        position = 0,
                        type = BlockType.IMAGE,
                        filePath = file.absolutePath,
                        width = width,
                        height = height,
                    )
                }
                for (media in page.media) {
                    val file = extracted[media.name]
                    if (file == null) {
                        warnings += "missing media: ${media.name}"
                        continue
                    }
                    val size = imageSize(file)
                    if (size == null) {
                        warnings += "unsupported media: ${media.name}"
                        file.delete()
                        continue
                    }
                    blocks += BlockEntity(
                        noteId = 0,
                        position = 0,
                        type = BlockType.IMAGE,
                        filePath = file.absolutePath,
                        width = size.first,
                        height = size.second,
                    )
                }
            }
            if (blocks.none { it.type == BlockType.TEXT }) {
                blocks += BlockEntity(noteId = 0, position = 0, type = BlockType.TEXT, text = "")
            }

            repository.insertBlocks(noteId, blocks)
            blocks.firstOrNull { it.type == BlockType.IMAGE }
                ?.filePath
                ?.let { repository.setThumbnail(noteId, it) }

            ImportOutcome(title = title, noteId = noteId, warnings = warnings)
        } catch (e: Exception) {
            noteId?.let { repository.deleteNote(it) }
            ImportOutcome(title = title, error = e.message ?: "import failed", warnings = warnings)
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: "note.sdocx"
    }

    private fun readAll(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("cannot open file")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw IOException("file is larger than 256 MB")
                out.write(chunk, 0, read)
            }
            return out.toByteArray()
        }
    }

    /** Copy referenced `media/` entries out of the archive; returns archive name -> file. */
    private fun extractMedia(
        archive: ByteArray,
        referenced: Set<String>,
        dir: File,
        warnings: MutableList<String>,
    ): Map<String, File> {
        if (referenced.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, File>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in referenced) {
                    zip.closeEntry()
                    continue
                }
                if (out.size >= MAX_MEDIA_FILES) {
                    zip.closeEntry()
                    continue
                }
                val data = zip.readEntryBytes()
                totalBytes += data.size
                if (totalBytes > MAX_MEDIA_TOTAL_BYTES) {
                    throw IOException("archive media is larger than ${MAX_MEDIA_TOTAL_BYTES / (1024 * 1024)} MB")
                }
                // Never reuse a name from the archive: entries are untrusted and a path
                // separator in the extension would escape the note directory.
                val file = File(dir, "media-${out.size}.${safeExtension(entry.name)}")
                file.writeBytes(data)
                out[entry.name] = file
            }
        }
        if (out.size >= MAX_MEDIA_FILES) {
            warnings += "only the first $MAX_MEDIA_FILES images were imported"
        }
        for (name in referenced) {
            if (name !in out) warnings += "media not found in archive: $name"
        }
        return out
    }

    private fun ZipInputStream.readEntryBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(32 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            out.write(chunk, 0, read)
            if (out.size() > MAX_MEDIA_BYTES) throw IOException("media entry is too large")
        }
        return out.toByteArray()
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("cannot write ${file.name}")
            }
        }
    }

    private fun imageSize(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    private companion object {
        const val MAX_FILE_BYTES = 256L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 64 * 1024 * 1024
        const val MAX_MEDIA_TOTAL_BYTES = 256L * 1024 * 1024
        const val MAX_MEDIA_FILES = 500

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")

        /**
         * Map an archive entry name to a file extension we allow on disk. Anything
         * unexpected (including names containing `/` or `..`) becomes `bin`.
         */
        fun safeExtension(entryName: String): String {
            val extension = entryName.substringAfterLast('.', "").lowercase()
            return if (extension in IMAGE_EXTENSIONS) extension else "bin"
        }
    }
}
