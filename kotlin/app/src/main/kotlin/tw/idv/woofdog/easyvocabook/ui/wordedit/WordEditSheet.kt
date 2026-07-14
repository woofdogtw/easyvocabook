package tw.idv.woofdog.easyvocabook.ui.wordedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.idv.woofdog.easyvocabook.R
import tw.idv.woofdog.easyvocabook.ui.Labels

private val LANGUAGES = Labels.SUPPORTED_LANGUAGES
private val POS_EN = listOf("", "noun", "verb", "adjective", "adverb", "phrase")
private val POS_JA = listOf("", "名詞", "動詞", "い形容詞", "な形容詞", "副詞", "助詞", "句")
private fun posOptions(lang: String) = if (lang == "ja") POS_JA else POS_EN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditSheet(
    wordId: Long?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: WordEditViewModel = viewModel(),
) {
    LaunchedEffect(wordId) { vm.load(wordId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val langDisplayFor: (String) -> String = { code -> context.getString(Labels.langResId(code)) }
    val formDisplayFor: (String) -> String = { key ->
        Labels.formLabelResId(key)?.let { context.getString(it) } ?: key
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding().padding(bottom = 32.dp)) {
            // Title + close
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (wordId == null) R.string.word_edit_title_add else R.string.word_edit_title_edit),
                    style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(Modifier.height(12.dp))

            // Language dropdown
            DropdownField(
                label = stringResource(R.string.word_edit_language),
                options = LANGUAGES,
                selected = state.language,
                onSelect = { vm.setLanguage(it) },
                displayFor = langDisplayFor,
            )
            Spacer(Modifier.height(8.dp))

            // Word
            OutlinedTextField(
                value = state.word,
                onValueChange = { vm.setWord(it) },
                label = { Text(stringResource(R.string.word_edit_word)) },
                isError = state.errorWord,
                supportingText = if (state.errorWord) ({ Text(stringResource(R.string.word_edit_required_word)) }) else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.reading,
                onValueChange = { vm.setReading(it) },
                label = { Text(stringResource(R.string.word_edit_reading)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // POS
            DropdownField(
                label = stringResource(R.string.word_edit_pos),
                options = posOptions(state.language),
                selected = state.pos,
                onSelect = { vm.setPOS(it) },
                displayFor = { Labels.posDisplay(it) }
            )
            Spacer(Modifier.height(8.dp))

            // Primary meaning
            OutlinedTextField(
                value = state.primaryMeaning,
                onValueChange = { vm.setPrimaryMeaning(it) },
                label = { Text(stringResource(R.string.word_edit_meaning_primary)) },
                isError = state.errorMeaning,
                supportingText = if (state.errorMeaning) ({ Text(stringResource(R.string.word_edit_required_meaning)) }) else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Additional meanings
            Text(stringResource(R.string.word_edit_additional_meanings), style = MaterialTheme.typography.labelMedium)
            state.additionalMeanings.forEachIndexed { idx, meaning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = meaning,
                        onValueChange = { vm.setMeaning(idx, it) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.removeMeaning(idx) }) { Icon(Icons.Default.Close, null) }
                }
            }
            TextButton(onClick = { vm.addMeaning() }) { Text(stringResource(R.string.word_edit_add_meaning)) }
            Spacer(Modifier.height(8.dp))

            // Note
            OutlinedTextField(
                value = state.note,
                onValueChange = { vm.setNote(it) },
                label = { Text(stringResource(R.string.word_edit_note)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))

            // Word forms
            Text(stringResource(R.string.word_edit_word_forms), style = MaterialTheme.typography.labelMedium)
            state.wordForms.forEachIndexed { idx, form ->
                val canonicalLabels = Labels.formLabelsForLanguage(state.language)
                val labelOptions = if (form.label in canonicalLabels || form.label.isBlank())
                    canonicalLabels else listOf(form.label) + canonicalLabels
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DropdownField(
                        label = stringResource(R.string.word_edit_form_label),
                        options = labelOptions,
                        selected = form.label,
                        onSelect = { vm.setFormLabel(idx, it) },
                        displayFor = formDisplayFor,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.value,
                        onValueChange = { vm.setFormValue(idx, it) },
                        label = { Text(stringResource(R.string.word_edit_form_value)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.removeWordForm(idx) }) { Icon(Icons.Default.Close, null) }
                }
            }
            TextButton(onClick = { vm.addWordForm() }) { Text(stringResource(R.string.word_edit_add_form)) }
            Spacer(Modifier.height(8.dp))

            // Sentences
            Text(stringResource(R.string.word_edit_sentences), style = MaterialTheme.typography.labelMedium)
            state.sentences.forEachIndexed { idx, s ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.text,
                            onValueChange = { vm.setSentenceText(idx, it) },
                            label = { Text(stringResource(R.string.word_edit_sentence_text)) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.removeSentence(idx) }) { Icon(Icons.Default.Close, null) }
                    }
                    OutlinedTextField(
                        value = s.translation,
                        onValueChange = { vm.setSentenceTranslation(idx, it) },
                        label = { Text(stringResource(R.string.word_edit_sentence_translation)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            TextButton(onClick = { vm.addSentence() }) { Text(stringResource(R.string.word_edit_add_sentence)) }
            Spacer(Modifier.height(16.dp))

            // Footer buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = { vm.save(onSaved) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    displayFor: (String) -> String = { it },
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = displayFor(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(displayFor(opt).ifBlank { "—" }) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
