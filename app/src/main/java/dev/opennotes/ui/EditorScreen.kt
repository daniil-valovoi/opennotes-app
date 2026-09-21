package dev.opennotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.opennotes.data.BlockEntity
import dev.opennotes.data.BlockType
import dev.opennotes.data.ListItem
import dev.opennotes.data.ListItemJson
import dev.opennotes.data.NoteEntity
import dev.opennotes.data.NoteSource
import dev.opennotes.data.NotesRepository
import dev.opennotes.data.TextSize
import dev.opennotes.data.TextSizeJson
import dev.opennotes.data.TodoItem
import dev.opennotes.data.TodoJson

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long,
    repository: NotesRepository,
    onBack: () -> Unit,
    onDoodle: () -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val viewModel: EditorViewModel = viewModel(
        key = "editor-$noteId",
        factory = viewModelFactory { initializer { EditorViewModel(repository, noteId) } },
    )
    val note by viewModel.note.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val titleDraft by viewModel.titleDraft.collectAsStateWithLifecycle()
    val textDrafts by viewModel.textDrafts.collectAsStateWithLifecycle()
    val firstTextBlockId = remember(blocks) {
        blocks.firstOrNull { it.type == BlockType.TEXT }?.id
    }
    val listState = rememberLazyListState()

    Scaffold(
        // Edge-to-edge disables window resizing, so the IME must be consumed here;
        // otherwise the keyboard covers the list and the fields can't be scrolled into view.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    val title = titleDraft ?: note?.title.orEmpty()
                    var titleFocused by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = title,
                        onValueChange = viewModel::updateTitle,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .onFocusChanged { titleFocused = it.isFocused },
                        decorationBox = { inner ->
                            if (titleFocused) {
                                if (title.isBlank()) {
                                    Text(
                                        "Title",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            } else {
                                // An unfocused BasicTextField scrolls instead of ellipsizing,
                                // which lets a long title run into the screen edge.
                                Text(
                                    text = title.ifBlank { "Title" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (title.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Add text",
                        onClick = { viewModel.addBlock(BlockType.TEXT) },
                    )
                    ToolIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Add todo",
                        onClick = { viewModel.addBlock(BlockType.TODO) },
                    )
                    var listMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        ToolIconButton(
                            icon = Icons.Default.List,
                            contentDescription = "Add list",
                            onClick = { listMenuOpen = true },
                        )
                        DropdownMenu(
                            expanded = listMenuOpen,
                            onDismissRequest = { listMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bulleted list") },
                                onClick = {
                                    listMenuOpen = false
                                    viewModel.addBlock(BlockType.BULLET_LIST)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Numbered list") },
                                onClick = {
                                    listMenuOpen = false
                                    viewModel.addBlock(BlockType.NUMBERED_LIST)
                                },
                            )
                        }
                    }
                    ToolIconButton(
                        icon = DoodleCurveIcon,
                        contentDescription = "Add doodle",
                        onClick = onDoodle,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                BlockCard(
                    block = block,
                    text = textDrafts[block.id] ?: block.fullText(note, firstTextBlockId),
                    canMoveUp = index > 0,
                    canMoveDown = index < blocks.lastIndex,
                    onTextChange = { viewModel.updateText(block.id, it) },
                    onItemsChange = { viewModel.updateTodoItems(block.id, it) },
                    onListItemsChange = { viewModel.updateListItems(block.id, it) },
                    onTypeChange = { viewModel.updateBlockType(block.id, it) },
                    onTextSizeChange = { viewModel.updateTextSize(block.id, it) },
                    onMoveUp = { viewModel.moveBlock(block.id, -1) },
                    onMoveDown = { viewModel.moveBlock(block.id, 1) },
                    onDelete = { viewModel.deleteBlock(block) },
                    onOpenImage = onOpenImage,
                )
            }
            item {
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

private val DoodleCurveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DoodleCurve",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 15.5f)
            curveTo(6.5f, 6f, 10.5f, 20f, 14.5f, 11f)
            curveTo(17f, 5.5f, 19.5f, 7.5f, 20.5f, 7.5f)
        }
    }.build()
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BlockCard(
    block: BlockEntity,
    text: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onItemsChange: (List<TodoItem>) -> Unit,
    onListItemsChange: (List<ListItem>) -> Unit,
    onTypeChange: (String) -> Unit,
    onTextSizeChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String) -> Unit,
) {
    var menuOpen by remember(block.id) { mutableStateOf(false) }
    val textSize = remember(block.itemsJson) { TextSizeJson.decode(block.itemsJson) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (block.type == BlockType.IMAGE) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            when (block.type) {
                BlockType.TEXT -> TextBlockEditor(text, textSize, onTextChange)
                BlockType.TODO -> TodoBlockEditor(block, onItemsChange)
                BlockType.BULLET_LIST -> ListBlockEditor(
                    block = block,
                    isOrdered = false,
                    onItemsChange = onListItemsChange,
                )
                BlockType.NUMBERED_LIST -> ListBlockEditor(
                    block = block,
                    isOrdered = true,
                    onItemsChange = onListItemsChange,
                )
                BlockType.IMAGE -> {
                    val path = block.filePath
                    if (path != null && block.width != null && block.height != null && block.height > 0) {
                        LocalImage(
                            path = path,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenImage(path) }
                                .aspectRatio(block.width.toFloat() / block.height.toFloat()),
                            maxDimension = 1600,
                        )
                    } else if (path != null) {
                        LocalImage(
                            path = path,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clickable { onOpenImage(path) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Box(
                    modifier = Modifier
                        .clickable { menuOpen = true }
                        .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Block options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; onMoveDown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                    if (block.type == BlockType.BULLET_LIST) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Switch to numbered list") },
                            onClick = {
                                menuOpen = false
                                onTypeChange(BlockType.NUMBERED_LIST)
                            },
                        )
                    } else if (block.type == BlockType.NUMBERED_LIST) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Switch to bulleted list") },
                            onClick = {
                                menuOpen = false
                                onTypeChange(BlockType.BULLET_LIST)
                            },
                        )
                    }
                    if (block.type == BlockType.TEXT) {
                        HorizontalDivider()
                        Text(
                            "Text size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                        )
                        TextSize.all.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(TextSize.label(size)) },
                                leadingIcon = {
                                    if (textSize == size) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Spacer(Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onTextSizeChange(size)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBlockEditor(text: String, textSize: Int, onTextChange: (String) -> Unit) {
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = TextSize.sp(textSize).sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 14.dp, top = 14.dp, end = 44.dp, bottom = 14.dp),
        decorationBox = { inner ->
            if (text.isEmpty()) {
                Text(
                    "Text",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = TextSize.sp(textSize).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inner()
        },
    )
}

@Composable
private fun TodoBlockEditor(block: BlockEntity, onItemsChange: (List<TodoItem>) -> Unit) {
    // Local state keeps typing responsive; this screen is the only writer of these items.
    var items by remember(block.id) { mutableStateOf(TodoJson.decode(block.itemsJson)) }

    fun update(next: List<TodoItem>) {
        items = next
        onItemsChange(next)
    }

    Column(modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 40.dp, bottom = 6.dp)) {
        if (items.isEmpty()) {
            Text(
                "No items yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { checked ->
                        update(items.toMutableList().also { it[index] = item.copy(checked = checked) })
                    },
                )
                BasicTextField(
                    value = item.text,
                    onValueChange = { value ->
                        update(items.toMutableList().also { it[index] = item.copy(text = value) })
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { update(items.toMutableList().also { it.removeAt(index) }) },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(
            onClick = { update(items + TodoItem("", false)) },
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add item")
        }
    }
}

@Composable
private fun ListBlockEditor(
    block: BlockEntity,
    isOrdered: Boolean,
    onItemsChange: (List<ListItem>) -> Unit,
) {
    var items by remember(block.id) { mutableStateOf(ListItemJson.decode(block.itemsJson)) }

    fun update(next: List<ListItem>) {
        items = next
        onItemsChange(next)
    }

    Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 40.dp, bottom = 8.dp)) {
        if (items.isEmpty()) {
            Text(
                "No items yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.widthIn(min = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (isOrdered) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = item.text,
                    onValueChange = { value ->
                        update(items.toMutableList().also { it[index] = item.copy(text = value) })
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            val nextItems = items.toMutableList()
                            nextItems.add(index + 1, ListItem(""))
                            update(nextItems)
                        },
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        if (item.text.isEmpty()) {
                            Text(
                                "List item",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    },
                )
                IconButton(
                    onClick = { update(items.toMutableList().also { it.removeAt(index) }) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(
            onClick = { update(items + ListItem("")) },
            modifier = Modifier.defaultMinSize(minWidth = 30.dp, minHeight = 30.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add item")
        }
    }
}

/**
 * Notes imported before the bridge kept the title line have it only in [NoteEntity.title].
 * Show it at the top of the body until the block is edited, so old imports look the same
 * as new ones without rewriting stored notes.
 */
private fun BlockEntity.fullText(note: NoteEntity?, firstTextBlockId: Long?): String {
    val stored = text.orEmpty()
    if (note?.source != NoteSource.SDOCX || id != firstTextBlockId) return stored
    val title = note.title.trim()
    if (title.isEmpty() || stored.isBlank()) return stored
    if (stored.lineSequence().firstOrNull()?.trimEnd() == title) return stored
    return "$title\n$stored"
}
