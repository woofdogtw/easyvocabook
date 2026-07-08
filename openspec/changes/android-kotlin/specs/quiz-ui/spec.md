## ADDED Requirements

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
(Material 3) showing "All", "English (en)", "Japanese (ja)", plus any other language present in
`DbTableMemory`. Selecting a value SHALL call `ViewModel.setLanguageFilter(code)` which triggers
re-sampling from the filtered pool.

#### Scenario: Android language filter restricts pool
- **WHEN** the user selects "Japanese" from the dropdown on the Android quiz screen
- **THEN** `QuizViewModel` resamples only from words with `language = "ja"`

### Requirement: Android typing quiz — Compose text inputs
On Android, the typing quiz card SHALL be implemented as a scrollable `Column` containing:
- A large `Text` showing the randomly-chosen meaning prompt
- One `OutlinedTextField` for the base word (always shown)
- Additional `OutlinedTextField`s for each required `word_form` field (based on language + part_of_speech)
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
