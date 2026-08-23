## 1. Schema documentation

- [x] 1.1 Rewrite `doc/schema.md` for a single version: add `words.transitivity` and
      `words.verb_group`, keep `word_forms.reading` in the base DDL, and remove the Migrations
      section describing v1→v2
- [x] 1.2 Keep the version-policy section, including the rule that `db_info.version` is the only
      authority and `PRAGMA user_version` must never be branched on, and note that no migration
      steps exist yet

## 2. Rust (PC) — schema and database layer

- [x] 2.1 In `rust/src/db/schema.rs`: set `CURRENT_VERSION` back to 1, add `transitivity` and
      `verb_group` to the `words` DDL, and keep `reading` in the `word_forms` DDL
- [x] 2.2 Remove only the v1→v2 step from `migrate()`. **Keep** the version guard, the
      `db_info.version` write-back that records a completed migration, and the `has_column`
      helper, so the first post-release migration has a working path; mark `has_column`
      `#[allow(dead_code)]` while nothing calls it
- [x] 2.3 Delete the v1→v2 migration tests (`v1_db_migrates_to_v2_with_rows_intact`,
      `migrated_db_reopens_without_duplicate_column_error`) and their `write_v1_db` fixture
- [x] 2.4 Add `transitivity: Option<String>` and `verb_group: Option<String>` to `WordEntry`,
      `NewWord`, and `UpdateWord` in `rust/src/db/types.rs`
- [x] 2.5 Read and write both columns in `rust/src/db/sqlite.rs`, storing `NULL` when absent
- [x] 2.6 Carry both through `rust/src/db/memory.rs`
- [x] 2.7 Add round-trip tests: a verb with both attributes, and a noun with neither

## 3. Rust (PC) — labels, locale, edit UI

- [x] 3.1 In `rust/src/db/labels.rs`: add `transitive_pair` to `suggested_labels("ja","verb")`
      and remove the `("ja","noun")` branch entirely, so Japanese nouns suggest nothing and both
      platforms implement the same table
- [x] 3.2 Add canonical key lists and locale-key mappings for the transitivity values
      (`intransitive` / `transitive` / `ambitransitive`) and the verb groups
      (`godan` / `ichidan` / `irregular`)
- [x] 3.3 Add the display strings for both sets to all three locales in `rust/src/locale/mod.rs`
- [x] 3.4 Extend the word-edit state in `rust/src/ui/mod.rs` with both attributes, add their
      messages, and clear them when the word is not a Japanese verb
- [x] 3.5 Render the transitivity radio group and the verb group control in
      `rust/src/ui/word_edit.rs`, shown only for Japanese verbs
- [x] 3.6 Add a test that the locale tables cover every transitivity and verb-group key

## 4. Rust (PC) — quiz engine

- [x] 4.1 Change the empty-expectation rule in `rust/src/quiz/engine.rs`: a field of the
      originally selected word with neither value nor reading is correct only when the input is
      empty; keep accepting anything when grading against a synonym that has no such form
- [x] 4.2 Add the `transitive_pair` field to Japanese verb questions via the suggestion list
- [x] 4.3 Add the transitivity question as a distinct choice-typed field carrying the expected
      key, graded by exact match and contributing to the overall verdict
- [x] 4.4 Expose the expected type on reveal so the UI can mark it, and make a field with no
      expectation reveal as `-`
