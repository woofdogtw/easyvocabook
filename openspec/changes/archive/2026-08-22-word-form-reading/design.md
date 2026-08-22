## Context

See `proposal.md` — Why. The relevant current state:

- `word_forms(id, word_id, label, value)` has no reading; `words` has both `word` and `reading`.
- **Version handling differs by platform, and the two disagree.** `doc/schema.md` and
  `openspec/config.yaml` both name `db_info.version` as the authority, and Rust
  (`db/mod.rs`) reads exactly that. Android, however, extends `SQLiteOpenHelper` with
  `CURRENT_VERSION`, so its `onCreate`/`onUpgrade` are driven by SQLite's `PRAGMA user_version`
  — which nothing in the codebase ever writes. A desktop-produced file therefore arrives on
  Android with `user_version = 0`.
- `schema::migrate()` is called on **every** open for any version ≤ current, and no code path
  writes `db_info.version` back after migrating.
- The two quiz engines reached the same behavior by different shapes, so this change lands
  differently on each platform:
  - **Kotlin** (`quiz/QuizEngine.kt`) folds the base word into the field list as a pseudo-field
    `TypingField("word", word.word)`, then grades everything in one loop with a
    `field.label == "word"` branch that is the only place a reading is accepted.
  - **Rust** (`quiz/engine.rs`) grades the base word separately (`grade_typing` → `matched`,
    which already accepts `word` or `reading`) and conjugation fields against
    `ConjugationField.expected`, which holds a single string.
- Comparison and trimming rules are inconsistent today and there is no single "existing"
  behavior to extend: Rust compares the base word with `eq_ignore_ascii_case` but the reading
  with exact `==`, and does not trim quiz input; Kotlin trims input and compares
  case-insensitively. On save, Rust trims form values while Kotlin does not.
- The "accept anything" escape hatch also differs: Kotlin keys it on a blank expected string,
  while Rust keys it on the matched synonym having no form row for that label.

## Goals / Non-Goals

**Goals:**

- One matching rule — "input matches value or reading" — applied uniformly to every field,
  with no base-form special case left in either engine, and with the comparison semantics
  written down rather than inherited by accident.
- A migration that is purely additive, runs exactly once, and is driven by the same version
  source on both platforms.
- Reveal formatting that reuses the `單字（讀音）` convention already used on question sides.

**Non-Goals:**

- Unifying the two engines' internal shapes. They stay as they are; only the matching rule is
  made common within each.
- A second input box per field in the quiz. Readings are an alternative answer, not extra input.
- Any change to multiple-choice grading, which is meaning-based.
- Kana normalization (folding hiragana with katakana) in answer comparison.

## Decisions

### Store the reading as a nullable column on `word_forms`

`ALTER TABLE word_forms ADD COLUMN reading TEXT`, bumping the schema to v2.

*Alternatives considered:* (a) a separate `word_form_readings` table — needless join and a
1:1 relationship modelled as 1:N; (b) keep using labels such as `hiragana` to carry readings —
that is the workaround being retired, and it cannot express "this specific form's reading";
(c) encode `value（reading）` in the existing column — unparseable for words containing
parentheses and breaks exact-match grading.

### Drive the Android migration from `db_info.version`, not `PRAGMA user_version`

This is the decision that keeps the change from bricking Android. Because desktop files carry
`user_version = 0`, `SQLiteOpenHelper` would route them to `onCreate` — whose statements are all
`CREATE TABLE IF NOT EXISTS` and therefore no-ops — never adding the `reading` column, while the
helper silently stamps `user_version` to the new value. Every later `SELECT ... reading` would
then fail. Android therefore performs the version check and migration against `db_info.version`
after the database is open (for example in `onOpen`, or in an explicit ensure-schema step),
rather than relying on `onUpgrade` alone. `onUpgrade` is kept as a safety net for files that do
happen to carry a correct `user_version`, so two code paths can attempt the same migration.

**Both paths therefore share one idempotent migration step**: before issuing
`ALTER TABLE word_forms ADD COLUMN reading`, the step checks whether the column already exists
and does nothing if it does. Without that guard the second path would fail with
`duplicate column name` and reopen the very defect the version write-back closes. The write-back
remains the mechanism that stops the migration re-running across opens; the existence check is
what makes the two paths safe within a single open.

*Alternative considered:* start writing `PRAGMA user_version` on both platforms and keep using
`onUpgrade`. Rejected: it adds a second source of truth that must be kept in sync with
`db_info.version` forever, and every file already in the wild would still have to be repaired
through the `db_info` path anyway.

### The migration writes `db_info.version` back, and steps are guarded by version

