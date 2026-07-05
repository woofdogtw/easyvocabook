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

