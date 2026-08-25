## Context

See proposal.md — *Why*. The constraints that shape the approach:

Both platforms already load what this needs. Desktop's `load_all` calls `load_sub_records(id)` per
row, Android's `listWords` batches `WHERE word_id IN (...)`; either way `wordForms` is populated
before the list renders, and `transitivity`, `verb_group` and `part_of_speech` are columns on
`words`. No query changes.

The two list surfaces are structurally different — a six-column desktop table with clickable
column headers, and an Android `LazyColumn` sorted by a cycling top-bar button — and they are not
being made to converge.

## Goals / Non-Goals

**Goals:**

- Reading a row tells the user what the word is and what it pairs with, without opening it.
- One rule decides both cells, so neither needs a per-row label.

**Non-Goals:**

- Antonyms, and any other "related word" that is not a companion *form*. See proposal.md.
- Matching the two platforms' layouts. They already differ; forcing a shared layout would make
  both worse.
- The desktop N+1 in `load_all`. It predates this change and this change does not worsen it —
  worth its own look, not this one's.
- New quiz behaviour. Nothing here changes what is asked or graded.

## Decisions

### One classification decides both cells

The badge is not decoration next to the companion; it is what makes the companion interpretable.
`上げる` alone does not say whether it is the transitive partner of the row's word or the row's
word restated. Knowing the row is 自 settles it.

The rule is a single lookup: a Japanese verb resolves to its `transitivity`, everything else to its
`part_of_speech`; the companion is then determined by that classification. Because the
classification is on screen, the companion never needs its own label — a column of bare `went`,
`children`, `上げる` reads correctly once each row says what kind of word it belongs to.

**Alternative considered:** label the companion instead (`他: 上げる`). It is unambiguous while a
companion exists and says nothing when one does not — failing exactly where the badge is most
needed, on a verb with no partner.

### Ownership is established by distance, not by wording

`自 → 上げる` reads as though 自 describes 上げる. No phrasing fixes this reliably; adjacency is
what causes it. Both layouts therefore place the badge against the word and the companion far from
it — separated by Reading and Meaning on desktop, on the opposite line on Android.

This is why the Android layout is a 2×2 grid rather than a single wrapped line: a line that wraps
where the text happens to run could place badge and companion side by side at some widths.

### Desktop keeps Correct Rate; Android drops the number only

Desktop sorts by clicking column headers, so removing the column would remove the only way to sort
by accuracy — and "which words do I keep failing" is the one thing accuracy is good for. Six
columns fit; keeping it costs nothing.

Android sorts through a cycling button that is independent of what the row displays, so dropping
the number there loses no capability. The number is the redundant part: per-row it says little, and
the sort already answers the question it exists for.

The result is a deliberate asymmetry, and it is the honest one — the platforms differ in how they
sort, so they differ in what they can afford to drop.

### Abbreviations follow the word's language, not the interface locale

Truncating the existing names gives 自他両用 → 自, which is wrong, so a parallel set of `.abbr`
strings is added and the full names stay untouched for the edit screen.

The badge draws from **two key namespaces at once** — `transitivity` for Japanese verbs and
`part_of_speech` for everything else — and the column never says which. An abbreviation must
therefore be **unique across the union of `TRANSITIVITY_KEYS`, `EN_POS` and `JA_POS`**, or the same
character means two things on two rows. The obvious collision: 其他 abbreviates naturally to 他,
which is already 他動詞. It takes 其 instead.

**Which script a badge uses is decided by the language of the word, not by the interface locale.**
Within CJK, the traditional/simplified variant still follows the interface locale, exactly as the
full part-of-speech names already do (`動詞 (verb)` in zh-TW, `动词 (verb)` in zh-CN).

| key | ja word, zh-TW | ja word, zh-CN | ja word, en | en word |
|---|---|---|---|---|
| `intransitive` | 自 | 自 | 自 | — |
| `transitive` | 他 | 他 | 他 | — |
| `ambitransitive` | 自他 | 自他 | 自他 | — |
| `noun` | 名 | 名 | 名 | N |
| `verb` | 動 | **动** | 動 | V |
| `adjective` | 形 | 形 | 形 | Adj |
| `adverb` | 副 | 副 | 副 | Adv |
| `pronoun` | 代 | 代 | 代 | Pron |
| `preposition` | 介 | 介 | 介 | Prep |
| `conjunction` | 連 | **连** | 連 | Conj |
| `interjection` | 感 | 感 | 感 | Interj |
| `other` | 其 | 其 | 其 | Oth |
| `i-adj` | い | い | い | — |
| `na-adj` | な | な | な | — |
| `particle` | 助 | 助 | 助 | — |
| `aux-verb` | 助動 | **助动** | 助動 | — |

Under an English interface a Japanese word keeps the unsimplified forms. There the choice is not
between Chinese variants at all — it is Japanese orthography, which writes 動詞 and 助動詞, and the
existing locale table already prints `i-adjective` in English while keeping the い. The column
happens to equal zh-TW; the reason is different.

