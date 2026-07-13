# word-edit-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Add/Edit word modal dialog
The system SHALL provide a modal dialog for creating and editing words.
The dialog SHALL contain:

**Fixed fields** (always visible):
- Language (dropdown, required): options per supported language; remembers last-used value
- Word (text input, required)
- Reading (text input, optional)
- Primary meaning (text input, required)
- Additional meanings (0..N text inputs with [×] remove button; [＋ Add meaning] button)
- Part of speech (dropdown, optional): options switch based on selected language (see below)
- Note (text input, optional)

**Dynamic word_forms section**: suggested label fields shown based on language + part_of_speech
combination. Each suggested row shows a **label dropdown** (options = canonical label list for
the selected language, localized display names) and a value text input. User may remove suggested
rows with [×] or add custom rows with [＋ Add custom field].

**Sentences section**: 0..N rows each with a sentence text input and an optional translation
input. [＋ Add sentence] appends a new row; [×] removes a row.

**Footer**: [Cancel] and [Save] buttons.

#### Scenario: Dialog opens pre-filled for edit
- **WHEN** the user selects Edit from the context menu on an existing word
- **THEN** the dialog opens with all existing fields, meanings, forms, and sentences populated

#### Scenario: Save with required fields missing
- **WHEN** the user clicks Save with the Word or primary Meaning field empty
- **THEN** the dialog highlights the empty required field and does not save

#### Scenario: Save creates word with all sub-records
- **WHEN** the user fills all fields and clicks Save for a new word
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
When opening the dialog for a new word, the language field SHALL default to the last language
the user used when saving a word. The default SHALL be `en` on first use. This value SHALL be
stored in `settings.toml` (not in the database).

#### Scenario: Last-used language is remembered
- **WHEN** the user saves a Japanese word and then opens the dialog again for a new word
- **THEN** the language dropdown defaults to `ja`

### Requirement: word_meanings deduplication at input
If the user enters the same text in two meaning fields (primary or additional), the system SHALL
silently ignore the duplicate on save (`INSERT OR IGNORE`).

#### Scenario: Duplicate meaning not saved twice
- **WHEN** the user types "放棄" in both the primary meaning and an additional meaning field
- **THEN** only one "放棄" meaning is stored for that word

