## MODIFIED Requirements

### Requirement: word_forms label vocabulary
The system SHALL define a canonical vocabulary of `word_forms` labels shared across all
implementations. Both platforms SHALL expose the same list. Labels are language-specific:

**English labels**: `singular`, `plural`, `base_form`, `past_tense`, `past_participle`, `gerund`,
`comparative`, `superlative`, `collocation`

**Japanese labels**: `dictionary_form`, `masu_form`, `ta_form`, `te_form`, `nai_form`,
`negative`, `past`, `particle`, `kanji`, `pitch_accent`, `counter`, `transitive_pair`, `origin`

Labels whose sole purpose was to carry a pronunciation — `phonetic` and `hiragana` — SHALL NOT
appear in the canonical vocabulary, because every word form now carries its own `reading`.
`kanji` (a written form) and `pitch_accent` (an accent pattern) are not readings and SHALL be
retained. The localized display names of the retired labels SHALL be kept, so that databases
still containing such rows show a translated label rather than a raw key.

Every label that the edit dialog can suggest for a language SHALL be present in that language's
canonical list, so that a suggested label is always selectable in the label dropdown.

UI SHOULD suggest these labels in the word-edit dialog based on `language` + `part_of_speech`.
Custom labels (outside this list) SHALL be accepted without error.

#### Scenario: English verb word_forms suggestions
- **WHEN** a word is added with `language = "en"` and `part_of_speech = "verb"`
- **THEN** the edit dialog suggests `base_form`, `past_tense`, `past_participle`, `gerund`

#### Scenario: Custom label accepted
- **WHEN** a user saves a word_form with label `my_custom_label`
- **THEN** it is stored without error

#### Scenario: Retired label still displays a localized name
- **WHEN** a database contains a `word_forms` row with label `hiragana`
- **THEN** the row is loaded without error and its label is displayed using the existing
  localized name rather than the raw key

#### Scenario: Suggested Japanese adjective labels are selectable
- **WHEN** the label dropdown is shown for a Japanese i-adjective, whose suggestions are
  `te_form`, `negative`, `past`
- **THEN** all three appear as options on both platforms

## ADDED Requirements

### Requirement: word_forms reading round-trip
Every word form SHALL carry an optional reading alongside its value. The DB interface and both
implementations (SQLite and in-memory) SHALL persist and return it unchanged. Both the value and
the reading SHALL be trimmed on save, on both platforms. A reading that is empty after trimming
SHALL be stored as SQL `NULL` and reported as absent.

#### Scenario: Word form reading is persisted
- **WHEN** a word is saved with a form of label `masu_form`, value `食べます`, reading `たべます`
- **AND** the word is loaded again
- **THEN** that form returns value `食べます` and reading `たべます`

#### Scenario: Word form without a reading
- **WHEN** a word is saved with a form that has a value but no reading
- **THEN** loading the word returns that form with an absent reading, and no error occurs

#### Scenario: Blank reading normalizes to absent
- **WHEN** a word is saved with a form whose reading is whitespace only
- **THEN** the stored reading is `NULL` and the loaded form reports no reading

#### Scenario: Form value is trimmed on save
- **WHEN** a word is saved with a form whose value has leading or trailing whitespace
- **THEN** the stored value has that whitespace removed

#### Scenario: In-memory and SQLite agree
- **WHEN** the same word with form readings is stored through the in-memory and the SQLite
  implementation
- **THEN** both return identical form values and readings
