## MODIFIED Requirements

### Requirement: Typing quiz UI (中翻英 / 中翻日)
For typing mode the system SHALL display:
- A randomly-chosen meaning from the word's full meaning set as the prompt (large text)
- One or more text input fields (fields depend on language + part_of_speech)
- **[Submit]** and **[Give Up / Show Answer]** buttons

After submission, the system SHALL display:
- Each field with a ✓ or ✗ indicator and the correct value, followed by its reading in
  parentheses when the field has one — for the base word field and every word form field alike,
  and regardless of whether that field was answered correctly. A field that carries only a
  reading shows that reading on its own, without empty parentheses or an empty prefix
- All valid synonym answers
- **[Next]** button to advance to the next card (no self-report; verdict is determined automatically)

#### Scenario: Typing prompt shows one random meaning
- **WHEN** the quiz engine selects a word with two meanings for a typing quiz
- **THEN** only one of the two meanings is shown as the prompt

#### Scenario: Conjugation fields for English verb
- **WHEN** an English verb is selected for 中翻英 typing mode
- **THEN** four fields are shown: base_form, past_tense, past_participle, gerund

#### Scenario: Reveal shows synonyms
- **WHEN** the user submits an answer and there are synonyms in the database
- **THEN** all valid synonym words are listed below the user's answer

#### Scenario: Give-up reveals readings for every field
- **WHEN** the user gives up on a JA verb card whose `masu_form` is `食べます` with reading `たべます`
- **THEN** the result shows that field's correct value together with its reading, so the learner
  can read the answer

#### Scenario: Correctly answered fields still show the answer
- **WHEN** the user answers a field correctly and the card is revealed
- **THEN** that field still shows its correct value, and its reading when it has one

#### Scenario: Field with only a reading shows the reading alone
- **WHEN** a revealed field has a reading but no value
- **THEN** the reading is shown on its own, with no empty parentheses or empty prefix

#### Scenario: Field without a reading shows value only
- **WHEN** a revealed field has a correct value but no reading
- **THEN** only the value is shown, with no empty parentheses

### Requirement: Multiple-choice quiz UI (英翻中 / 日翻中)
For multiple-choice mode the system SHALL display:
- `words.word` (and `words.reading` if present) as the question
- A subtitle: "Select all correct meanings"
- All correct meanings + distractor options as checkboxes (correct meanings are never truncated;
  only distractors are limited)
- **[Submit]** and **[Give Up / Show Answer]** buttons

After submission, the system SHALL mark each option with ✓ (correct) or ✗ (incorrect/missed)
and show **[Next]** to advance (no self-report). The answer view SHALL keep showing the word
with its reading, in the same form as the question view, so the reading remains visible after
giving up.

#### Scenario: All correct meanings always visible
- **WHEN** the quiz word has 5 correct meanings
- **THEN** all 5 appear as checkbox options (not capped)

#### Scenario: All correct meanings must be selected
- **WHEN** the word has 2 correct meanings among 4 options and the user selects only 1
- **THEN** the answer is marked incorrect after submission

#### Scenario: Options are shuffled
- **WHEN** the multiple-choice options are generated
- **THEN** the order of options is randomized each time

#### Scenario: Reading stays visible on the answer view
- **WHEN** the user gives up on a JA multiple-choice card for `雨` with reading `あめ`
- **THEN** the answer view shows `雨` together with `あめ`, not the word alone

### Requirement: Android typing quiz — Compose text inputs
On Android, the typing quiz card SHALL be implemented as a scrollable `Column` containing:
- A large `Text` showing the randomly-chosen meaning prompt
- One `OutlinedTextField` for the base word (always shown)
- Additional `OutlinedTextField`s for each required `word_form` field (based on language + part_of_speech);
  field labels SHALL use localized names (e.g. "過去式" for `past_tense` in Chinese locales)
- A row with `[Give Up]` (`TextButton`) and `[Submit]` (`Button`)
- A `⏭ Skip` `IconButton` in the top action bar

Each field remains a single input; the reading is an accepted alternative answer, not a second
input box.

After the user submits or gives up, the card transitions to a result view showing each field with
a ✓ or ✗ indicator, the correct value with its reading when present — shown for correctly and
incorrectly answered fields alike — and any valid synonyms, followed by a `[Next →]` button.

#### Scenario: Android typing quiz shows correct word_form fields
- **WHEN** an English verb is selected for a typing quiz on Android
- **THEN** the Compose UI renders four `OutlinedTextField`s: base_form, past_tense, past_participle, gerund

#### Scenario: Android typing quiz result shows all field verdicts
- **WHEN** the user submits a partially correct answer on Android
- **THEN** each field shows its ✓/✗ indicator and the correct value before [Next →] appears

#### Scenario: Android typing result shows form readings
- **WHEN** the user gives up on a JA verb card on Android and its forms have readings
- **THEN** each revealed field shows its value together with its reading

### Requirement: Android multiple-choice quiz — Compose checkboxes
On Android, the multiple-choice quiz card SHALL be implemented as a scrollable `LazyColumn`
containing:
- A large `Text` showing `words.word` (and `words.reading` in parentheses if present)
- A subtitle: "Select all correct meanings"
- One `Row { Checkbox(...); Text(meaning) }` per option (all correct meanings + distractors)
- A row with `[Give Up]` (`TextButton`) and `[Submit]` (`Button`)
- A `⏭ Skip` `IconButton` in the top action bar

After submission, each row SHALL be color-coded (correct ✓ / incorrect ✗) and the `[Next →]`
button SHALL appear. Options SHALL be shuffled before display. The result view SHALL render the
word heading with its reading in parentheses, matching the question view.

#### Scenario: Android MCQ options rendered as Compose checkboxes
- **WHEN** a multiple-choice quiz card is shown on Android
- **THEN** each meaning option is rendered as a `Checkbox` + `Text` row in a `LazyColumn`

#### Scenario: Android MCQ options are shuffled
- **WHEN** the multiple-choice options are generated on Android
- **THEN** the order is randomized; correct meanings are not always first

#### Scenario: Android MCQ result keeps the reading
- **WHEN** the user submits or gives up on a JA multiple-choice card whose word has a reading
- **THEN** the result view heading shows the word with its reading in parentheses
