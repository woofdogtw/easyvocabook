package tw.idv.woofdog.easyvocabook.ui.wordedit

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.MainActivity
import tw.idv.woofdog.easyvocabook.data.model.*
import tw.idv.woofdog.easyvocabook.quiz.WordFormLabels

data class FormField(val label: String, val value: String)

data class SentenceField(val text: String, val translation: String)

data class WordEditUiState(
    val wordId: Long? = null,
    val language: String = "en",
    val word: String = "",
    val reading: String = "",
    val pos: String = "",
    val primaryMeaning: String = "",
    val additionalMeanings: List<String> = emptyList(),
    val note: String = "",
    val wordForms: List<FormField> = emptyList(),
    val sentences: List<SentenceField> = emptyList(),
    val errorWord: Boolean = false,
    val errorMeaning: Boolean = false,
    val isSaving: Boolean = false,
)

class WordEditViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository.get(application)

    private val _state = MutableStateFlow(WordEditUiState())
    val state: StateFlow<WordEditUiState> = _state

    fun load(wordId: Long?) {
        if (wordId == null) {
            val prefs = getApplication<Application>().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val lastLang = prefs.getString(MainActivity.SP_LAST_LANGUAGE, "en") ?: "en"
            _state.value = WordEditUiState(language = lastLang, wordForms = suggestedForms(lastLang, ""))
            return
        }
        viewModelScope.launch {
            val w = repo.sqlite.getWord(wordId) ?: return@launch
            _state.value = WordEditUiState(
                wordId = wordId,
                language = w.language,
                word = w.word,
                reading = w.reading ?: "",
                pos = w.partOfSpeech ?: "",
                primaryMeaning = w.meaning,
                additionalMeanings = w.wordMeanings.map { it.meaning },
                note = w.note ?: "",
                wordForms = w.wordForms.map { FormField(it.label, it.value) },
                sentences = w.sentences.map { SentenceField(it.sentence, it.translation ?: "") },
            )
        }
    }

    fun setLanguage(lang: String) {
        val prefs = getApplication<Application>().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(MainActivity.SP_LAST_LANGUAGE, lang).apply()
        _state.value = _state.value.copy(language = lang, wordForms = suggestedForms(lang, _state.value.pos))
    }

    fun setWord(v: String) { _state.value = _state.value.copy(word = v, errorWord = false) }
    fun setReading(v: String) { _state.value = _state.value.copy(reading = v) }
    fun setPOS(v: String) {
        _state.value = _state.value.copy(pos = v,
            wordForms = mergeForms(_state.value.wordForms, suggestedForms(_state.value.language, v)))
    }
    fun setPrimaryMeaning(v: String) { _state.value = _state.value.copy(primaryMeaning = v, errorMeaning = false) }
    fun setNote(v: String) { _state.value = _state.value.copy(note = v) }

    fun addMeaning() { _state.value = _state.value.copy(additionalMeanings = _state.value.additionalMeanings + "") }
    fun setMeaning(idx: Int, v: String) {
        val updated = _state.value.additionalMeanings.toMutableList().apply { this[idx] = v }
        _state.value = _state.value.copy(additionalMeanings = updated)
    }
    fun removeMeaning(idx: Int) {
        val updated = _state.value.additionalMeanings.toMutableList().apply { removeAt(idx) }
        _state.value = _state.value.copy(additionalMeanings = updated)
    }

    fun addWordForm() { _state.value = _state.value.copy(wordForms = _state.value.wordForms + FormField("", "")) }
    fun setFormLabel(idx: Int, v: String) {
        val updated = _state.value.wordForms.toMutableList().apply { this[idx] = this[idx].copy(label = v) }
        _state.value = _state.value.copy(wordForms = updated)
    }
    fun setFormValue(idx: Int, v: String) {
        val updated = _state.value.wordForms.toMutableList().apply { this[idx] = this[idx].copy(value = v) }
        _state.value = _state.value.copy(wordForms = updated)
    }
    fun removeWordForm(idx: Int) {
        val updated = _state.value.wordForms.toMutableList().apply { removeAt(idx) }
        _state.value = _state.value.copy(wordForms = updated)
    }

    fun addSentence() { _state.value = _state.value.copy(sentences = _state.value.sentences + SentenceField("", "")) }
    fun setSentenceText(idx: Int, v: String) {
        val updated = _state.value.sentences.toMutableList().apply { this[idx] = this[idx].copy(text = v) }
        _state.value = _state.value.copy(sentences = updated)
    }
    fun setSentenceTranslation(idx: Int, v: String) {
        val updated = _state.value.sentences.toMutableList().apply { this[idx] = this[idx].copy(translation = v) }
        _state.value = _state.value.copy(sentences = updated)
    }
    fun removeSentence(idx: Int) {
        val updated = _state.value.sentences.toMutableList().apply { removeAt(idx) }
        _state.value = _state.value.copy(sentences = updated)
    }

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        var hasError = false
        if (s.word.isBlank()) { _state.value = s.copy(errorWord = true); hasError = true }
        if (s.primaryMeaning.isBlank()) { _state.value = _state.value.copy(errorMeaning = true); hasError = true }
        if (hasError) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val now = System.currentTimeMillis() / 1000
            val data = WordEntry(
                id = s.wordId ?: 0L,
                word = s.word.trim(),
                reading = s.reading.trim().takeIf { it.isNotBlank() },
                meaning = s.primaryMeaning.trim(),
                partOfSpeech = s.pos.trim().takeIf { it.isNotBlank() },
                note = s.note.trim().takeIf { it.isNotBlank() },
                language = s.language,
                practiceCount = 0, correctCount = 0, createdAt = now, practicedAt = null,
                wordMeanings = s.additionalMeanings.filter { it.isNotBlank() }.map { WordMeaning(0, it.trim()) },
                wordForms = s.wordForms.filter { it.label.isNotBlank() }.map { WordForm(0, it.label, it.value) },
                sentences = s.sentences.filter { it.text.isNotBlank() }.map { Sentence(0, it.text, it.translation.takeIf { t -> t.isNotBlank() }) },
            )
            if (s.wordId == null) repo.createWord(data)
            else repo.updateWord(s.wordId, data)
            _state.value = _state.value.copy(isSaving = false)
            onSuccess()
        }
    }

    private fun suggestedForms(language: String, pos: String): List<FormField> =
        WordFormLabels.forWord(language, pos).map { FormField(it, "") }

    private fun mergeForms(existing: List<FormField>, suggested: List<FormField>): List<FormField> {
        val existingMap = existing.associate { it.label to it.value }
        return suggested.map { FormField(it.label, existingMap[it.label] ?: "") }
    }
}