Only three abbreviations differ between the Chinese variants — 動/动, 連/连, 助動/助动 — and each
set is unique within its own locale (16/16 both ways). The English column is identical in all three
interface locales, since a Latin abbreviation has no variant to localise.

An English word therefore reads `go  V  went` in every locale, and a Japanese word reads
`上がる 自 上げる` in every locale. What varies by locale is only the character variant, never the
writing system. Both complaints that produced this rule are answered at once, and the badge column
never mixes two scripts within one language.

**Why not split by interface locale.** The earlier attempt kept a set of "Japanese-only" categories
CJK everywhere and let the rest follow the locale, justified as "these have no English word". That
justification is false for five of the seven: intransitive, transitive, ambitransitive, particle
and auxiliary verb are all ordinary English grammatical terms; only い形容詞 and な形容詞 genuinely
lack an English name. What those seven actually share is that they can only ever appear on a
Japanese word — which is this rule, described badly. Extending it to the remaining nine makes the
criterion objective (what language is this word?) instead of a per-key judgement about whether a
category counts as universal.

It also avoids mixing scripts within one column: under the locale split, an English-locale user
looking at this vocabulary set would see `N` on 773 noun rows and 自 / 他 on 289 verb rows — the
same concept in two writing systems, switching on whether English happens to have a word for it.

The cost is that a Chinese-locale user sees `N / V / Adj` on English words rather than 名 / 動 / 形.
That is the notation Chinese-market English dictionaries have used for decades (`n.` `v.` `adj.`),
so it reads as convention rather than as untranslated text.

⚠️ Latin abbreviations are wider than two CJK characters (`Interj` is the worst), so the Class
column is sized for them — the program must handle English words whether or not any exist today.

Note 助 and 助動 differ by one character and sit in the same list; they are distinguishable but
worth watching in the rendered table.

### The classification rule lives where the database layer can reach it

Sorting happens in `DbTableMemory`, which the delta spec keeps as a requirement, and both new sort
fields compare values this rule produces. Putting the rule in the UI layer — the intuitive home,
since a badge is a UI concept — would leave `db` unable to depend on it and the Comparison sort
unimplementable.

On Rust it therefore goes in `db/labels.rs`, beside `suggested_labels` and the canonical
vocabularies it already draws on, reachable from both `db/memory.rs` and `ui/word_list.rs`. Kotlin
has no such constraint — Android does not sort on Comparison — but the rule is placed
symmetrically so the two implementations stay comparable.

### `—` applies uniformly, including to ambitransitive verbs

Treating 自他両用 as "no companion by definition" was considered and rejected against the data: of
the four ambitransitive verbs recorded, three name a partner (開きます→開ける twice, つきます→つける).
A rule that hid them would suppress true information to make a category tidier. One rule — show the
companion if the word has one, `—` otherwise — covers every case.

### Class sorts by key, which is not the order the badges suggest

Sorting Class alphabetically by key yields `ambitransitive, i-adj, intransitive, na-adj, noun,
transitive, …` — so 自他, い, 自, な, 名, 他. Words of the same kind do group together, which is
what the requirement asks and what makes the column worth sorting, but the three transitivity
values are not adjacent to each other.

Sorting by the displayed abbreviation instead would reorder by locale, and a hand-written order
would be one more table to keep in step with two others. The key order is accepted; it is recorded
here so the sequence is not mistaken for a defect during verification.

## Risks / Trade-offs

**The comparison column is empty for most rows** → In the user's 1162 words it holds a value for
201 (17%): 101 paired verbs plus all 100 adjectives. The other 961 (83%) show `—` — 773 nouns,
which have no companion concept, and 188 verbs with no partner. Class, by contrast, is populated
for every row.

This is the column reporting the truth, not failing. But it decides the layout: **Comparison gets a
narrow fixed width rather than `Length::Fill`**, because a column that is blank 83% of the time
should not take a quarter of the flexible width from Word, Reading and Meaning — and Meaning is the
column already flagged as at risk below. Companion words are short (上げる, 開ける, 開けない), so a
fixed width suffices. Sorting by the column gathers the 201 rows that have a value, which makes the
sparsity navigable rather than merely visible.

**Six columns crowd a narrow desktop window** → Only Word, Reading and Meaning stay `Fill` and
shrink together. Class is fixed and narrow, sized for the widest Latin abbreviation (`Interj`)
rather than for two CJK characters; Comparison is fixed (see above); and Correct Rate keeps its
existing width. `sort_header` currently picks between two widths
with a single `if field == SortField::CorrectRate`; it now has to express three, so that expression
is replaced rather than extended. Meaning is the column at risk, and it is checked at the narrowest
window the app allows before this is called done.

**Android's second line has to hold meaning and companion together** → A 6:4 split with independent
wrapping per cell, verified on a real device rather than assumed. If the meaning proves too
cramped, the ratio is the adjustment, not the layout.

**A stale scenario name outlives its meaning** → `Android unpracticed word shows dash` describes a
number the Android row no longer displays. A scenario cannot be renamed from inside a change, so
its body is rewritten now and the rename happens in the main spec after archiving — the same
sequence `word-form-reading` used for the stale `(future)` qualifier.
