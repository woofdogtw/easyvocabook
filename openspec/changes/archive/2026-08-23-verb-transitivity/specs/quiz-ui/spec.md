## MODIFIED Requirements

### Requirement: Typing quiz UI (中翻英 / 中翻日)
For typing mode the system SHALL display:
- A randomly-chosen meaning from the word's full meaning set as the prompt (large text)
- One or more text input fields (fields depend on language + part_of_speech)
- For a Japanese verb, a three-way single choice for the transitivity type — 自動詞 / 他動詞 /
  自他両用 — with no option preselected, so an unanswered question is never credited as a guess
- **[Submit]** and **[Give Up / Show Answer]** buttons

After submission, the system SHALL display:
- Each field with a ✓ or ✗ indicator and the correct value, followed by its reading in
  parentheses when the field has one — for the base word field and every word form field alike,
  and regardless of whether that field was answered correctly. A field that carries only a
  reading shows that reading on its own, without empty parentheses or an empty prefix, and a
  field with no expected answer at all shows `-`, so that "there is nothing to answer" reads as
  an answer rather than an omission
- For a Japanese verb, its verb group — 五段 (I 類) / 一段 (II 類) / 不規則 — shown for
  reference and never marked correct or incorrect
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

#### Scenario: Type question rendered as three options
- **WHEN** a Japanese verb typing card is shown
- **THEN** three mutually exclusive transitivity options are offered, none of them preselected

#### Scenario: Unanswered type question is incorrect
- **WHEN** the user submits without choosing a type
- **THEN** the type question is marked incorrect

#### Scenario: Empty expected answer reveals a dash
- **WHEN** a revealed field has no expected value or reading, as for a verb with no partner
- **THEN** the field shows `-` rather than an empty space

#### Scenario: Verb group is revealed but not graded
- **WHEN** a Japanese verb card is revealed
- **THEN** its verb group is shown with no ✓ or ✗ indicator

#### Scenario: Type question absent for other languages
- **WHEN** an English word is quizzed
- **THEN** no transitivity choice is rendered

### Requirement: Android typing quiz — Compose text inputs
On Android, the typing quiz card SHALL be implemented as a scrollable `Column` containing:
- A large `Text` showing the randomly-chosen meaning prompt
- One `OutlinedTextField` for the base word (always shown)
- Additional `OutlinedTextField`s for each required `word_form` field (based on language + part_of_speech);
  field labels SHALL use localized names (e.g. "過去式" for `past_tense` in Chinese locales)
- For a Japanese verb, a `Row` of three `RadioButton`s for the transitivity type, none selected
  initially, and a read-only `Text` showing the verb group
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

#### Scenario: Android renders the transitivity choice
- **WHEN** a Japanese verb typing card is shown on Android
- **THEN** three `RadioButton`s appear for the type, with none selected

