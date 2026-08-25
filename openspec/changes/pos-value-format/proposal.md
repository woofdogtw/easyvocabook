## Why

`words.part_of_speech` is specified to hold a language-neutral key, but Android's Japanese
part-of-speech dropdown stores the localized display string instead — `動詞` where every other
producer writes `verb`. The divergence was harmless for six weeks because the only consumer
deliberately accepted both spellings. `verb-transitivity` then added six comparisons against
`"verb"` that do not, so a Japanese verb created on Android now gets no transitivity controls and
its `transitivity` / `verb_group` are written as NULL — the feature shipped two days ago does not
work for any word the user creates.

Nothing here is a design question. `db-schema` already requires the key form and its scenario
names the exact wrong value; `word-edit-ui` already fixes both canonical option lists. This change
brings the implementation back to the specs it was always supposed to follow.

## What Changes

- Android's `EN_POS` and `JA_POS` (renamed from `POS_EN`/`POS_JA` to match `EN_FORM_LABELS` and Rust's `db/labels.rs`) store **language-neutral keys**, replacing the Japanese list's
  display strings. This is the root fix; the six `== "verb"` comparisons are already correct and
  are left alone. (kotlin)
- Both lists move from `WordEditSheet.kt` to `ui/Labels.kt`, where the other canonical constants
  live. They are file-private today, which is why nothing can test them. (kotlin)
- Both Android lists are completed to the nine options each that `word-edit-ui` specifies. English
  was missing `pronoun`, `preposition`, `conjunction`, `interjection` and `other`; Japanese was
  missing `aux-verb`, `conjunction` and `other` — none of them were selectable on Android. (kotlin)
- `phrase` / `句` is **removed** from both lists. It appears in no spec, in neither Rust list, and
  on no word. (kotlin)
- The dual-format tolerance in `WordFormLabels.forWord` (`"verb", "動詞" ->`) is **removed**. With
  one stored format it protects nothing, and it is what let the divergence stay invisible: a bad
  value passed here while failing in six other places. The English `"adj"` alias goes with it —
  same species, equally unproduced. (kotlin)
- A consistency test asserts each list holds exactly the specified keys and nothing non-ASCII —
  **added on both platforms**. Rust's existing test iterates its POS constants but only checks the
  labels they suggest, so a display string there would slip through as well; neither side is
  guarded today. (both)
- Japanese parts of speech start rendering in Chinese locales as 「動詞 (verb)」 instead of a bare
  「動詞」. `posDisplay` is not edited — it maps keys and passed the display string straight
  through, so storing the key is what reaches the mapping. `word-edit-ui` requires the bilingual
  form and the desktop always produced it, making the bare label a spec violation this fix ends
  rather than a new decision. It is also the fastest way to tell the two builds apart. (kotlin)
- **No migration and no read-time tolerance.** A database is reset only if it actually holds a
  stranded value; the current seed data is entirely keys, so the check may well come back clean
  and the practice statistics can stay. (both)

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Both governing requirements already describe the corrected behavior:

- `openspec/specs/db-schema/spec.md` — *part_of_speech stored as language-neutral key*, whose
  scenario states that a word typed 「い形容詞」stores `i-adj`, "not 「い形容詞」".
- `openspec/specs/word-edit-ui/spec.md` — *Part-of-speech dropdown options per language*, which
  lists all nine English and nine Japanese options and repeats that the stored value is the key.

This change sets `skip_specs: true`. Writing a delta would mean restating requirements that are
already correct, and no behavior is being specified that the specs do not already demand.

## Impact

Android only for behavior; the Rust desktop already conforms and gains just a test.

- `ui/wordedit/WordEditSheet.kt` — both POS lists move out. The dropdown's display callback is
  deliberately untouched, though what it renders changes because its input does
- `ui/Labels.kt` — receives both POS lists as canonical constants
- `quiz/QuizEngine.kt` — `WordFormLabels.forWord` drops its display-string branches and the
  `"adj"` alias
- Android unit tests — list consistency test, and a save-path test for the verb attributes
- `rust/src/db/labels.rs` — one added test; no behavior change

Data: no schema change. Each database copy is checked with
`SELECT DISTINCT part_of_speech FROM words;` and reset only if it holds a non-key value — the
seed data is all keys today. If a reset is needed, every copy must go together (desktop file,
Android data, Drive remote), since sync restores whichever survives.

Downstream: the word-list comparison badge planned next reads `part_of_speech` and is blocked on
this fix, since the badge cannot be computed from two formats.