Without a write-back, `migrate()` — which runs on every open — would re-issue
`ALTER TABLE ... ADD COLUMN reading` on the second open and fail with `duplicate column name`,
leaving the user unable to open their own database. Writing the new version back also keeps the
file honest for the other platform: a file still advertising v1 would be opened and written by a
not-yet-updated desktop app, whose inserts omit `reading`, silently discarding readings the user
had entered.

### Retire only the labels that carried a pronunciation, and keep their locale strings

`phonetic` and `hiragana` leave the canonical vocabulary; `kanji` (a written form) and
`pitch_accent` (an accent pattern) are not readings and stay. `form_locale_key()` /
`Labels.formLabelResId()` keep mapping the retired labels, so a database still holding such rows
shows a translated name. `suggested_labels()` never proposed either of them, so the suggestion
table in `word-edit-ui` is unaffected.

The one consumer of `phonetic` is the homophone feature in `word-list-ui`, which specifies it as
an IPA fallback for English words with no reading. Neither platform implements that fallback:
Kotlin falls back to same-spelling matching, and the Rust menu item is still a stub that only
closes the menu. The delta therefore does the minimum honest thing — it deletes the `phonetic`
fallback from the specification and writes down the same-spelling behavior that Kotlin actually
has. It deliberately does **not** introduce a new form-reading fallback, which would once again
put unimplemented behavior into a spec. The missing Rust implementation is an existing gap,
recorded here and left to a separate change.

### Fix the canonical-list drift, but leave the suggestion-table drift alone

Rust's `JA_FORM_LABELS` lacks `negative` and `past`, which the Japanese i-adjective suggestions
already propose — so those rows appear pre-labelled but their label cannot be re-selected from
the Rust dropdown. Adding the two labels is behavior-neutral (it only widens a dropdown) and is
done here, since this change rewrites both canonical lists anyway and archiving the delta would
otherwise re-assert a list Rust does not implement.

The **suggestion table** has a similar divergence — Rust offers `counter, particle` for ja/noun
where Kotlin offers nothing, and Kotlin offers `particle` for a word whose part of speech is
itself `particle` — but it is deliberately **left for a separate change**. The suggestion table
is not only an edit-dialog concern: both quiz engines derive their typing fields from the same
function, and Kotlin additionally falls back to "test every form the word actually has" when the
suggestion list is empty. Aligning the table would therefore silently change which fields the
Android quiz asks for — Japanese nouns would gain two usually-empty `counter`/`particle` fields
that, under the unspecified-field rule, accept any input, while particle-POS words would switch
from one field to all of their forms. Doing it properly requires rewriting the **Fields shown**
list in `quiz-engine` as well, which has nothing to do with per-form readings and would put two
of this change's own deltas in conflict.

### Model the expectation as a (value, reading) pair, matched by one helper

Both engines gain a single matching predicate, guarded symmetrically on both sides —
`matches(input, value, reading) =
(value non-empty && eq(input, value)) || (reading non-empty && eq(input, reading))`.

The guard on the **value** side matters as much as the one on the reading side: because a row
may now carry a reading without a value, an unguarded `eq(input, value)` would let an empty
answer satisfy an empty expected value and mark a reading-only field correct. Fields with
nothing at all to match are handled by the unspecified rule below, not by the predicate.

- **Kotlin**: `TypingField` carries a reading; the base word becomes
  `TypingField("word", word.word, word.reading)` and the `label == "word"` branch is deleted.
  Synonym form lookups compare against the synonym form's value and reading too.
- **Rust**: `ConjugationField` gains `expected_reading`; `grade_typing` uses the same predicate
  for conjugation fields, while the base-word `matched` lookup already implements it apart from
  the comparison inconsistency noted below.

*Alternative considered:* keeping the special case and adding a second one for forms — rejected,
since the special case is exactly the defect being removed.

### Define `eq` explicitly: trim both sides, fold case with Unicode, nothing else

There is no single existing comparison to inherit, so the rule is specified rather than assumed:
trim leading and trailing whitespace from both sides — both platforms' `trim()` already cover
full-width spaces such as U+3000 — and compare ignoring letter case with Unicode case folding.

Unicode folding is chosen over ASCII-only folding because it is what each platform's natural
idiom already does on one side (`String.equals(ignoreCase = true)` in Kotlin), so specifying
ASCII-only would require Kotlin to deliberately avoid its own idiom and would reintroduce the
cross-platform divergence this change exists to remove. It also behaves better for the English
vocabulary this app supports, where `café` and `CAFÉ` should match. Rust therefore compares
lowercased strings rather than using `eq_ignore_ascii_case`.