- [x] 4.5 Update existing tests that relied on an empty field accepting anything
- [x] 4.6 Add engine tests: empty expectation requires an empty answer, blank is correct for a
      partnerless verb, the rule applies to any empty field (use a verb whose `nai_form` has no
      recorded value — a noun's `counter` is no longer shown), a wrong type fails,
      `ambitransitive` is distinct, and neither question appears for English

## 5. Rust (PC) — quiz UI

- [x] 5.1 Render the transitivity question as a radio group in `rust/src/ui/quiz.rs` with no
      option preselected, and carry the choice into grading
- [x] 5.2 Show `-` on reveal for a field whose expected answer is empty
- [x] 5.3 Mark the type question with ✓ / ✗ alongside the text fields

## 6. Kotlin (Android) — schema and database layer

- [x] 6.1 In `DbTableSQLite.kt`: set `CURRENT_VERSION` back to 1, add both columns to the `words`
      DDL, and keep `reading` in the `word_forms` DDL
- [x] 6.2 Remove only the v1→v2 step from `migrateFromDbInfo()`. **Keep** the function, its
      version-authority check, the `db_info.version` write-back, the `hasColumn` guard, and the
      `onOpen` / `onUpgrade` call sites
- [x] 6.3 Delete the v1→v2 migration tests, including
      `v1DatabaseFromDesktop_withZeroUserVersion_stillMigrates` and the `writeV1Database`
      fixture. Keep `versionTooNew_throwsOnUpgrade`, which still holds once `CURRENT_VERSION`
      is 1 and covers the guard that remains
- [x] 6.4 Add `transitivity: String?` and `verbGroup: String?` to `WordEntry` in `Models.kt`
- [x] 6.5 Read and write both columns in `DbTableSQLite.kt`, storing `NULL` when absent
- [x] 6.6 Carry both through `DbTableMemory.kt`
- [x] 6.7 Add round-trip tests mirroring the Rust ones

## 7. Kotlin (Android) — labels, strings, edit UI

- [x] 7.1 Add `transitive_pair` to the Japanese verb suggestions in `WordFormLabels.forWord()`,
      and confirm Japanese nouns still return an empty list
- [x] 7.2 Remove the fallback in `QuizEngine.buildTypingCard` that quizzes every form a word has
      whenever the suggestion list is empty. An unlisted combination must produce no form fields,
      as it already does on Rust — otherwise the two platforms only agree on paper, and any
      custom form a user adds to a Japanese noun reintroduces the divergence
- [x] 7.3 Add key lists and `stringResource` mappings for the transitivity and verb-group values
      in `ui/Labels.kt`
- [x] 7.4 Add the display strings to `values`, `values-zh-rTW`, and `values-zh-rCN`
- [x] 7.5 Extend `WordEditViewModel` with both attributes and clear them when the word is not a
      Japanese verb
- [x] 7.6 Render the transitivity radio group and the verb group control in `WordEditSheet.kt`,
      shown only for Japanese verbs

## 8. Kotlin (Android) — quiz engine and UI

- [x] 8.1 Restructure `gradeTyping` in `quiz/QuizEngine.kt` to first decide **which word** is
      being graded against — the selected word, or the synonym the user typed in the base field —
      and only then apply that word's expectations. The current OR-chain across the target and
      every synonym would let any string that happens to match some synonym's field satisfy an
      empty expectation. Then apply the empty-expectation rule, keeping the synonym leniency
- [x] 8.2 Add the `transitive_pair` field and the transitivity question to Japanese verb cards,
      both counting toward `allCorrect`
- [x] 8.3 Expose the expected type in `TypingFieldResult` for the reveal
- [x] 8.4 Render the type question as radio buttons in `QuizScreen.kt` with none preselected,
      and show `-` for a field with no expected answer
- [x] 8.5 Update every existing test invalidated by either the new grading rule or the changed
      field composition, including `buildTypingCard_jaVerb_hasSixFields`, whose assertion counts
      fields rather than exercising the empty-field rule and so breaks once `transitive_pair`
      and the type question join a Japanese verb card
- [x] 8.6 Add `QuizEngine` tests mirroring the Rust cases

## 9. Seed regeneration

- [x] 9.1 Rewrite the generator to read the Markdown sources and emit SQL for the new schema,
      including `word_forms.transitive_pair` rows and the two new columns. Clean the source
      artefacts on the way in: split a parenthesised reading into the `reading` column rather
      than leaving `来る(くる)` in a value, move a usage note (`かからない (改用「無料です」…)`)
      into `note`, normalise the full-width space inside compound verbs, and fall back to the
      kana column when the Japanese column is empty
- [x] 9.2 Derive `verb_group` from the dictionary and ない forms rather than the source's
      `動詞類` markings. Assert every verb is classified, treat compounds ending in 来る as
      irregular (`持って　来る` does not start with it), and report rather than guess when a ない
      form contradicts the rule — `冷やしない` and `変えす` are known bad rows
- [x] 9.3 Classify transitivity for every verb: carry over the 98 explicit markings, infer from
      partners and morphology where possible, and judge the remaining ~104
- [x] 9.4 Emit a consistency report covering: pairs whose two sides disagree on type, partners
      that name each other inconsistently, verbs left unclassified, the 23 rows that repeat a
      dictionary form already present, the 7 cells holding two forms (`作る、造る`), any ない
      form that fails the group derivation, and any adjective whose `な` column is blank yet does
      not end in `い` (`心配` is the one real violation)
- [x] 9.5 Review the report and the classifications: correct the 4 pairs whose sides disagree,
      decide for each repeated dictionary form whether it is a merge or two genuine senses
      (`できる` is two senses; `決める` twice is one), and split or pick a primary form for the
      seven two-form cells. Two rows sharing a `word` are acceptable when the senses are
      genuinely different: `words.word` carries no uniqueness constraint, and non-overlapping
      meanings mean the two never count as synonyms of each other
- [x] 9.6 Derive the adjective forms mechanically and emit them, so i-adjectives and
      na-adjectives stop quizzing with empty fields: い → くて / くない / かった,
      な → で / じゃない / だった, with `いい` irregular (よくて / よくない / よかった)
- [x] 9.7 Stop writing `動詞類` and `自他` into `words.note` for verbs, keeping `note` for the
      noun 補充 column

## 10. Verification

- [x] 10.1 Run the full Rust test suite and the Android unit tests
- [x] 10.2 Delete every existing database **before installing the new build on Android**:
      the desktop file, the Android app's data, and the cloud remote. Ordering matters — the
      helper's version argument drops from 2 back to 1 while existing Android files still have
      `PRAGMA user_version = 2`, so opening the new build over old data hits the default
      `onDowngrade` and crashes instead of showing the friendly version message
- [x] 10.3 Apply the regenerated seed with the desktop app closed
      (`sqlite3 easyvocabook.db < seed.sql` against the platform data path), then open the app,
      confirm the verbs carry a type and a group, and sync up. The app has no import feature —
      adding one is separate work
- [x] 10.4 Manually verify on both platforms: a verb with a partner accepts the partner and
      rejects a wrong one; a verb without a partner requires a blank answer and rejects any text;
      the type question grades correctly and reveals `-` where there is no partner
- [x] 10.5 Confirm neither Japanese-verb question appears for English words or Japanese nouns
- [ ] 10.6 After archiving, rename the scenario `Japanese word with particle data adds particle
      field` in `openspec/specs/quiz-engine/spec.md` to match what it now says — for example
      `Recording a particle does not create a question`. A scenario cannot be renamed from inside
      a change, so the main spec is edited directly afterwards, the same way the stale `(future)`
      qualifier was cleaned up after `word-form-reading`
