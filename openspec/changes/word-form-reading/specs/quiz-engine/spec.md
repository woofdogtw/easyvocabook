## MODIFIED Requirements

### Requirement: Typing mode (中翻英 / 中翻日)
The engine SHALL display one randomly-chosen meaning from the word's full meaning set
(primary + word_meanings) as the prompt. The user types the target-language word.

**Fields shown** depend on `language` and `part_of_speech`:
- EN verb: base_form, past_tense, past_participle, gerund
- EN noun: singular (and plural if available)
- EN adjective: comparative, superlative
- JA verb: dictionary_form, masu_form, ta_form, te_form, nai_form
- JA i-adj: te_form, negative, past
- JA na-adj: te_form, negative
- JA (any with particle data): particle field added
- All others: base word field only

Each field has a single input; the reading is an accepted alternative answer, not a second
input box.

**Reading acceptance**: a field SHALL be graded correct when the input matches a **non-empty**
value **or** a **non-empty** reading. Only the strings the field actually carries participate in
matching: a field whose reading is absent is matched on its value alone, and a field whose value
is empty is matched on its reading alone. An empty expected string SHALL never match an empty
input. This applies uniformly to the base word field (matching `words.word` or `words.reading`)
and to every word form field.

**Comparison semantics**: the same comparison SHALL be used for every field and for both value
and reading. The user's input and the expected string are compared after trimming leading and
trailing whitespace from both — including full-width whitespace such as U+3000 — and ignoring
letter case using Unicode case folding, so that accented letters compare equal regardless of
case. No other normalization is applied — in particular hiragana and katakana are not folded
together, so they remain distinct answers.

**Unspecified fields**: a field counts as unspecified — and therefore accepts any input,
including none — only when both its value and its reading are empty. A field with a value but
no reading is a real expectation and is graded on its value.

**Synonym acceptance**: if the user's base-word field matches any word in the database whose
meaning set intersects the prompt meaning, the answer is accepted; the match may be on that
word's word or its reading. Conjugation fields are then graded against **the word the user
typed** (not the originally selected word), again accepting either value or reading. If that
synonym has no word_forms for the required fields, those fields are accepted as correct
regardless of input.

**Grading**: all shown fields must be correct (or accepted) for the overall answer to count as correct.

**After reveal**: the engine SHALL expose, for the base word and for every field, both the
correct value and its reading when one exists, so the UI can show how the answer is read. All
valid synonyms SHALL also be exposed.

#### Scenario: Word form reading accepted as the answer
- **WHEN** a JA verb quiz shows a `masu_form` field whose value is `食べます` and reading is `たべます`
- **AND** the user types `たべます`
- **THEN** the field is graded correct

#### Scenario: Word form value still accepted
- **WHEN** the same `masu_form` field is shown and the user types `食べます`
- **THEN** the field is graded correct

#### Scenario: Base word reading accepted
- **WHEN** the target word is `雨` with reading `あめ` and the user types `あめ` in the base field
- **THEN** the base field is graded correct, as it is when the user types `雨`

#### Scenario: Form without a reading is matched on value only
- **WHEN** a form field has a value but no reading and the user types something other than that value
- **THEN** the field is graded incorrect

#### Scenario: Reading-only field is matched on its reading
- **WHEN** a form field has no value but a reading `たべます`
- **AND** the user submits an empty answer for that field
- **THEN** the field is graded incorrect
- **AND** typing `たべます` grades it correct

#### Scenario: Case folding covers non-ASCII letters
- **WHEN** the expected value is `café` and the user types `CAFÉ`
- **THEN** the field is graded correct

#### Scenario: Surrounding whitespace is ignored
- **WHEN** the user types an otherwise correct answer with leading or trailing spaces, whether
  ASCII spaces or full-width spaces
- **THEN** the field is graded correct

#### Scenario: Kana scripts are not folded
- **WHEN** the expected reading is `たべます` and the user types `タベマス`
- **THEN** the field is graded incorrect

#### Scenario: Reveal exposes the base word reading
- **WHEN** a typing card for `雨` with reading `あめ` is revealed
- **THEN** the revealed base word carries both `雨` and `あめ`

#### Scenario: Synonym accepted with its own conjugations
- **WHEN** the prompt is "放棄", user types base_form="forsake", past_tense="forsook"
- **AND** "forsake" exists in DB with word_forms past_tense="forsook"
- **THEN** the answer is correct (graded against forsake's word_forms, not abandon's)

#### Scenario: Synonym with no word_forms accepts any conjugation input
- **WHEN** the user types a valid synonym that has no word_forms in the DB
- **THEN** the conjugation fields are accepted regardless of what was typed

#### Scenario: All fields must be correct
- **WHEN** an English verb quiz shows 4 conjugation fields and the user fills 3 correctly but one incorrectly
- **THEN** the overall answer is marked incorrect

#### Scenario: Japanese word with particle data adds particle field
- **WHEN** a JA word has a `particle` entry in word_forms
- **THEN** the typing quiz includes a particle field that must be filled correctly
