# quiz-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Quiz tab as default view
The Quiz tab SHALL be the first tab and the default view shown when the application starts.
When no quizzable words exist, the tab SHALL display an empty-state message.

#### Scenario: App starts on Quiz tab
- **WHEN** the application launches with words in the database
- **THEN** the Quiz tab is active and a quiz card is displayed

#### Scenario: Empty state when no words
- **WHEN** the database contains no words or the active language filter yields no words
- **THEN** the Quiz tab shows: "No words to quiz. Go to Word List to add some." with a link to the Word List tab

### Requirement: Language filter on Quiz tab
The Quiz tab SHALL provide a language filter dropdown (All / English / Japanese / …) that
restricts the pool from which quiz words are drawn.

#### Scenario: Filter to English only
- **WHEN** the user selects "English" in the language filter
- **THEN** only English words are sampled for the quiz

### Requirement: Give-up action (all modes)
Every quiz card SHALL display a **[Give Up / Show Answer]** button alongside the primary input.
Pressing it SHALL immediately reveal the correct answer and record the attempt as incorrect
(no self-report dialog). The button is available at any time before the user submits an answer.

#### Scenario: Give-up reveals answer without self-report
- **WHEN** the user presses [Give Up / Show Answer]
- **THEN** the correct answer is revealed, the attempt is counted as incorrect, and [Next] is shown

### Requirement: Typing quiz UI (中翻英 / 中翻日)
For typing mode the system SHALL display:
- A randomly-chosen meaning from the word's full meaning set as the prompt (large text)
- One or more text input fields (fields depend on language + part_of_speech)
- **[Submit]** and **[Give Up / Show Answer]** buttons

After submission, the system SHALL display:
- Each field with a ✓ or ✗ indicator and the correct value
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

### Requirement: Multiple-choice quiz UI (英翻中 / 日翻中)
For multiple-choice mode the system SHALL display:
- `words.word` (and `words.reading` if present) as the question
- A subtitle: "Select all correct meanings"
- All correct meanings + distractor options as checkboxes (correct meanings are never truncated;
  only distractors are limited)
- **[Submit]** and **[Give Up / Show Answer]** buttons

After submission, the system SHALL mark each option with ✓ (correct) or ✗ (incorrect/missed)
and show **[Next]** to advance (no self-report).

#### Scenario: All correct meanings always visible
- **WHEN** the quiz word has 5 correct meanings
- **THEN** all 5 appear as checkbox options (not capped)

#### Scenario: All correct meanings must be selected
- **WHEN** the word has 2 correct meanings among 4 options and the user selects only 1
- **THEN** the answer is marked incorrect after submission

#### Scenario: Options are shuffled
- **WHEN** the multiple-choice options are generated
- **THEN** the order of options is randomized each time

### Requirement: Skip to next card
The Quiz tab action bar SHALL contain a **⏭ Skip** button that discards the current card and
draws a new one from the weighted pool without recording any counter update. This is distinct
from Give Up (which records a wrong answer).

#### Scenario: Skip advances without recording
- **WHEN** the user taps ⏭ Skip
- **THEN** a new card is drawn; `practice_count` and `correct_count` are not changed

### Requirement: Android quiz screen — Compose structure
On Android, the quiz screen SHALL be implemented as a Composable function backed by a
`QuizViewModel` that exposes a `StateFlow<QuizUiState>`. The Composable SHALL call
`collectAsStateWithLifecycle()` to observe state and recompose on each state update.

The quiz screen SHALL be the first tab in the `NavigationBar` (leftmost, index 0) and SHALL be
the launch destination of the `NavHost`. When the composable enters composition it SHALL call
`ViewModel.startQuiz()` to draw the first card.

#### Scenario: Android quiz screen is launch destination
- **WHEN** the Android app starts
- **THEN** the quiz screen Composable is the active destination and a quiz card is rendered (or empty-state if no words)

