## 1. Schema documentation

- [ ] 1.1 Update `doc/schema.md`: add `reading TEXT` to the `word_forms` DDL, change
      `Current version: **1**` to **2**, and add a Migrations section listing the v1→v2
      `ALTER TABLE` statement plus the `db_info.version` write-back
- [ ] 1.2 State in `doc/schema.md` that `db_info.version` is the only version authority and that
      `PRAGMA user_version` is not maintained, so neither platform may branch on it

## 2. Rust (PC) — database layer and migration

- [ ] 2.1 Add `reading: Option<String>` to `WordForm` in `rust/src/db/types.rs`
- [ ] 2.2 In `rust/src/db/schema.rs`: bump `CURRENT_VERSION` to 2, add `reading` to the
      `word_forms` create statement, and add a version-guarded v1→v2 step inside `migrate()`
      that skips the `ALTER TABLE` when the column already exists
- [ ] 2.3 Make `migrate()` write `db_info.version = CURRENT_VERSION` after applying steps, so
      that re-opening a migrated file runs no statement and cannot fail with `duplicate column name`
- [ ] 2.4 Read and write the new column in `rust/src/db/sqlite.rs` (word form select/insert),
      trimming both value and reading and storing `NULL` for an empty reading
- [ ] 2.5 Carry the reading through `rust/src/db/memory.rs` so both implementations round-trip
      identically
- [ ] 2.6 Add a migration test: open a v1 fixture, assert `db_info.version` becomes 2, the column
      exists, and pre-existing rows keep their label/value with `reading = NULL`
- [ ] 2.7 Add a test that opens the migrated database a second time and succeeds, proving the
      migration is not re-applied
- [ ] 2.8 Add round-trip tests for form readings in the SQLite and in-memory implementations,
      including a form with no reading, a whitespace-only reading normalizing to absent, and a
      value with surrounding whitespace being trimmed

## 3. Rust (PC) — labels, quiz engine, UI

- [ ] 3.1 In `rust/src/db/labels.rs`: remove `phonetic` from `EN_FORM_LABELS` and `hiragana` from
      `JA_FORM_LABELS`, keep `kanji` and `pitch_accent`, add the missing `negative` and `past` to
      `JA_FORM_LABELS`, and leave all `form_locale_key()` mappings intact
- [ ] 3.2 Add `expected_reading` to `ConjugationField` and populate it in
      `conjugation_fields_for()` in `rust/src/quiz/engine.rs`
- [ ] 3.3 Introduce the shared matching predicate — trim both sides, Unicode case folding (compare
      lowercased strings rather than `eq_ignore_ascii_case`), no kana folding — guarding both
      sides so an empty expected string never matches an empty input; use it for the base word
      (replacing the exact `==` used for readings) and for conjugation fields in `grade_typing`
- [ ] 3.4 Express "unspecified" as "value and reading are both empty", which subsumes the current
      "synonym has no form row" case rather than removing it — a synonym lacking that label still
      yields an empty pair and still accepts any input
- [ ] 3.5 Grade synonym forms against the synonym form's value and reading as well
- [ ] 3.6 Include the reading in the typing-mode reveal: build `word_display` for
      `QuizMode::Typing` with `format_word_display(target)` instead of `target.word.clone()`,
      and expose each field's correct value and reading
- [ ] 3.7 Add the reading to the word-edit state in `rust/src/ui/mod.rs`: widen
      `pub forms: Vec<(String, String)>` to carry a reading, add a `WordEditFormReading` message
      beside `WordEditFormLabel` / `WordEditFormValue`, and update the load and save mappings
- [ ] 3.8 Change the save filter in `rust/src/ui/mod.rs` to drop a form row only when its value
      and reading are both empty, so a reading-only row is kept
- [ ] 3.9 Render the reading input per word form row in `rust/src/ui/word_edit.rs`, and keep a
      non-canonical label (such as a retired one) selectable so it survives an edit
- [ ] 3.10 Show the readings on the answer side in `rust/src/ui/quiz.rs` for the base word and
      every field, whether answered correctly or not, showing a reading-only field's reading
      alone without empty parentheses
- [ ] 3.11 Add engine tests: form reading accepted, form value accepted, form without a reading
      matched on value only, reading-only field rejecting an empty answer, whitespace (ASCII and
      full-width) trimmed, `café`/`CAFÉ` folded, and katakana not accepted for a hiragana reading.
      `ja_reading_accepted_as_correct_answer` stays valid and needs no rewrite

