## Why

Japanese verbs come in transitive/intransitive pairs (起きる／起こす), and knowing which verb
a word pairs with — or that it has no partner — is part of learning it. The vocabulary source
already records this for all 284 verbs, but the seed importer folded it into the free-text
`words.note` field, where the quiz engine never looks. The quiz therefore cannot ask about it.

Making the quiz ask exposes a second problem. A field with nothing recorded is currently graded
as "accepts any input", so a verb with no partner would be satisfied by typing anything. That
rule was introduced for a different purpose (grading an answer given as a synonym) and it
already makes 873 of the user's 1157 words quiz with fields that are correct no matter what is
typed. Asking about verb pairs is only meaningful once an empty field means "leave this blank".

## What Changes

- **BREAKING** The schema is rewritten at version 1 rather than migrated: the v1→v2 migration
  added by `word-form-reading` is deleted, `word_forms.reading` folds into the v1 DDL, and the
  new verb columns are added there too. Existing databases are discarded, not upgraded. Version
  numbers are being kept in reserve until the app is actually released. (both)
- Verbs gain two structured attributes on the word itself: a **transitivity type**
  (自動詞 / 他動詞 / 自他両用) and a **verb group** (I / II / irregular). Both are recorded on
  the word rather than as a word form, because they describe the verb itself, not one of its
  inflections. (both)
- The paired verb is recorded as a `transitive_pair` word form. A verb with no partner has no
  such row; the answer side shows `-` for it. (both)
- Typing quiz for Japanese verbs gains two graded questions: the paired verb (text input) and
  the transitivity type (three-way choice). Both count toward the verdict. Neither appears for
  other languages. (both)
- **Grading changes for every field**: an empty expectation on the target word now requires an
  empty answer instead of accepting anything. Answering as a synonym that lacks a field stays
  lenient, because that leniency exists to avoid penalising a correct synonym. (both)
- Japanese nouns stop suggesting `counter` and `particle`, which only the desktop offered: a
  counter is not unique for most nouns and a particle depends on sentence role, not on the noun.
  They are quizzed on word and reading alone, which also aligns the two platforms. (both)
- The seed is regenerated from the Markdown sources: verb group and the adjective conjugations
  are derived mechanically, transitivity is classified with review, and a consistency report
  flags disagreeing pairs, repeated dictionary forms, and rows whose ない form contradicts the
  group derivation.

## Capabilities

### New Capabilities

None — this extends existing quiz, edit, and schema behavior.

### Modified Capabilities

- `db-schema`: schema is defined at version 1 with `word_forms.reading` and the new verb
  columns; the v1→v2 migration is removed.
- `db-layer`: word entries carry transitivity type and verb group; `transitive_pair` is part of
  the canonical Japanese label set for verbs.
- `word-edit-ui`: Japanese verbs offer a transitivity type choice and a paired-verb field.
- `quiz-engine`: Japanese verb typing questions include the pair and the type; an empty
  expectation on the target word requires an empty answer.
- `quiz-ui`: the type question is rendered as a three-way choice, and a field with no expected
  answer reveals `-`.

## Non-goals

- No migration path from an existing database. Discarding data is a deliberate decision taken
  while the app has a single user; a released app would have to migrate instead.
- No general-purpose Markdown-to-SQL tooling. The seed is regenerated once here; turning that
  into a reusable skill that classifies transitivity and writes example sentences is separate
  work, recorded in the explore notes.
- No import-SQL feature in the app, which is what would later let vocabulary be added without
  discarding practice statistics.
- No attempt to distinguish "not yet classified" from "no partner" for word forms. A blank
  `transitive_pair` means the verb has no partner, because the field is always shown for
  Japanese verbs and is therefore always answered deliberately.
- No changes to multiple-choice grading, which is meaning-based.
- No verb-group question in the quiz. The group is recorded and revealed with the answer, not graded.

## Impact

- **Schema**: `words` gains two columns; `word_forms.reading` moves into the v1 DDL; the v1→v2
  migration step, its version write-back, and its tests are removed on both platforms.
  `doc/schema.md` is rewritten to describe a single version.
- **Rust (PC)**: `db/schema.rs`, `db/types.rs`, `db/sqlite.rs`, `db/memory.rs`, `db/labels.rs`,
  `quiz/engine.rs` (field construction, grading, reveal), `ui/mod.rs` (edit state and messages),
  `ui/word_edit.rs`, `ui/quiz.rs`, `locale/mod.rs`.
- **Kotlin (Android)**: `data/model/Models.kt`, `DbTableSQLite` (DDL, removed migration),
  `DbTableMemory`, `ui/Labels.kt`, `quiz/QuizEngine.kt`, `ui/wordedit/*`, `ui/quiz/QuizScreen.kt`,
  `res/values*/strings.xml`.
- **Data**: the seed SQL is regenerated from `~/tmp/*.md`. Roughly 104 of the 261 distinct verb
  forms need transitivity classified by judgement, 4 pairs disagree, 23 rows repeat a form
  already present, 7 cells hold two forms, and at least two ない forms are wrong.
- **Tests**: the v1→v2 migration tests are deleted, including the one guarding the
  `PRAGMA user_version` defect; grading tests are updated for the stricter empty-field rule and
  extended for the two new questions.
