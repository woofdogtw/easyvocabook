## 0. Clear the way in `db/labels.rs`

- [ ] 0.1 Delete `pos_display` from `rust/src/db/labels.rs`. It has no callers — the desktop
      renders parts of speech through `pos_locale_key()` + `t()` against `locale/mod.rs` — and its
      table contradicts the live one, mapping ja/conjunction to 接続詞 where the app shows
      連接詞. Dead code that disagrees with the running code misleads whoever reads it next, and
      this change adds the classification rule to the same file
- [ ] 0.2 Remove the file-wide `#![allow(dead_code)]` from `db/labels.rs`. It concealed exactly one
      item, the function above; with that gone the file compiles clean and future dead code there
      will warn
- [ ] 0.3 Give the remaining `#[allow(dead_code)]` sites a stated reason, as `network/ftp.rs`
      already does — `db/sqlite.rs` (`update_book_info`), `network/sync.rs` (`run_sync`) and
      `db/types.rs` (data-carrier fields, file-wide). An unexplained allow is how a contradictory
      copy survived six weeks unnoticed
- [ ] 0.4 Confirm the Rust suite still passes; nothing here changes behaviour

## 1. Shared: abbreviated label strings

- [ ] 1.1 Add `.abbr` locale strings for the three transitivity keys in `locale/mod.rs` for en,
      zh-TW and zh-CN, leaving the existing full names untouched
- [ ] 1.2 Add `.abbr` strings for every part of speech in both canonical lists, using the table in
      design.md. The script follows the **word's language**, not the interface locale: `JA_POS`
      keys take CJK (名 / 動 / 形 / 副 / 代 / 介 / 連 / 感 / 其 / い / な / 助 / 助動) and `EN_POS`
      keys take Latin (N / V / Adj / Adv / Pron / Prep / Conj / Interj / Oth). Within CJK the
      traditional/simplified variant still follows the interface locale, as the full names already
      do — three abbreviations differ (動/动, 連/连, 助動/助动). Under the **English** interface a
      Japanese word keeps the unsimplified forms (動 / 連 / 助動): the choice there is Japanese
      orthography, not a Chinese variant. The Latin set is identical in all three locales
- [ ] 1.3 The abbreviations MUST be unique across the union of `TRANSITIVITY_KEYS`, `EN_POS` and
      `JA_POS` — the badge shows both namespaces without saying which, so a repeated value would
      mean two things on two rows. `other` takes 其, not 他, because 他 is already 他動詞. Note the
      five keys in both lists (`noun`, `verb`, `adverb`, `conjunction`, `other`) need one CJK and
      one Latin form each, keyed by the word's language
- [ ] 1.4 Mirror both sets into `kotlin/app/src/main/res/values*/strings.xml`
- [ ] 1.5 Add `TRANSITIVITY_KEYS` as a list constant in Kotlin's `Labels.kt`, following `EN_POS` and
      `JA_POS`. Only a `when` mapping exists today, and a test cannot iterate a `when`
- [ ] 1.6 Add a test on each platform asserting every key in `EN_POS`, `JA_POS` and
      `TRANSITIVITY_KEYS` resolves to an abbreviation for its own language **in every locale** —
      the gap that let 32 zh-CN strings silently fall back during `word-form-reading`. On Android
      this means running the assertion under `@Config(qualifiers = "zh-rTW")` and `"zh-rCN"` as
      well as the default; checking only the default locale would miss exactly what this guards
- [ ] 1.7 Assert uniqueness in the same test, once per locale

## 2. Shared: the classification rule

- [ ] 2.1 Add a function resolving a word to its Class key: a Japanese verb with a recorded
      `transitivity` yields that, everything else yields `part_of_speech`; absent both, empty
- [ ] 2.2 Add a function resolving a word to its Comparison value from that Class: `transitive_pair`
      for a Japanese verb, `negative` for an adjective, `past_tense` for an English verb, `plural`
      for an English noun, `comparative` for an English adjective; empty otherwise. The recorded
      form is shown whether or not it is irregular — `walk → walked` too — since detecting
      irregularity would need rules and data this change does not add
