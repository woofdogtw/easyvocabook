package tw.idv.woofdog.easyvocabook.ui.wordlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter

enum class SortOrder { WORD_ASC, WORD_DESC, RATE_ASC, RATE_DESC }

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
                SortOrder.RATE_ASC -> raw.sortedWith(
                    compareBy(nullsFirst()) { w ->
                        if (w.practiceCount == 0) null else w.correctCount.toDouble() / w.practiceCount
                    }
                )
                SortOrder.RATE_DESC -> raw.sortedWith(
                    compareByDescending(nullsLast()) { w ->
                        if (w.practiceCount == 0) null else w.correctCount.toDouble() / w.practiceCount
                    }
                )
            }
            _state.value = s.copy(words = sorted)
        }
    }

    fun reload() = refresh()
}
