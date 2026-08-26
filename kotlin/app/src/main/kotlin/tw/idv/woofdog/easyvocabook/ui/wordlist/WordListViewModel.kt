package tw.idv.woofdog.easyvocabook.ui.wordlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.ui.Labels
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter

// Class replaces correct rate here. The Android row does not show a percentage, and sorting on a
// value the list never displays produces an order the user cannot account for. The Class badge is
// on screen, so sorting by it is legible. The desktop still sorts by rate, where the number shows.
private fun classSortKey(w: WordEntry): String =
    Labels.classOf(w.language, w.partOfSpeech, w.transitivity)
        ?.let { "${it.namespace}:${it.key}" }
        ?: "\uFFFF"   // words with no class sort last, behind both blocks

enum class SortOrder { WORD_ASC, WORD_DESC, CLASS_ASC, CLASS_DESC }

data class WordListUiState(
    val words: List<WordEntry> = emptyList(),
    val languageFilter: String? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.WORD_ASC,
    val syncInProgress: Boolean = false,
    val syncMessage: String? = null,
)

class WordListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository.get(application)

    private val _state = MutableStateFlow(WordListUiState())
    val state: StateFlow<WordListUiState> = _state

    init {
        viewModelScope.launch {
            repo.initialize()
            refresh()
        }
    }

    fun setLanguageFilter(lang: String?) {
        _state.value = _state.value.copy(languageFilter = lang)
        refresh()
    }

    fun setSearchQuery(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
        refresh()
    }

    fun setSortOrder(order: SortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
        refresh()
    }

    fun deleteWord(id: Long) {
        viewModelScope.launch {
            repo.deleteWord(id)
            refresh()
        }
    }

    fun clearStats() {
        viewModelScope.launch {
            repo.clearPracticeStats()
            refresh()
        }
    }

    fun setSyncInProgress(inProgress: Boolean, message: String? = null) {
        _state.value = _state.value.copy(syncInProgress = inProgress, syncMessage = message)
    }

    private fun refresh() {
        viewModelScope.launch {
            val s = _state.value
            val filter = WordFilter(
                language = s.languageFilter,
                query = s.searchQuery.takeIf { it.isNotBlank() },
            )
            val raw = repo.memory.listWords(filter)
            val sorted = when (s.sortOrder) {
                SortOrder.WORD_ASC -> raw.sortedBy { it.word.lowercase() }
                SortOrder.WORD_DESC -> raw.sortedByDescending { it.word.lowercase() }
                // Unpracticed words (practice_count == 0) sort first in ASC, last in DESC,
                // mirroring the desktop behaviour so users can spot unstudied words easily.
                // Sorted on `namespace:key` so part-of-speech and transitivity badges form
                // separate blocks rather than interleaving, matching the desktop.
                SortOrder.CLASS_ASC -> raw.sortedBy { classSortKey(it) }
                SortOrder.CLASS_DESC -> raw.sortedByDescending { classSortKey(it) }
            }
            _state.value = s.copy(words = sorted)
        }
    }

    fun reload() = refresh()
}
