## Why

Browsing the word list teaches nothing today. It shows word, reading, meaning and a correct-rate
percentage, so seeing that 上がる pairs with 上げる — or that a verb has no partner at all — means
opening each word one at a time. The pairing is exactly the kind of thing that is learned by
comparison, and the list is where comparison would be cheapest.

The data has been there since `verb-transitivity`: `words.transitivity` says which side of a pair a
verb sits on, and a `transitive_pair` word form names its partner. Both are already loaded by the
list query on both platforms. Nothing surfaces them.

## What Changes

- The word list gains a **classification badge** and a **comparison column**. The badge shows the
  word's own classification — 自 / 他 / 自他 for Japanese verbs, otherwise the part of speech — and
  the comparison column shows the companion form that classification implies: the opposite verb for
  a Japanese verb, the negative form for an adjective, the past tense or plural for English.
  (both)
- **The badge sits with the word, never beside the companion.** `自 → 上げる` reads as though 自
  described 上げる; separating them by the intervening columns makes ownership unambiguous, and the
  badge still says what the word is when there is no companion to show. (both)
- Desktop goes from four columns to six, **keeping Correct Rate**: Word, Reading, Meaning, Badge,
Comparison, Correct Rate. All six columns sort; Class sorts on the classification key and
  Comparison on the
  companion word. (rust)
- Android drops the correct-rate **number** and lays each row out as a 2×2 grid — word (with
  reading) and badge on the first line, meaning and companion on the second, each cell wrapping
  independently. Its sort cycle drops Correct Rate for Class: with no percentage on the row,
  sorting by one produces an order the user cannot account for, while the Class badge is on screen.
  The desktop, which still shows the number, still sorts by it. (kotlin)
- Abbreviated label strings are added for badge use, and must be **unique across the union of the
  transitivity and part-of-speech keys** — the badge draws from both namespaces without saying
  which, so a repeated character would mean two things on two rows (`其他` therefore abbreviates to
  其, since 他 is already 他動詞). **Which script a badge uses follows the language of the word, not
  the interface locale**: a Japanese word reads 名 / 動 / 自 / 他 / い / な in every locale, an
  English word reads N / V / Adj in every locale. The existing full names are unchanged. (both)
- A word with no companion shows `—`, the convention `verb-transitivity` established. This applies
  uniformly, including to ambitransitive verbs — 3 of the 4 in the user's data do record a partner,
  so suppressing it for them would hide real data. (both)

Antonyms are deliberately **not** part of this. They have no two-way symmetry, so a column of them
cannot be read as a comparison; and a `word_forms` row is a graded quiz field, so an `antonym`
label would silently become a required answer with no way to say which of several is correct.

Folded in because it sits in the way: `db/labels.rs` gains the classification rule this change
needs, and that file already holds a dead `pos_display` whose table disagrees with the live one —
it misled the design of this very change twice before being caught in review. It is deleted here,
along with the file-wide `allow(dead_code)` that hid it, and the remaining allow sites gain stated
reasons. No behaviour changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `word-list-ui`: the desktop table gains two columns and two more sort fields, taking it from
four to six; the Android row gains
  a badge and companion and loses the correct-rate number. Two requirements change —
  *Word list table view* and *Android word list — LazyColumn*.

## Impact

- `rust/src/ui/word_list.rs` — two columns, their headers, and a three-way column width
- `rust/src/db/labels.rs` — the change's core: `class_of`, `comparison_label`, `comparison_value`
  and the two badge-key lookups all land here, where both `db` and `ui` can reach them. Also where
  the dead `pos_display` and the file-wide `allow(dead_code)` are removed
- `rust/src/db/sqlite.rs`, `rust/src/network/sync.rs`, `rust/src/db/types.rs` — stated reasons on
  the remaining `allow(dead_code)` sites
- `rust/src/db/types.rs` — `SortField` gains class and comparison variants
- `rust/src/db/memory.rs` — where `SortField` comparison actually happens; both new sorts land
  here, which is why the classification rule must live somewhere `db` can reach
- `rust/src/locale/mod.rs` — abbreviated strings in en, zh-TW, zh-CN
- `kotlin/.../ui/wordlist/WordListScreen.kt` — 2×2 row layout
- `kotlin/.../ui/Labels.kt` — badge resolution shared with the abbreviations
- `kotlin/app/src/main/res/values*/strings.xml` — abbreviated strings

No schema change and no migration: every field this reads already exists and is already loaded.

Depends on `pos-value-format` (archived 2026-08-25), which made `part_of_speech` a single format
across platforms. The badge reads that field and could not be computed reliably before it.
