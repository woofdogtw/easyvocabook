## 1. Android — part-of-speech option lists

- [x] 1.1 Move `EN_POS` and `JA_POS` (renamed from `POS_EN`/`POS_JA` to match `EN_FORM_LABELS` and Rust's `db/labels.rs`) out of `ui/wordedit/WordEditSheet.kt` into `ui/Labels.kt`,
      alongside `SUPPORTED_LANGUAGES`, `EN_FORM_LABELS` and `JA_FORM_LABELS`. They are top-level
      `private` today, so no test can see them; `Labels.kt` is also where the canonical constants
      belong, mirroring Rust's `db/labels.rs`
- [x] 1.2 Replace the Japanese list's display strings with the nine language-neutral keys
      `word-edit-ui` specifies: `noun`, `verb`, `i-adj`, `na-adj`, `adverb`, `particle`,
      `aux-verb`, `conjunction`, `other`
- [x] 1.3 Complete the English list to its nine specified keys, adding `pronoun`, `preposition`,
      `conjunction`, `interjection` and `other`
- [x] 1.4 Remove `phrase` / `句` from both lists — it appears in no spec and in neither Rust
      constant. Keep the `phrase` entry in `posDisplay` so an unexpected stored value still
      renders as a name rather than a raw key
- [x] 1.5 Keep the leading blank "unset" entry the dropdown relies on, and leave `posDisplay`
      otherwise untouched: with keys stored it already returns 「動詞 (verb)」-style labels that
      match both the desktop and `word-edit-ui`'s localization rule

## 2. Android — remove the format tolerance

- [x] 2.1 Drop the display-string branches from `WordFormLabels.forWord` in `quiz/QuizEngine.kt`
      so the Japanese arms match keys only (`"verb" ->`, `"i-adj" ->`, `"na-adj" ->`)
- [x] 2.2 Drop the `"adj"` alias from the English adjective arm for the same reason: it is not a
      canonical key, appears in no spec, and no producer emits it
- [x] 2.3 Confirm the six `== "verb"` comparisons added by `verb-transitivity` need no edit —
      they are correct against the key form. Leave them unchanged and say so in the commit

## 3. Guard against recurrence

- [x] 3.1 Add a Kotlin unit test asserting the two lists contain exactly the keys `word-edit-ui`
      names for each language
- [x] 3.2 Assert in the same test that no entry contains a non-ASCII character — the check that
      would have caught this bug the day it was introduced
- [x] 3.3 Add the equivalent assertion to Rust for its own `EN_POS` / `JA_POS` in `db/labels.rs`.
      `every_suggested_label_is_canonical` iterates these constants but only checks the labels
      they suggest, so a display string in `JA_POS` makes `suggested_labels` return empty and the
      test still passes — Rust is unguarded too, it was merely written correctly
- [x] 3.4 Add a test that a Japanese verb saved through `WordEditViewModel` keeps its
      `transitivity` and `verbGroup`, covering the persistence path that currently nulls them.
      `WordListScreenTest` already shows the Robolectric + real `DbTableSQLite` + ViewModel setup

## 4. Verification

- [x] 4.1 Run the Android unit tests and the Rust test suite. Observe the build isolation rule —
      never both at once, `-j1`
- [x] 4.2 Check the desktop file and the Drive copy for stranded values **before** deciding to
      reset anything: `SELECT DISTINCT part_of_speech FROM words;`. Only a non-key value
      (`動詞`, `い形容詞`, …) means a database is dirty
- [x] 4.3 Judge the phone's copy by provenance, not by query: its database is in the app's private
      `filesDir` and the hand-testing build is a signed release, so `adb run-as` cannot reach it.
      It is clean if no word has been created on Android and no part-of-speech dropdown re-picked
      there since 2026-07-13. Do **not** sync the phone up first to inspect it through Drive —
      latest-wins would overwrite a clean Drive copy with a dirty phone one
- [x] 4.4 If every copy is clean, install the fixed build and keep the data — practice statistics
      survive and the seed needs no regeneration. If any copy is dirty, regenerate the seed and
      delete **all three** before importing; leaving one alive lets sync restore the bad values.
      A stray `phrase` value does not warrant this: it still reads back and only becomes
      unselectable, so editing that one word to a legal part of speech is enough
- [x] 4.5 On Android, create a new Japanese verb from scratch: the three transitivity radio
      buttons appear, and after saving and reopening the word both attributes are still set —
      the defect that motivated this change
- [x] 4.6 On Android, open an existing Japanese verb, re-pick 動詞 in the dropdown, save, and
      confirm the verb attributes survive — the silent-rewrite path
- [x] 4.7 Confirm the newly added options (代名詞 / 介系詞 / 連接詞 / 感嘆詞 / 助動詞) are
      selectable and that a word saved with one reloads showing the same option
- [x] 4.8 Confirm the part-of-speech dropdown renders 「動詞 (verb)」-style bilingual labels in
      Chinese locales, as `word-edit-ui` requires. **On Android this is a change**: storing the
      display string made `posDisplay` fall through to its raw-value branch, so the dropdown read
      a bare 「動詞」— a spec violation the key form corrects. The desktop already conformed and
      must look identical afterwards. This is also the quickest way to tell the builds apart: a
      bare 「動詞」means the old APK is still installed
