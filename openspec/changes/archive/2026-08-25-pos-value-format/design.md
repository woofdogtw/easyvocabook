## Context

See proposal.md — *Why*. The state that shapes the approach:

Three producers write `words.part_of_speech`. Rust's `EN_POS` / `JA_POS` and
`~/tmp/gen_vocab_sql.py` both emit keys; Android's `POS_JA` emits display strings while its
`POS_EN` emits keys. The value crosses platforms through Drive sync, so a word created on Android
carries `動詞` onto the desktop, where five equivalent comparisons miss it and
`suggested_labels("ja", "動詞")` returns nothing.

Two Kotlin consumers read the field differently. `WordFormLabels.forWord` matches both spellings
on purpose; the six comparisons `verb-transitivity` added match only the key. That asymmetry is
the mechanism of the bug — the tolerant reader kept the dropdown looking correct while the strict
readers silently dropped data.

## Goals / Non-Goals

**Goals:**

- One stored format for `part_of_speech` across both platforms and the seed.
- Recurrence blocked by a test, not by convention.

**Non-Goals:**

- Rust behavior changes. It already matches both specs; editing its logic would risk a
  conforming implementation for no gain. One test is added to `db/labels.rs` (see *Assert the
  list contents on both platforms*) — it asserts what the constants already hold and changes
  nothing at runtime.
- Backward compatibility with `動詞`-style values. The database is discarded (proposal.md — *What
  Changes*), so there is nothing to stay compatible with.
- The word-list comparison badge. It consumes this field and is deliberately a later change.

## Decisions

### Fix the producer, not the consumers

Six comparisons break on `動詞`, and making each accept both spellings would be a smaller diff
than changing the dropdown. It is rejected because those comparisons are correct as written —
they follow the spec's key form. The defect is one producer emitting a value the spec forbids.
Spreading tolerance across six call sites would leave the illegal value in the database, where
the next reader has to know about it too, and the desktop's five comparisons would still need the
same treatment.

**Alternative considered:** normalize at the data layer, mapping `動詞` → `verb` on write. This
keeps every reader simple but leaves the dropdown producing a value the schema spec forbids, and
the normalizer becomes permanent — it can never be removed without proving no old row survives.

### Remove the dual-format tolerance rather than extend it

`WordFormLabels.forWord` matching `"verb", "動詞"` looks like prudent defensive coding. With a
single stored format it defends nothing, and it actively hides defects: it is precisely why a
malformed value behaved correctly in one place while failing in six others, which is what let
this survive six weeks and a round of hand-testing.

Removing it makes a format error fail everywhere at once, which is the condition under which the
existing tests and the new consistency test can catch it.

### Assert the list contents on both platforms

The root cause is one list maintained in two languages with no link between the copies, and
**neither copy is guarded today**. Rust's `every_suggested_label_is_canonical` iterates `EN_POS`
and `JA_POS`, but it only asserts that the labels those parts of speech *suggest* are canonical:
put a display string in `JA_POS` and `suggested_labels` returns empty, the inner loop never runs,
and the test still passes. Rust did not catch this because it was written correctly, not because
anything checks it.

So the assertion goes in on both sides: each list contains exactly the keys the spec names, and
no entry contains a non-ASCII character. The ASCII check is what would have caught this specific
bug on the day it was written.

Adding a Rust test is compatible with leaving Rust's behavior alone — it asserts what the
constants already are, and would fail only if someone later changed them.

**Alternative considered:** generate the Kotlin lists from the Rust constants at build time. It
removes the duplication at its source, but couples the Android build to the Rust crate for one
nine-element list — disproportionate, and neither platform builds the other today.

### Move the Kotlin lists to `Labels.kt`

Both lists are top-level `private` in `WordEditSheet.kt`, so no test can reach them; the guard
above is unimplementable while they stay there. `ui/Labels.kt` already holds
`SUPPORTED_LANGUAGES`, `EN_FORM_LABELS` and `JA_FORM_LABELS`, and is the file that corresponds to
Rust's `db/labels.rs`. A canonical vocabulary list has no business living inside a Compose screen.

## Risks / Trade-offs

**A stale database copy syncs back and reintroduces `動詞`** → With tolerance removed, such a word
silently loses its verb attributes exactly as it does today. Mitigation: when the check finds any
copy dirty, all three go together — the desktop file, the phone's `filesDir/easyvocabook.db`, and
the Drive copy. Deleting two of the three is worse than deleting none, because sync restores the
survivor. When every copy is clean, nothing is deleted.

**The phone's copy cannot be inspected with SQL** → Its database lives in the app's private
`filesDir`, and the build used for hand-testing is a signed release, so `adb run-as` (debuggable
builds only) does not reach it. The check there is by provenance instead: an illegal value can
only come from creating a word on Android or re-picking a part of speech there, so a phone that
has done neither since 2026-07-13 is clean. Syncing the phone up first to inspect it through
Drive is not a workaround — latest-wins would overwrite a clean Drive copy with a dirty phone one.

**Removing `phrase` orphans any word already using it** → No word does; the seed classifies
multi-word entries such as 「風邪を引く」as verbs. Should one exist, the consequence is mild and
local: the value still reads back, `posDisplay` keeps its `phrase` entry, and only re-selecting it
in the dropdown becomes impossible. Editing that one word to a legal part of speech is the
proportionate fix — it is not grounds for discarding three databases.

**Five newly selectable English options and three Japanese ones change what the dropdown offers**
→ This is the specified behavior being restored, not new scope. No stored value changes meaning,
and no existing word can have used an option that was never offered.

## Migration Plan

No schema migration; the schema is untouched and `CURRENT_VERSION` does not move.

**Reset only if a database actually holds a stranded value.** The illegal format can only have
been produced by creating or re-picking a part of speech on Android, so a database may well be
clean. The current seed database holds four values — `noun` 773, `verb` 289, `i-adj` 65,
`na-adj` 35 — all keys, all ASCII, no `phrase`, and no English words at all, so the five added
English options strand nothing either.

The check is `SELECT DISTINCT part_of_speech FROM words;` on the desktop file and the Drive copy.
The phone's is judged by provenance instead, for the reason given under *Risks*. If every copy
comes back clean, install the fixed build and keep the data; practice statistics survive and the seed
needs no regeneration. The user reset everything for `verb-transitivity` two days ago, and a
second reset would discard those statistics again for no reason.

If any copy is dirty, regenerate the seed and delete the desktop file, the Android database and
the Drive copy before importing. All three must go together — sync restores whichever survives.

Rollback is reverting the commit. Nothing written after this change is unreadable by the previous
build: a word storing `verb` is what the desktop and the seed already produced.
