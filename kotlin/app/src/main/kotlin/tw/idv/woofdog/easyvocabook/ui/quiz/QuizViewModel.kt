package tw.idv.woofdog.easyvocabook.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter
import tw.idv.woofdog.easyvocabook.quiz.*

sealed class QuizUiState {
    object Loading : QuizUiState()
    object Empty : QuizUiState()
    data class TypingCard(val card: tw.idv.woofdog.easyvocabook.quiz.TypingCard, val inputs: List<String>) : QuizUiState()
    data class TypingResult(val result: tw.idv.woofdog.easyvocabook.quiz.TypingResult) : QuizUiState()
    data class McqCard(val card: tw.idv.woofdog.easyvocabook.quiz.McqCard, val selected: Set<String>) : QuizUiState()
    data class McqResult(val result: tw.idv.woofdog.easyvocabook.quiz.McqResult) : QuizUiState()
}

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository.get(application)
    private val engine = QuizEngine()

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state

    private var languageFilter: String? = null
    private var allWords: List<WordEntry> = emptyList()

    init {
        viewModelScope.launch {
            repo.initialize()
            allWords = repo.memory.allWords()
            drawNext()
        }
    }

    fun setLanguageFilter(language: String?) {
        languageFilter = language
        drawNext()
    }

    fun skip() = drawNext()

    fun submitTyping(inputs: List<String>) {
        val st = _state.value as? QuizUiState.TypingCard ?: return
        val result = engine.gradeTyping(st.card, inputs, allWords)
        updateStats(st.card.word, result.allCorrect)
        _state.value = QuizUiState.TypingResult(result)
    }

    fun giveUpTyping() {
        val st = _state.value as? QuizUiState.TypingCard ?: return
        val result = engine.gradeTyping(st.card, st.inputs, allWords)
        updateStats(st.card.word, isCorrect = false)
        _state.value = QuizUiState.TypingResult(result)
    }

    fun submitMcq(selected: Set<String>) {
        val st = _state.value as? QuizUiState.McqCard ?: return
        val result = engine.gradeMcq(st.card, selected)
        updateStats(st.card.word, result.allCorrect)
        _state.value = QuizUiState.McqResult(result)
    }

    fun giveUpMcq() {
        val st = _state.value as? QuizUiState.McqCard ?: return
        val result = engine.gradeMcq(st.card, emptySet())
        updateStats(st.card.word, isCorrect = false)
        _state.value = QuizUiState.McqResult(result)
    }

    fun next() = drawNext()

    fun updateTypingInput(index: Int, value: String) {
        val st = _state.value as? QuizUiState.TypingCard ?: return
        val newInputs = st.inputs.toMutableList().apply { this[index] = value }
        _state.value = st.copy(inputs = newInputs)
    }

    fun toggleMcqSelection(meaning: String) {
        val st = _state.value as? QuizUiState.McqCard ?: return
        val newSelected = if (meaning in st.selected) st.selected - meaning else st.selected + meaning
        _state.value = st.copy(selected = newSelected)
    }

    private fun drawNext() {
        allWords = repo.memory.allWords()
        val filter = WordFilter(language = languageFilter)
        val word = engine.nextWord(allWords, filter)
        if (word == null) {
            _state.value = QuizUiState.Empty
            return
        }
        // Alternate between typing and MCQ randomly (50/50)
        if ((System.nanoTime() % 2L) == 0L) {
            val card = engine.buildTypingCard(word)
            _state.value = QuizUiState.TypingCard(card, List(card.fields.size) { "" })
        } else {
            val card = engine.buildMcqCard(word, allWords)
            _state.value = QuizUiState.McqCard(card, emptySet())
        }
    }

    private fun updateStats(word: WordEntry, isCorrect: Boolean) {
        viewModelScope.launch {
            repo.updatePracticeStats(word.id, isCorrect)
            allWords = repo.memory.allWords()
        }
    }
}
