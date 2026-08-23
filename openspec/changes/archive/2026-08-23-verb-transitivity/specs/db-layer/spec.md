## ADDED Requirements

### Requirement: Verb transitivity and group
A word SHALL be able to carry two verb attributes describing the verb itself: a **transitivity
type** — `intransitive` (自動詞), `transitive` (他動詞), or `ambitransitive` (自他両用) — and a
**verb group** — `godan` (I 類), `ichidan` (II 類), or `irregular`. Both are optional and are
absent for non-verbs and for languages that do not distinguish them.

They are stored as language-neutral keys, the same convention `part_of_speech` follows, and
displayed through the locale tables. The DB interface and both implementations (SQLite and
in-memory) SHALL persist and return them unchanged.

A verb that is `ambitransitive` behaves as both readings of the same form (ドアが開く /
ドアを開く) and is a distinct value, not a way of saying "unknown".

#### Scenario: Verb attributes round-trip
- **WHEN** a word is saved with `transitivity = intransitive` and `verb_group = ichidan`
- **AND** the word is loaded again
- **THEN** both values are returned unchanged

#### Scenario: Non-verb carries neither attribute
- **WHEN** a noun is saved
- **THEN** loading it returns no transitivity and no verb group, and no error occurs

#### Scenario: In-memory and SQLite agree
- **WHEN** the same word is stored through the in-memory and the SQLite implementation
- **THEN** both return identical transitivity and verb group values

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

`transitive_pair` records the paired transitive or intransitive verb. A verb with no partner
SHALL have no `transitive_pair` row at all: because the field is always offered for Japanese
verbs, leaving it blank is a deliberate statement that no partner exists, not an unfilled gap.

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

#### Scenario: Verb with no partner has no transitive_pair row
- **WHEN** a Japanese verb is saved with its paired-verb field left blank
- **THEN** no `transitive_pair` row is stored for it
