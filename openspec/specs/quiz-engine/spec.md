# quiz-engine Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Weighted random word selection
The quiz engine SHALL select the next word using weighted random sampling from all words in
`DbTableMemory` that match the active language filter.

Weight formula:
- `practice_count == 0` → `weight = NEW_WEIGHT` (default: 3.0)
- `practice_count > 0` → `weight = BASE + (incorrect_rate × MULTIPLIER)`
  where `incorrect_rate = (practice_count - correct_count) / practice_count`

Default constants: `NEW_WEIGHT = 3.0`, `BASE = 1.0`, `MULTIPLIER = 3.0`.

Small-sample amplification (1 wrong attempt → weight 4.0) is intentional; words just failed
resurface quickly. No Laplace smoothing.

#### Scenario: New word selected with elevated weight
- **WHEN** the pool contains one new word (practice_count=0) and one fully-correct word (practice_count=10, correct_count=10)
- **THEN** the new word has weight 3.0 and the correct word has weight 1.0

#### Scenario: Heavily-wrong word outweighs new word
- **WHEN** the pool contains one new word and one word with practice_count=5, correct_count=0
- **THEN** the wrong word (weight 4.0) has higher selection probability than the new word (weight 3.0)

### Requirement: Quiz mode selection by language
The quiz engine SHALL determine the quiz mode based on `words.language` and available data:
- `en` words: 中翻英 (typing) or 英翻中 (multiple-choice), chosen randomly
- `ja` words: 中翻日 (typing) or 日翻中 (multiple-choice), chosen randomly

There is no separate flip-card mode. All quiz modes require the user to actively type or select
an answer. A "give up" action is available in every mode (see Give-up action requirement).

Cloze and conjugation-drill modes are deferred to a future change.

#### Scenario: English word gets EN quiz mode
- **WHEN** a word with `language = "en"` is selected for a quiz
- **THEN** the quiz mode is either 中翻英 or 英翻中

### Requirement: Give-up action
Every quiz mode SHALL provide a **[Give Up / Show Answer]** button. Activating it SHALL:
1. Immediately reveal the correct answer (all correct fields / meanings)
2. Count the attempt as incorrect: `practice_count += 1`, `practiced_at = now`, `correct_count` unchanged
3. The user is NOT asked to self-report; the result is always wrong

This replaces the self-report flip-card mode. Users who cannot answer can give up to see the answer
and have their miss recorded strictly.

#### Scenario: Give up counts as incorrect
- **WHEN** the user presses [Give Up / Show Answer] before submitting an answer
- **THEN** `practice_count` is incremented, `correct_count` is not, and the correct answer is shown

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

**Synonym acceptance**: if the user's base-word field matches any word in the database whose
meaning set intersects the prompt meaning, the answer is accepted. Conjugation fields are then
graded against **the word the user typed** (not the originally selected word). If that synonym
has no word_forms for the required fields, those fields are accepted as correct regardless of input.

**Grading**: all shown fields must be correct (or accepted) for the overall answer to count as correct.

**After reveal**: display each field with ✓/✗, list all valid synonyms.

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

### Requirement: Multiple-choice mode (英翻中 / 日翻中)
The engine SHALL display `words.word` (and `reading` if present). The correct answer set is the
union of `words.meaning` and all `word_meanings.meaning` for that word.

Options shown = ALL correct meanings (never truncated) + distractors to fill up to a maximum of
`max(correct_count + 3, 4)` total options. Distractors are individual meaning strings drawn from
other words, excluding any meaning that intersects the correct meaning set.

**Grading**: the selected set must exactly equal the correct set (no extra, no missing selections).

#### Scenario: All correct meanings always shown
- **WHEN** the quiz word has 5 correct meanings
- **THEN** all 5 correct meanings appear as options (not capped at 4)

#### Scenario: Multi-meaning word requires all to be selected
- **WHEN** the quiz word "bank" has meanings ["銀行", "河岸"] and 2 distractor meanings are shown
- **THEN** the user must select both "銀行" and "河岸" for the answer to be correct

#### Scenario: Synonym excluded from distractors
- **WHEN** drawing distractors for a word with meaning "放棄"
- **THEN** no meaning string that matches "放棄" appears as a distractor option

#### Scenario: Fewer distractors when pool is small
- **WHEN** only 1 other non-synonym word exists in the database
- **THEN** the quiz shows 2 options total (all correct meanings + 1 distractor)

### Requirement: Practice counter update
After each quiz answer or give-up, the engine SHALL update the selected word's counters:
- Always: `practice_count += 1`, `practiced_at = current Unix epoch second`
- If correct: `correct_count += 1`
- Give-up: treated as incorrect (practice_count only)

Updates SHALL be written to `DbTableSQLite` and reflected in `DbTableMemory`.

#### Scenario: Correct answer increments both counters
- **WHEN** the user answers correctly
- **THEN** `practice_count` and `correct_count` both increase by 1 and `practiced_at` is updated

#### Scenario: Incorrect answer or give-up increments only practice_count
- **WHEN** the user answers incorrectly or presses [Give Up]
- **THEN** `practice_count` increases by 1, `correct_count` is unchanged, `practiced_at` is updated

### Requirement: Synonym definition
Two words SHALL be considered synonyms if their meaning sets (union of `words.meaning` and all
`word_meanings.meaning`) have at least one element in common (exact string match).

#### Scenario: Two words with shared meaning are synonyms
- **WHEN** word A has meanings ["放棄", "拋棄"] and word B has meaning "放棄"
- **THEN** A and B are synonyms

