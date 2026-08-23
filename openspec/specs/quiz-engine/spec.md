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
- JA verb: dictionary_form, masu_form, ta_form, te_form, nai_form, transitive_pair,
  and the transitivity type
- JA i-adj: te_form, negative, past
- JA na-adj: te_form, negative
- All others, including JA nouns: base word field only

No field is added from a word's own data outside this table. A `particle` word form records the
particle a verb takes (〜に会う), which is worth learning, but neither platform ever implemented
the data-driven rule that once promised to quiz it and no word in any database carries the
label. Asking it belongs with the equivalent English question — which preposition a word takes —
and is designed as one feature rather than left as a rule nothing honours.

Each field has a single input; the reading is an accepted alternative answer, not a second
input box.

**Japanese verb questions**: for `language = "ja"` and `part_of_speech = "verb"` the engine
SHALL additionally ask two questions, and both SHALL count toward the verdict:

- **Paired verb** (`transitive_pair`): a text field, graded like any other word form. A verb
  with no partner has no such row, so the expectation is empty and the answer must be left
  blank — see Unspecified fields below.
- **Transitivity type**: a three-way choice between 自動詞, 他動詞, and 自他両用, graded against
  the word's recorded type. It is a choice rather than free text, so trimming and case folding
  do not apply.

Neither question SHALL appear for any other language. The verb group is recorded on the word but
is NOT asked.

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

**Empty expectations**: when a field of the **originally selected word** has neither a value
nor a reading, the answer SHALL be correct only if it is also empty. Recording nothing is itself
the answer — the verb has no partner, the noun has no counter — so typing something is wrong.
A field with a value but no reading remains a real expectation graded on its value.

The leniency for synonyms below is the one exception, and it exists for a different reason: it
avoids penalising a correct synonym that simply has no data of its own.

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

#### Scenario: Empty expectation on the target word requires an empty answer
- **WHEN** a Japanese verb has no `transitive_pair` row and the user types anything in that field
- **THEN** the field is graded incorrect, and the overall answer is incorrect

#### Scenario: Leaving a partnerless verb blank is correct
- **WHEN** the same verb is shown and the user leaves the paired-verb field empty
- **THEN** the field is graded correct

#### Scenario: Empty expectation applies beyond the paired verb
- **WHEN** any shown field of the selected word has neither a value nor a reading and the user
  types anything into it
- **THEN** the field is graded incorrect

#### Scenario: Japanese nouns are quizzed on the word alone
- **WHEN** a Japanese noun is selected for a typing quiz
- **THEN** only the base word field is shown, with no counter or particle field

#### Scenario: Transitivity type is graded
- **WHEN** a verb recorded as 他動詞 is quizzed and the user chooses 自動詞
- **THEN** the type question is incorrect, and the overall answer is incorrect

#### Scenario: Ambitransitive is a distinct answer
- **WHEN** a verb recorded as 自他両用 is quizzed and the user chooses 自動詞
- **THEN** the type question is incorrect

#### Scenario: Verb questions are Japanese-only
- **WHEN** an English verb is quizzed
- **THEN** neither the paired-verb field nor the transitivity choice is shown

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

#### Scenario: Recording a particle does not create a question
- **WHEN** a JA word has a `particle` entry in word_forms
- **THEN** the typing quiz shows only the fields its language and part of speech call for, and
  does not add a particle field — recording a particle does not by itself make it a question

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