### Requirement: Android quiz language filter — Compose dropdown
On Android, the language filter on the quiz screen SHALL be implemented as an `ExposedDropdownMenuBox`
(Material 3) showing the localized "All languages" label plus one entry per supported language
(`en`, `ja`), displayed with localized names (e.g. "英文" / "日文" in Chinese locales).
Selecting a value SHALL call `ViewModel.setLanguageFilter(code)` which triggers
re-sampling from the filtered pool.

#### Scenario: Android language filter restricts pool
- **WHEN** the user selects "Japanese" from the dropdown on the Android quiz screen
- **THEN** `QuizViewModel` resamples only from words with `language = "ja"`

### Requirement: Android typing quiz — Compose text inputs
On Android, the typing quiz card SHALL be implemented as a scrollable `Column` containing:
- A large `Text` showing the randomly-chosen meaning prompt
- One `OutlinedTextField` for the base word (always shown)
- Additional `OutlinedTextField`s for each required `word_form` field (based on language + part_of_speech);
  field labels SHALL use localized names (e.g. "過去式" for `past_tense` in Chinese locales)
- A row with `[Give Up]` (`TextButton`) and `[Submit]` (`Button`)
- A `⏭ Skip` `IconButton` in the top action bar

After the user submits or gives up, the card transitions to a result view showing each field with
a ✓ or ✗ indicator, the correct value, and any valid synonyms, followed by a `[Next →]` button.

#### Scenario: Android typing quiz shows correct word_form fields
- **WHEN** an English verb is selected for a typing quiz on Android
- **THEN** the Compose UI renders four `OutlinedTextField`s: base_form, past_tense, past_participle, gerund

#### Scenario: Android typing quiz result shows all field verdicts
- **WHEN** the user submits a partially correct answer on Android
- **THEN** each field shows its ✓/✗ indicator and the correct value before [Next →] appears

### Requirement: Android typing quiz — single-line inputs with keyboard navigation
Each `OutlinedTextField` in the typing quiz card SHALL use `singleLine = true` to prevent
multi-line input. The soft keyboard SHALL support sequential field navigation via the IME action
button:
- All fields except the last SHALL use `ImeAction.Next`; pressing it moves focus to the next field
  via `FocusRequester`.
- The last field SHALL use `ImeAction.Done`; pressing it moves focus to the `[Submit]` button
  (via `FocusRequester`) and hides the soft keyboard.

#### Scenario: IME Next moves focus to the following field
- **WHEN** the user presses the keyboard's Next button on a non-last typing field
- **THEN** focus moves to the immediately following `OutlinedTextField`

#### Scenario: IME Done on last field focuses submit and hides keyboard
- **WHEN** the user presses the keyboard's Done button on the last typing field
- **THEN** focus moves to the `[Submit]` button and the soft keyboard is dismissed

### Requirement: Android multiple-choice quiz — Compose checkboxes
On Android, the multiple-choice quiz card SHALL be implemented as a scrollable `LazyColumn`
containing:
- A large `Text` showing `words.word` (and `words.reading` in parentheses if present)
- A subtitle: "Select all correct meanings"
- One `Row { Checkbox(...); Text(meaning) }` per option (all correct meanings + distractors)
- A row with `[Give Up]` (`TextButton`) and `[Submit]` (`Button`)
- A `⏭ Skip` `IconButton` in the top action bar

After submission, each row SHALL be color-coded (correct ✓ / incorrect ✗) and the `[Next →]`
button SHALL appear. Options SHALL be shuffled before display.

#### Scenario: Android MCQ options rendered as Compose checkboxes
- **WHEN** a multiple-choice quiz card is shown on Android
- **THEN** each meaning option is rendered as a `Checkbox` + `Text` row in a `LazyColumn`

#### Scenario: Android MCQ options are shuffled
- **WHEN** the multiple-choice options are generated on Android
- **THEN** the order is randomized; correct meanings are not always first

