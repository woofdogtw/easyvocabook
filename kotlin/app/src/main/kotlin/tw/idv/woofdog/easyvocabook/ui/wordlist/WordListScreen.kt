package tw.idv.woofdog.easyvocabook.ui.wordlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.R
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.ui.wordedit.WordEditSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(vm: WordListViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.reload()
        }
    }
    var searchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editWord by remember { mutableStateOf<WordEntry?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var homophoneWord by remember { mutableStateOf<WordEntry?>(null) }
    var deleteConfirmWord by remember { mutableStateOf<WordEntry?>(null) }

    Scaffold(
        topBar = {
            if (searchActive) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { vm.setSearchQuery(it) },
                    onSearch = { searchActive = false },
                    active = false,
                    onActiveChange = { if (!it) searchActive = false },
                    placeholder = { Text(stringResource(R.string.word_list_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    content = {},
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.tab_word_list)) },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.word_list_search))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.word_list_menu_sort)) },
                                    onClick = {
                                        showMenu = false
                                        val next = when (state.sortOrder) {
                                            SortOrder.WORD_ASC  -> SortOrder.WORD_DESC
                                            SortOrder.WORD_DESC -> SortOrder.RATE_ASC
                                            SortOrder.RATE_ASC  -> SortOrder.RATE_DESC
                                            SortOrder.RATE_DESC -> SortOrder.WORD_ASC
                                        }
                                        vm.setSortOrder(next)
                                    })
                                DropdownMenuItem(text = { Text(stringResource(R.string.word_list_menu_import)) },
                                    onClick = { showMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.word_list_menu_export)) },
                                    onClick = { showMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.word_list_menu_stats)) },
                                    onClick = { showMenu = false; vm.clearStats() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.word_list_menu_sync)) },
                                    onClick = { showMenu = false })
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.syncInProgress) {
                FloatingActionButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    ) { padding ->
        if (state.words.isEmpty() && state.searchQuery.isBlank() && state.languageFilter == null) {
            EmptyWordList(Modifier.padding(padding)) { showAddSheet = true }
        } else {
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.words, key = { it.id }) { word ->
                        WordRow(
                            word = word,
                            onEdit = { editWord = word },
                            onDelete = { deleteConfirmWord = word },
                            onHomophones = { homophoneWord = word },
                        )
                        HorizontalDivider()
                    }
                }
                ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }

    if (showAddSheet) {
        WordEditSheet(
            wordId = null,
            onDismiss = { showAddSheet = false },
            onSaved = { showAddSheet = false; vm.reload() },
        )
    }

    editWord?.let { w ->
        WordEditSheet(
            wordId = w.id,
            onDismiss = { editWord = null },
            onSaved = { editWord = null; vm.reload() },
        )
    }

    deleteConfirmWord?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteConfirmWord = null },
            title = { Text(stringResource(R.string.word_list_delete_title)) },
            text = { Text(stringResource(R.string.word_list_delete_message, w.word)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteWord(w.id); deleteConfirmWord = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.word_list_action_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmWord = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    homophoneWord?.let { w ->
        HomophoneDialog(word = w, allWords = state.words, onDismiss = { homophoneWord = null })
    }
}

@Composable
private fun ListScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val firstIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val visibleCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
    if (totalItems <= visibleCount) return

    var dragging by remember { mutableStateOf(false) }
    var thumbY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .width(20.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
    ) {
        val totalPx = constraints.maxHeight.toFloat()
        val minThumbPx = with(density) { 32.dp.toPx() }
        val thumbFraction = visibleCount.toFloat() / totalItems
        val thumbPx = (totalPx * thumbFraction).coerceAtLeast(minThumbPx)
        val maxScrollOffset = (totalPx - thumbPx).coerceAtLeast(0f)
        val thumbOffsetPx = if (totalItems > visibleCount && maxScrollOffset > 0f)
            firstIndex.toFloat() / (totalItems - visibleCount) * maxScrollOffset
        else 0f

        // Visual thumb on the right edge
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(6.dp)
                .height(with(density) { thumbPx.toDp() })
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .clip(RoundedCornerShape(3.dp))
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.6f else 0.3f)
                )
        )

        // Full-width drag target (wider touch area)
        val updatedTotal by rememberUpdatedState(totalItems)
        val updatedVisible by rememberUpdatedState(visibleCount)
        val updatedMax by rememberUpdatedState(maxScrollOffset)
        val updatedThumbPx by rememberUpdatedState(thumbPx)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { pos ->
                            dragging = true
                            // Initialise accumulated thumb position at tap point
                            thumbY = pos.y
                            val fraction = ((thumbY - updatedThumbPx / 2) / updatedMax)
                                .coerceIn(0f, 1f)
                            val idx = (fraction * (updatedTotal - updatedVisible))
                                .roundToInt().coerceIn(0, (updatedTotal - 1).coerceAtLeast(0))
                            coroutineScope.launch { listState.scrollToItem(idx) }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val movable = updatedTotal - updatedVisible
                            if (updatedMax > 0f && movable > 0) {
                                // Accumulate in float — avoids per-frame toInt() truncation
                                thumbY = (thumbY + dragAmount).coerceIn(0f, updatedMax + updatedThumbPx)
                                val fraction = ((thumbY - updatedThumbPx / 2) / updatedMax)
                                    .coerceIn(0f, 1f)
                                val idx = (fraction * movable).roundToInt().coerceIn(0, movable)
                                coroutineScope.launch { listState.scrollToItem(idx) }
                            }
                        },
                    )
                }
        )
    }
}

@Composable
private fun EmptyWordList(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.word_list_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAdd) { Text(stringResource(R.string.word_list_empty_action)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordRow(
    word: WordEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHomophones: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { expanded = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(word.word, fontWeight = FontWeight.SemiBold)
                    if (!word.reading.isNullOrBlank()) Text("(${word.reading})", style = MaterialTheme.typography.bodySmall)
                }
                Text(word.meaning, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            val rate = if (word.practiceCount > 0) "${word.correctCount * 100 / word.practiceCount}%" else "—"
            Text(rate, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.word_list_action_edit)) },
                onClick = { expanded = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.word_list_action_delete)) },
                onClick = { expanded = false; onDelete() })
            DropdownMenuItem(text = { Text(stringResource(R.string.word_list_action_homophones)) },
                onClick = { expanded = false; onHomophones() })
        }
    }
}

@Composable
private fun HomophoneDialog(word: WordEntry, allWords: List<WordEntry>, onDismiss: () -> Unit) {
    val homophones = allWords.filter { other ->
        other.id != word.id && other.language == word.language &&
            (if (word.reading != null) other.reading == word.reading
            else other.word.equals(word.word, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_list_action_homophones)) },
        text = {
            if (homophones.isEmpty()) Text("—")
            else Column { homophones.forEach { Text("${it.word}: ${it.meaning}") } }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}
