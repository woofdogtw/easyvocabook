package tw.idv.woofdog.easyvocabook.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.idv.woofdog.easyvocabook.R
import tw.idv.woofdog.easyvocabook.quiz.McqCard
import tw.idv.woofdog.easyvocabook.quiz.TypingCard
import tw.idv.woofdog.easyvocabook.quiz.TypingResult
import tw.idv.woofdog.easyvocabook.ui.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(vm: QuizViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val languages = listOf(null) + Labels.SUPPORTED_LANGUAGES
    var selectedLang by remember { mutableStateOf<String?>(null) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_quiz)) },
                actions = {
                    ExposedDropdownMenuBox(
                        expanded = langMenuExpanded,
                        onExpandedChange = { langMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedLang?.let { stringResource(Labels.langResId(it)) } ?: stringResource(R.string.quiz_language_all),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langMenuExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).width(160.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        )
                        ExposedDropdownMenu(expanded = langMenuExpanded, onDismissRequest = { langMenuExpanded = false }) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang?.let { stringResource(Labels.langResId(it)) } ?: stringResource(R.string.quiz_language_all)) },
                                    onClick = {
                                        selectedLang = lang
                                        langMenuExpanded = false
                                        vm.setLanguageFilter(lang)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.skip() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.quiz_skip))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is QuizUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is QuizUiState.Empty -> EmptyState()
                is QuizUiState.TypingCard -> TypingCardView(s.card, s.inputs, vm)
                is QuizUiState.TypingResult -> TypingResultView(s.result, vm)
                is QuizUiState.McqCard -> McqCardView(s.card, s.selected, vm)
                is QuizUiState.McqResult -> McqResultView(s.result, vm)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.quiz_empty), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TypingCardView(card: TypingCard, inputs: List<String>, vm: QuizViewModel) {
    val fieldCount = card.fields.size
    val focusRequesters = remember(fieldCount) { List(fieldCount) { FocusRequester() } }
    val submitFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(card.meaningPrompt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.quiz_typing_label), style = MaterialTheme.typography.bodyMedium)
        card.fields.forEachIndexed { idx, field ->
            val labelText = Labels.formLabelResId(field.label)
                ?.let { stringResource(it) } ?: field.label
            val isLast = idx == fieldCount - 1
            OutlinedTextField(
                value = inputs.getOrElse(idx) { "" },
                onValueChange = { vm.updateTypingInput(idx, it) },
                label = { Text(String.format(stringResource(R.string.quiz_enter_form), labelText)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequesters[idx]),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusRequesters[idx + 1].requestFocus() },
                    onDone = { submitFocus.requestFocus(); keyboard?.hide() },
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.giveUpTyping() }) { Text(stringResource(R.string.quiz_give_up)) }
            Button(
                onClick = { vm.submitTyping(inputs) },
                modifier = Modifier.focusRequester(submitFocus),
            ) { Text(stringResource(R.string.quiz_submit)) }
        }
    }
}

@Composable
private fun TypingResultView(result: TypingResult, vm: QuizViewModel) {
    val green = MaterialTheme.colorScheme.primary
    val red = MaterialTheme.colorScheme.error
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(result.card.meaningPrompt, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        result.fieldResults.forEach { fr ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (fr.correct) "✓" else "✗", color = if (fr.correct) green else red, fontSize = 18.sp)
                Column {
                    val labelText = Labels.formLabelResId(fr.label)
                        ?.let { stringResource(it) } ?: fr.label
                    Text(labelText, style = MaterialTheme.typography.labelSmall)
                    if (!fr.correct) Text(fr.correctValue, color = green)
                }
            }
        }
        if (result.synonyms.isNotEmpty()) {
            Text(stringResource(R.string.quiz_synonyms), style = MaterialTheme.typography.labelMedium)
            Text(result.synonyms.joinToString(", "))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.next() }) { Text(stringResource(R.string.quiz_next)) }
    }
}

@Composable
private fun McqCardView(card: McqCard, selected: Set<String>, vm: QuizViewModel) {
    val word = card.word
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(word.word, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (word.reading != null) Text("(${word.reading})", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.quiz_mcq_label), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            itemsIndexed(card.options) { _, opt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = opt.meaning in selected, onCheckedChange = { vm.toggleMcqSelection(opt.meaning) })
                    Text(opt.meaning, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.giveUpMcq() }) { Text(stringResource(R.string.quiz_give_up)) }
            Button(onClick = { vm.submitMcq(selected) }) { Text(stringResource(R.string.quiz_submit)) }
        }
    }
}

@Composable
private fun McqResultView(result: tw.idv.woofdog.easyvocabook.quiz.McqResult, vm: QuizViewModel) {
    val green = MaterialTheme.colorScheme.primary
    val red = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(result.card.word.word, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            itemsIndexed(result.card.options) { _, opt ->
                val wasSelected = opt.meaning in result.selected
                val color = when {
                    opt.isCorrect -> green
                    wasSelected -> red
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (opt.isCorrect) "✓" else if (wasSelected) "✗" else "  ",
                        color = color,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(opt.meaning, color = color, modifier = Modifier.weight(1f))
                }
            }
        }
        Button(onClick = { vm.next() }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.quiz_next))
        }
    }
}