- [ ] 2.3 On Rust, put both functions in `db/labels.rs` — `db/memory.rs` performs the sorting and
      cannot depend on `ui/`, so a UI-layer home would make the Comparison sort unimplementable.
      Place the Kotlin equivalents symmetrically
- [ ] 2.4 Implement both on both platforms with matching behaviour, and unit-test them there:
      a paired verb, a partnerless verb, an ambitransitive verb that has a partner, a noun, and a
      word whose language has no rule

## 3. Desktop: six-column table

- [ ] 3.1 Add `SortField` variants for **both** new columns — Class sorts on the classification
      key, Comparison on the companion word — and implement their comparisons in `db/memory.rs`
      alongside the existing four
- [ ] 3.2 Insert the Class column after Meaning at a fixed narrow width, and the Comparison column
      after it also at a fixed width — it is blank for 83% of rows and must not take flexible width
      from Meaning. Only Word/Reading/Meaning stay `Length::Fill`; Correct Rate keeps its width
- [ ] 3.3 Replace the two-way width expression in `sort_header`
      (`if field == SortField::CorrectRate { Fixed(130) } else { Fill }`) — it now has to express
      three widths, so extending it with another branch would not scale
- [ ] 3.4 Add both column headers to the sortable header row with their locale strings
- [ ] 3.5 Render `—` in Comparison when the word has no companion; leave Class empty when the word
      records no classification at all
- [ ] 3.6 Confirm Class is never rendered adjacent to Comparison — Reading and Meaning separate them

## 4. Android: 2×2 row layout

- [ ] 4.1 Restructure the row into two lines of two cells: word with reading and Class on the first,
      meaning and Comparison on the second, at roughly 8:2 and 6:4
- [ ] 4.2 Let each cell wrap its own text independently, so a long meaning does not displace the
      companion
- [ ] 4.3 Remove the correct-rate number from the row; leave the sort cycle including
      `RATE_ASC`/`RATE_DESC` untouched
- [ ] 4.4 Style the Class as a chip or otherwise visually distinct, so it reads as an attribute of
      the word rather than another word

## 5. Verification

- [ ] 5.1 Run the Rust test suite and the Android unit tests. Observe the build isolation rule —
      never both at once, `-j1`
- [ ] 5.2 Desktop: confirm a paired verb shows its transitivity and partner, a partnerless verb
      shows `—` while still showing its transitivity, and a noun shows its part of speech with `—`
- [ ] 5.3 Desktop: click the Class header and confirm words group by classification — all
      intransitive verbs together, all nouns together. Expect key order
      (`ambitransitive, i-adj, intransitive, na-adj, noun, transitive …`), so the three transitivity
      values will not be adjacent; that is by design, not a defect
- [ ] 5.4 Desktop: click the Comparison header and confirm the words that have a companion group
      together, away from the `—` rows
- [ ] 5.5 Desktop: narrow the window to the smallest size the app allows and confirm Meaning is
      still readable and no column collapses
- [ ] 5.6 Android: confirm the two-line layout on a real device, with a long meaning and a long
      companion in the same row
- [ ] 5.7 Android: confirm the sort button still cycles through Correct Rate ↑/↓ and reorders the
      list, with no percentage displayed
- [ ] 5.8 Both: create one English word and confirm its Class shows a Latin abbreviation while the
      Japanese rows keep CJK, with the interface in zh-TW, zh-CN and English — the current data is
      entirely Japanese, so this path has no natural coverage. Confirm 動 in zh-TW reads 动 in
      zh-CN on the same word
- [ ] 5.9 Both: confirm the Class never appears next to the Comparison, and that 自他両用 verbs
      show their partner where one is recorded (開きます→開ける) and `—` where none is (閉じます)
- [ ] 5.10 After archiving, rename the scenario `Android unpracticed word shows dash` in
      `openspec/specs/word-list-ui/spec.md` to match what it now says. A scenario cannot be renamed
      from inside a change, so the main spec is edited directly afterwards — the same follow-up
      `word-form-reading` and `verb-transitivity` both needed