## 4. Kotlin (Android) — database layer and migration

- [ ] 4.1 Add `reading: String?` to `WordForm` in `data/model/Models.kt`
- [ ] 4.2 In `DbTableSQLite.kt`: bump `CURRENT_VERSION` to 2 and add `reading` to the
      `word_forms` create statement
- [ ] 4.3 Drive the v1→v2 migration from `db_info.version` (for example an ensure-schema step run
      from `onOpen`) rather than relying on `onUpgrade`, because a desktop-produced file arrives
      with `PRAGMA user_version = 0` and would otherwise be routed to the no-op `onCreate`
- [ ] 4.4 Keep `onUpgrade` as a safety net and make the shared migration step idempotent by
      checking whether the `reading` column already exists, so the two paths cannot both issue
      the `ALTER TABLE` in one open
- [ ] 4.5 Write `db_info.version = CURRENT_VERSION` once the migration has been applied
- [ ] 4.6 Read and write the new column in `DbTableSQLite.kt` word form queries, trimming both
      value and reading and storing `NULL` for an empty reading
- [ ] 4.7 Carry the reading through `DbTableMemory.kt`
- [ ] 4.8 Add a migration test for a normal v1 file: version becomes 2, column exists, rows intact
- [ ] 4.9 Add a migration test for a file with `db_info.version = 1` but `user_version = 0`
      (as produced by the desktop app), asserting the column is added and the version written back
- [ ] 4.10 Add a test that re-opens a migrated database without error, and one that exercises both
      migration paths in a single open without a duplicate-column failure
- [ ] 4.11 Add form-reading round-trip tests to `DbTableSQLiteTest.kt` and `DbTableMemoryTest.kt`

## 5. Kotlin (Android) — labels, quiz engine, UI

- [ ] 5.1 In `ui/Labels.kt`: remove `phonetic` and `hiragana` from the canonical lists, keep
      `kanji` and `pitch_accent`, and keep `formLabelResId()` mappings and string resources
- [ ] 5.2 Give `TypingField` a reading and build the base field as
      `TypingField("word", word.word, word.reading)` in `quiz/QuizEngine.kt`
- [ ] 5.3 Delete the `field.label == "word"` special case in `gradeTyping` and grade every field
      with the shared value-or-reading predicate, including synonym form lookups
- [ ] 5.4 Apply the agreed comparison semantics — trim both sides, `equals(ignoreCase = true)` for
      Unicode case folding, no kana folding — and guard both sides so an empty expected string
      never matches; treat a field as unspecified only when value and reading are both empty
- [ ] 5.5 Expose the correct reading alongside `correctValue` in `TypingFieldResult`
- [ ] 5.6 Add a reading input per word form row in `ui/wordedit/WordEditSheet.kt` and carry it
      through `FormField` and the save mapping in `WordEditViewModel.kt`, trimming value and reading
- [ ] 5.7 Change the save filter so a form row is dropped only when its value and reading are
      both empty, replacing the current label-only filter
- [ ] 5.8 In `ui/quiz/QuizScreen.kt`: remove the `if (!fr.correct)` guard so every field shows its
      correct value with its reading, show a reading-only field's reading alone, and show the word
      with its reading in `McqResultView`
- [ ] 5.9 Add `QuizEngine` unit tests mirroring the Rust cases (form reading accepted, form value
      accepted, form without reading matched on value only, reading-only field rejecting an empty
      answer, whitespace trimmed, `café`/`CAFÉ` folded, kana not folded)

## 6. Homophone matching

- [ ] 6.1 Confirm the Kotlin homophone dialog matches the specified behavior — same `reading`, or
      same spelling when the word has no reading — that no word form label is consulted, and that
      both comparisons use the same trimming and case folding as quiz answer matching
- [ ] 6.2 Leave the Rust Homophones and More info menu items as the existing stubs they are today;
      record the missing implementations as an existing gap rather than fixing them here

## 7. Verification

- [ ] 7.1 Run the full Rust test suite and the Android unit tests
- [ ] 7.2 Manually verify on both platforms: add a JA verb with form readings, quiz it typing the
      reading for a non-base form, and give up to confirm readings appear on the answer side for
      correctly and incorrectly answered fields alike
- [ ] 7.3 Verify a pre-change (v1) database opens, migrates to v2, keeps its words and forms, and
      opens again cleanly
- [ ] 7.4 Verify the cross-platform path: copy a desktop-produced v1 file onto Android, open it,
      and confirm the migration runs and readings can then be saved and read back
