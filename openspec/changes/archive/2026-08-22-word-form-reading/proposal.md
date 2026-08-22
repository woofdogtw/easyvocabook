## Why

Readings (kana / phonetics) are currently a privilege of the base form only. The `words`
table has both `word` and `reading`, but `word_forms` has a single `value` column. One
missing column causes three user-visible gaps: other word forms cannot record a reading,
the quiz refuses a reading typed for any form except the base one, and the answer side
reveals kanji without telling the learner how to read it — defeating the purpose of
giving up to learn the answer.

## What Changes

- **BREAKING** `word_forms` gains a nullable `reading` column; DB schema version goes
  **v1 → v2**. A v2 file cannot be opened by a v1 app (existing migration guard), so both
  platforms ship the update together.
- Add/Edit word: every word form gets a reading input alongside its value, mirroring the
  base form. Available for all languages; optional to fill. A row is discarded only when its
  value and reading are both empty. (both)
- Quiz typing mode: a form is graded correct when the input matches **either** its non-empty
  value **or** its non-empty reading. This removes the hard-coded "only the base form accepts a
  reading" special case, and pins down the comparison semantics (trim, Unicode case folding, no
  kana folding) that the two platforms currently implement differently. (both)
- Quiz answer side: reveal `value（reading）` for the base form and every word form, whether
  the field was answered correctly or not, so a given-up card teaches the reading too. (both)
- Retire `hiragana` and `phonetic` — the two labels that existed only to carry a
  pronunciation — from the canonical vocabulary, now that every form has its own `reading`.
  `kanji` and `pitch_accent` stay: they record a written form and an accent pattern, not a
  reading. Retired labels keep their locale strings and are still accepted on input. (both)
- Homophone matching drops its `word_forms.phonetic` fallback, which neither platform ever
  implemented, and the specification is corrected to describe the same-spelling fallback that
  Android actually uses. (both)
- Fix an existing drift as part of the same edit: the Rust canonical Japanese label list is
  missing `negative` and `past`, so labels the app itself suggests cannot be picked from the
  Rust dropdown. This only widens a dropdown and changes no behavior. (PC)

## Capabilities

### New Capabilities

None — this change generalizes existing behavior rather than introducing a new capability.

### Modified Capabilities

- `db-schema`: `word_forms` gains `reading`; schema version becomes 2, with a v1→v2 migration
  that is driven by `db_info.version` and writes the new version back on completion.
- `db-layer`: `WordForm` carries a reading through the DB interface and both implementations;
  the canonical `word_forms` label vocabulary drops the reading-carrying labels and gains the
  two labels missing from the Rust list.
- `word-edit-ui`: each word form row accepts a reading in addition to its value.
- `quiz-engine`: typing-mode grading accepts value or reading for every form, not just the
  base form, with defined comparison semantics.
- `quiz-ui`: the answer side displays readings for the base form and all word forms.
- `word-list-ui`: homophone matching no longer names the retired `phonetic` label, the existing
  same-spelling fallback is written down, and its comparison semantics are tied to the quiz's.
- `cloud-sync`: the version-guard scenario is stated without hard-coding the current version.

## Non-goals

- No automatic data migration of existing `hiragana` / `phonetic` form rows into the new
  `reading` column. The migration only adds the column; such rows keep working as ordinary
  word forms.
- No removal of the retired labels' locale strings, and no rejection of those labels on
  input — custom labels remain accepted per the existing schema contract.
- No change to how the base form's own `words.reading` is stored or displayed elsewhere
  (word list, search, multiple-choice question side already show it).
- No reading-related changes to multiple-choice grading, which is meaning-based.
- No auto-derivation of a form's reading from the base form's reading.
- No kana normalization in answer comparison (hiragana and katakana stay distinct); only
  trimming and Unicode case folding are specified.
- No Rust implementation of the homophone dialog or the More info view, both of which are still
  stubs; that gap is recorded, not closed, by this change.
- No alignment of the `word_forms` **suggestion table** between platforms. That table also drives
  which fields the typing quiz asks for, so aligning it would change Android quiz behavior and
  require rewriting the quiz-engine field list — unrelated to per-form readings, and left to a
  separate change.

## Impact

- **Schema**: `word_forms` table; `db_info.version` 1 → 2; new migration path in both
  implementations. `doc/schema.md` needs a Migrations section and a version bump.
- **Rust (PC)**: `db/schema.rs` (`CURRENT_VERSION`, create DDL, `migrate()`), `db/mod.rs`
  (version check and write-back), `db/types.rs` (`WordForm`), `db/sqlite.rs` + `db/memory.rs`
  (column I/O), `db/labels.rs` (label lists), `quiz/engine.rs` (`grade_typing`,
  `ConjugationField`, typing-mode word display), `ui/mod.rs` (edit state, messages, save
  mapping), `ui/word_edit.rs` (view), `ui/quiz.rs` (reveal).
- **Kotlin (Android)**: `data/model/Models.kt` (`WordForm`), `DbTableSQLite` (version source,
  migration, column I/O) + `DbTableMemory`, `ui/Labels.kt`, `quiz/QuizEngine.kt` (`gradeTyping`
  — drop the `label == "word"` special case), `ui/wordedit/*` (form reading input),
  `ui/quiz/QuizScreen.kt` (answer-side reading, always-shown correct values).
- **Sync**: unchanged in mechanism, but a v2 file is refused by a v1 peer, and a v1 file
  downloaded onto an updated app must migrate on the next open — both platforms must be
  updated before syncing.
- **Tests**: migration tests (including a file whose `PRAGMA user_version` disagrees with
  `db_info.version`), form-reading round-trip tests, and grading tests for reading-per-form.
