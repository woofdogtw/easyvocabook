# word-edit-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Add/Edit word modal dialog
The system SHALL provide a platform-appropriate editing surface for creating and editing words:
- **PC (Rust/iced)**: a modal dialog
- **Android (Kotlin)**: a `ModalBottomSheet` (`WordEditSheet`) triggered by the FAB (add) or
  long-press → Edit (edit). The bottom sheet is scrollable to accommodate variable-length forms.

Both surfaces SHALL contain the same fixed fields and sections:

**Fixed fields** (always visible):
- Language (dropdown, required): supported languages are `en` (English) and `ja` (Japanese);
  option labels are localized (e.g. "英文" / "日文" in Chinese locales); remembers last-used value
- Word (text input, required)
- Reading (text input, optional)
- Primary meaning (text input, required): corresponds to `words.meaning`
- Additional meanings (0..N text inputs with [×] remove button; [＋ Add meaning] button):
  corresponds to `word_meanings`
- Part of speech (dropdown, optional): options switch based on selected language; labels are
  localized — in Chinese locales displayed as "中文 (english_key)" (e.g. "名詞 (noun)")
- Note (text input, optional): corresponds to `words.note`

**Dynamic word_forms section**: suggested label fields shown based on language + part_of_speech.
Each suggested row shows a **label dropdown** (options = canonical label list for the selected
language, localized display names) and a value text input. User may remove rows with [×] or add
custom rows with [＋ Add custom field] (custom label is a free-text input).

On Android (`WordEditSheet`), the sheet SHALL apply `imePadding()` so the soft keyboard does not
obscure the active field.

**Sentences section**: 0..N rows each with a sentence text input and an optional translation
input. [＋ Add sentence] appends a new row; [×] removes a row.

**Footer**: [Cancel] and [Save] buttons.

#### Scenario: Android WordEditSheet opens for add via FAB
- **WHEN** the user taps the FAB on the Android word list screen
- **THEN** `WordEditSheet` slides up as a `ModalBottomSheet` with all fields empty (except language defaulting to last-used)

#### Scenario: Android WordEditSheet opens pre-filled for edit
- **WHEN** the user long-presses a word and selects Edit on Android
- **THEN** `WordEditSheet` opens with all existing fields, meanings, forms, and sentences populated

#### Scenario: Save with required fields missing
- **WHEN** the user taps Save with the Word or primary Meaning field empty
- **THEN** the empty required field is highlighted and the save is not performed

#### Scenario: Save creates word with all sub-records
- **WHEN** the user fills all fields and taps Save for a new word
- **THEN** `words`, `word_meanings`, `word_forms`, and `sentences` rows are all created in one transaction

### Requirement: Part-of-speech dropdown options per language
The part-of-speech dropdown SHALL show different options based on the selected language:

**English** (`en`): noun, verb, adjective, adverb, pronoun, preposition, conjunction, interjection, other

**Japanese** (`ja`): noun (名詞), verb (動詞), i-adj (い形容詞), na-adj (な形容詞), adverb (副詞),
particle (助詞), aux-verb (助動詞), conjunction (接続詞), other

The value stored in `words.part_of_speech` SHALL be the language-neutral key (e.g., `i-adj`),
not the display string.

#### Scenario: Switching language resets part-of-speech
- **WHEN** the user changes the language dropdown from `en` to `ja`
- **THEN** the part-of-speech dropdown updates to show Japanese options

### Requirement: word_forms suggestions by language and POS
When the user selects a language + part_of_speech combination, the system SHALL automatically
populate the word_forms section with suggested (empty) label rows based on the canonical label
vocabulary:

| Language | POS | Suggested labels |
|----------|-----|-----------------|
| en | verb | base_form, past_tense, past_participle, gerund |
| en | noun | singular, plural |
| en | adjective | comparative, superlative |
| ja | verb | dictionary_form, masu_form, ta_form, te_form, nai_form |
| ja | i-adj | te_form, negative, past |
| ja | na-adj | te_form, negative |
| ja | noun | counter, particle |

Suggestions appear as pre-labelled empty rows. The user may fill, remove, or add custom rows.

#### Scenario: Suggestions appear on POS change
- **WHEN** the user selects language=en, part_of_speech=verb
- **THEN** four word_forms rows appear: base_form, past_tense, past_participle, gerund (all empty)

#### Scenario: Changing POS replaces suggestions
- **WHEN** the user changes part_of_speech from verb to noun (with no values filled in yet)
- **THEN** the verb suggestion rows are replaced with singular/plural rows

### Requirement: Language memory for new-word dialog
When opening the add-word surface, the language field SHALL default to the last language
the user used when saving a word. The default SHALL be `en` on first use.

The last-used language SHALL be stored in a platform-appropriate persistent store:
- **PC (Rust)**: `settings.toml`
- **Android (Kotlin)**: `SharedPreferences` (`SP_LAST_LANGUAGE`)

#### Scenario: Last-used language remembered on Android
- **WHEN** the user saves a Japanese word on Android and then opens the add-word sheet again
- **THEN** the language dropdown defaults to `ja`

### Requirement: word_meanings deduplication at input
If the user enters the same text in two meaning fields (primary or additional), the system SHALL
silently ignore the duplicate on save (`INSERT OR IGNORE`).

#### Scenario: Duplicate meaning not saved twice
- **WHEN** the user types "放棄" in both the primary meaning and an additional meaning field
- **THEN** only one "放棄" meaning is stored for that word

