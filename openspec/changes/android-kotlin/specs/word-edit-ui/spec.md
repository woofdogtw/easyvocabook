## MODIFIED Requirements

### Requirement: Add/Edit word modal dialog
The system SHALL provide a platform-appropriate editing surface for creating and editing words:
- **PC (Rust/iced)**: a modal dialog
- **Android (Kotlin)**: a `ModalBottomSheet` (`WordEditSheet`) triggered by the FAB (add) or
  long-press → Edit (edit). The bottom sheet is scrollable to accommodate variable-length forms.

Both surfaces SHALL contain the same fixed fields and sections:

**Fixed fields** (always visible):
- Language (dropdown, required): options per supported language; remembers last-used value
- Word (text input, required)
- Reading (text input, optional)
- Primary meaning (text input, required): corresponds to `words.meaning`
- Additional meanings (0..N text inputs with [×] remove button; [＋ Add meaning] button):
  corresponds to `word_meanings`
- Part of speech (dropdown, optional): options switch based on selected language
- Note (text input, optional): corresponds to `words.note`

**Dynamic word_forms section**: suggested label fields shown based on language + part_of_speech.
Each suggested row shows the label name and an empty value input. User may remove suggested rows
with [×] or add custom rows with [＋ Add custom field].

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

### Requirement: Language memory for new-word dialog
When opening the add-word surface, the language field SHALL default to the last language
the user used when saving a word. The default SHALL be `en` on first use.

The last-used language SHALL be stored in a platform-appropriate persistent store:
- **PC (Rust)**: `settings.toml`
- **Android (Kotlin)**: `SharedPreferences` (`SP_LAST_LANGUAGE`)

#### Scenario: Last-used language remembered on Android
- **WHEN** the user saves a Japanese word on Android and then opens the add-word sheet again
- **THEN** the language dropdown defaults to `ja`