Kana folding is deliberately excluded — a learner who types katakana for a hiragana reading has
typed a different string, and treating them as equal would hide a real mistake, particularly for
loanwords that are meant to be written in katakana.

Applying this rule means Rust must start trimming quiz input and stop using exact comparison for
readings, and Kotlin must start trimming form values on save.

### A field is "unspecified" only when both value and reading are empty

The escape hatch that lets synonyms with missing forms be graded is re-expressed over the pair.
A form with a value but no reading is still a real expectation, matched on its value alone; only
a form with neither is a free pass. Note this is a **behavior change for Rust**, not merely a
widening: Rust currently keys the escape hatch on the synonym having no form row at all, and
would compare a present-but-empty value literally. Both engines converge on the pair-based rule.

### Keep a word form row that has only a reading

Because a row may now carry a reading without a value, the save filter changes from "drop rows
with an empty value" (Rust today) and "drop rows with a blank label" (Kotlin today) to a single
rule: drop a row only when its value and reading are both empty. Without this, a reading-only
row would be silently discarded on PC and stored as a mandatory empty-valued field on Android.

### Reveal shows `value（reading）` for every field, answered correctly or not

Applied to the base word field and every form field, on both the typing result view and the
multiple-choice answer view, and shown whether or not the field was answered correctly — a
learner who wrote the kanji correctly may still not know how to read it. Kotlin currently gates
the correct value behind `if (!fr.correct)`, and Rust's typing-mode `word_display` is
`target.word` with no reading (only the multiple-choice branch uses `format_word_display`);
both are brought in line.

## Risks / Trade-offs

- **A v2 file is refused by a v1 peer** → both platforms are updated together before the next
  sync (proposal decision ①). Because sync is whole-file and latest-wins, a stale peer fails
  closed with the existing "please update the app" error rather than corrupting data.
- **The reverse direction is the quieter risk**: an updated app that downloads a v1 file from a
  not-yet-updated peer must migrate it on open, which is exactly the path that the
  `user_version` defect would break. It is covered by an explicit scenario and a migration test.
- **`"word"` is a magic label in the Kotlin engine** → a user-defined form literally labelled
  `word` would collide with the base-word pseudo-field. This predates the change and is not
  made worse by it, since the branch keyed on that label is being removed; noted so it is not
  mistaken for a new defect.
- **Extra input per form row raises edit-dialog density**, most visibly on phones → layout is
  tuned during implementation; the specs constrain behavior, not arrangement.
- **The Android helper still routes on `PRAGMA user_version`**: `SQLiteOpenHelper` keeps
  receiving `CURRENT_VERSION`, so a file whose stamped `user_version` is *higher* than the app's
  (an Android-created file after a future schema bump, opened by a downgraded app) triggers the
  helper's downgrade error before the `db_info.version` guard can produce the friendlier
  "please update the app" message. The outcome is still fail-closed rather than data loss, and
  the ensure-schema path keeps the low-`user_version` direction — the one that actually occurs
  with desktop-produced files — working. Overriding `onDowngrade` to defer to the `db_info`
  guard is left to whichever change first needs it.
- **Two independent migration implementations can drift** → `doc/schema.md` stays the single
  source of truth and both sides get a migration test that opens a v1 file, asserts v2 structure
  with rows intact, and re-opens to prove the migration is not repeated.
- **Tightening the comparison rules touches passing behavior**: Rust gains trimming and
  case-insensitive reading matches. Existing tests such as
  `ja_reading_accepted_as_correct_answer` assert the base form accepts its reading and remain
  valid under the new rule, so this change adds cases rather than rewriting them.

## Migration Plan

1. Update `doc/schema.md`: add the column, raise the stated current version, and add a
   Migrations section listing the v1→v2 statement.
2. Raise the version constant to 2 in both implementations; add the guarded migration step, the
   `db_info.version` write-back, and the fresh-create DDL. On Android, route the check through
   `db_info.version` rather than `user_version`.
3. Thread the reading through models, DB read/write, edit UI, engines, and reveal views.
4. Ship desktop and Android together; sync only after both are updated.

*Rollback:* the column is nullable and additive, so a v2 file is structurally readable by v1
code once the version guard is satisfied. Reverting the app while keeping a v2 file requires
setting `db_info.version` back to 1; on Android the stamped `PRAGMA user_version` must be reset
to 1 as well, or `SQLiteOpenHelper` raises a downgrade error before any of this is reached.
Readings already entered would then be ignored, not lost.

## Open Questions

- Edit-dialog arrangement for the added reading input (side-by-side versus stacked per row),
  to be settled visually during implementation. It affects neither the specs nor the tasks.
