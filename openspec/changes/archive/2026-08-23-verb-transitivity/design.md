## Context

See `proposal.md` — Why, and `doc/explore/202608222102-verb-transitivity.md` for the data survey
behind the numbers quoted here. The state that shapes this design:

- The pair data exists for all 284 verbs but sits inside `words.note` as free text
  (`"動詞類: 2、自動 / 自他: 起こす"`), written there by `~/tmp/gen_vocab_sql.py`. The quiz engine
  never reads `note`, and `word_forms` contains no `transitive_pair` row.
- The importer collapsed two distinct source states: the Markdown writes `-` for "no partner"
  and leaves the cell blank for "unconfirmed", but both produce the same absent output. Only the
  Markdown still distinguishes them.
- `transitive_pair` is already a canonical Japanese label on both platforms, with translations,
  but `suggested_labels("ja","verb")` does not include it.
- `word-form-reading` established that a field with neither value nor reading accepts any input.
  That rule exists so a synonym lacking data is not penalised, but it applies to every field, so
  873 of the user's 1157 words currently quiz with fields that are correct no matter what.
- The same change also established that a word form row with an empty value and empty reading is
  dropped on save, so "an empty row" is not a representation available to this design.

## Goals / Non-Goals

**Goals:**

- Ask about the paired verb and the verb's own transitivity, and grade both.
- Make an empty expectation mean "the answer is nothing", so that "this verb has no partner" is
  expressible without inventing a sentinel value or a new row state.
- Keep the seed reproducible for the parts that are mechanical, and confined to review for the
  parts that are judgement.

**Non-Goals:**

- Preserving existing databases. They are discarded; see the schema decision below.
- Grading the verb group. It is recorded and revealed with the answer, not asked.
- A reusable Markdown-to-SQL skill. This change regenerates the seed once.

## Decisions

### Reset the schema to version 1 instead of adding version 3

The two new columns and the `reading` column from `word-form-reading` are written directly into
the version 1 DDL, and the v1→v2 migration is deleted.

The alternative — a v2→v3 migration — is the textbook choice and was argued for, but it buys
nothing here: every database that exists is going to be discarded anyway, there is exactly one
user, and version numbers spent before release are numbers unavailable for describing real
upgrades later. The operational risk of a downgrade (the version guard refuses to open a file
newer than the app) is handled by deleting every copy: desktop file, Android app data, and the
cloud remote.

*Accepted cost, recorded deliberately:* v1→v2 is currently the only migration the machinery ever
runs, and its tests include the one guarding the `PRAGMA user_version` defect — a desktop-written
file reports 0, which would route Android to a no-op `onCreate` and silently skip the migration.
Deleting the migration leaves `migrateFromDbInfo` with no steps to execute, so that defect is
unguarded until the first post-release schema change. Keeping a driver-only test was offered and
declined. The requirement text in `db-schema` therefore states that the guard and the
version-authority rule stay in place even with no steps to run.

### Store transitivity and verb group on `words`, not as word forms

They describe the verb itself, not one of its inflections. Modelling them as `word_forms` rows
would tell every future reader of that table that "this verb is intransitive" is a kind of
conjugation, and would make a natural query — list the transitive verbs — a join against a
free-text label. Since the schema is being rewritten anyway, columns cost nothing.

Values are language-neutral keys (`intransitive` / `transitive` / `ambitransitive`,
`godan` / `ichidan` / `irregular`), matching how `part_of_speech` is already stored and
displayed.

### Absence of a `transitive_pair` row means "no partner"

No sentinel value, no empty row. The field is always offered for Japanese verbs, so the user
always sees it and either fills it or does not; leaving it blank is a deliberate statement.

This works only because the grading rule changes at the same time — under the old rule an absent
expectation accepted anything, which would make "no partner" unaskable. The two decisions are
one decision.

*Alternatives considered:* a sentinel string such as `-` (the source Markdown's convention) would
have to be excluded from every display and comparison path, and a learner could type it and be
marked correct; permitting an empty row would reverse a rule shipped a week earlier and leave two
ways to spell the same state.

### An empty expectation on the target word requires an empty answer

Applied to every field, not just `transitive_pair`. The narrow alternative — special-casing one
label — would leave the same defect in place for the 873 words that already quiz with
always-correct fields, and would mean touching the same grading code twice.

The synonym exemption stays, because it answers a different question. When the user types a
synonym, grading switches to that synonym's forms; a field the synonym has no data for is not
something the user got wrong. The distinction is therefore *whose* forms are being graded:

```
graded against the originally selected word   → empty expectation demands an empty answer
graded against a synonym the user typed       → a missing field accepts anything
```

### The type question is a choice, not a text field

Three options, none preselected. Free text would invite spelling variants (自動詞 / 自動 / intransitive)
that the comparison rules would then have to normalise, and the answer space is closed anyway.

Two controls (a pair of checkboxes, or two radio buttons for 自動詞 / 他動詞) were considered and
rejected: they admit a "neither selected" state that means nothing, and they cannot express
自他両用. Three options make exactly one answer correct.

The third option is not decoration. `閉じる` (目が閉じる／目を閉じる) and `終わる`
(会議が終わる／会議を終わる) are genuinely ambitransitive and appear in the source data classified
as one side only; without a third option the data would have to keep saying something false.

### Show the verb group on the answer side, and nowhere else yet

The group is worth knowing but not worth asking: it is fully derivable, so quizzing it tests a
rule rather than a fact about the word. Revealing it with the answer costs nothing and puts it
where a learner is already looking.

The word list's More info view would be the other natural home, but it is still an inert menu
entry on both platforms, so "recorded and displayed" would otherwise mean "visible only while
editing".

### Derive the verb group mechanically; classify transitivity by judgement

These two look alike but are not.

**Verb group is computable** from the dictionary form and the ない form, both of which are
present for all 284 verbs with no gaps:

```
起きる → 起きない    stem unchanged after dropping る   → ichidan
働く   → 働かない    く → か                            → godan
終わる → 終わらない  る → ら                            → godan   (ends in る, is not ichidan)
勉強する → 勉強しない                                    → irregular
```

It is more complete than the source's own markings — `寝る` is marked only 自動 with no group,
yet its ない form proves it is ichidan.

The exact split depends on two rule details, so the seed asserts the derivation is total and
reports anomalies rather than hard-coding counts that shift with the rule: compounds ending in
来る (`持って　来る`) are irregular even though the word does not start with it, and a wrong ない
form (`冷やしない`) makes its verb look like a different group. Both surface as anomalies, which
is the point — the derivation doubles as a data check.

**Transitivity is not computable.** The source marks 自動 for 104 verbs, 他動 for only 2, and
leaves 178 unmarked; the author noted intransitivity when it was worth noting, so "unmarked"
carries no information. Coverage after every mechanical avenue is exhausted, counted over the
**261 distinct dictionary forms** rather than the 284 rows (see the duplicate-form decision
below, which is why the two totals differ):

| Source | Verbs |
|---|---|
| Explicitly marked | 98 |
| Inferred from a partner that is itself marked (A and B are always opposite) | 18 |
| Inferred from pair morphology | 41 |
| Requires linguistic judgement | 104 |

Those 104 are classified during seed generation and reviewed before the SQL is applied. This is
where a wrong answer hurts most: a misclassified verb marks a correct learner wrong.

### One word row per distinct dictionary form, with the duplicates reviewed

The 284 source rows cover only **261 distinct dictionary forms**: 23 rows repeat a form already
present. Some are true duplicates carrying the same meaning (`決める` twice, both 決定), while
others are distinct senses of one verb (`できる` as 能夠 and as 建好). Merging blindly would lose
the second kind; keeping every row would create two `word` entries that quiz identically.

The seed therefore emits one row per distinct dictionary form and lists every collision in the
consistency report, so each is resolved as a merge or a deliberate pair of senses. This is not a
detail: `開く`, the verb used above to justify a third transitivity value, is itself a duplicated
form (花が開く and 本を開く occupy separate rows), which shows the duplicates can carry genuinely
different meanings.

Three further source-quality problems surfaced while checking this, and the same report covers
them:

- **Seven rows hold two dictionary forms in one cell** (`作る、造る`, `直す、治す`, `見る、診る`).
  Each needs splitting or a decision to treat one as primary.
- **At least two ない forms are wrong**: `冷やす → 冷やしない` (should be 冷やさない) and `変えす`,
  which is not a verb at all. Because the verb group is derived from the ない form, these surface
  as derivation anomalies rather than passing silently.
- **Four compound verbs contain a full-width space** (`持って　来る`, `連れて　行く`). The ones
  built on 来る are irregular, which a naive rule anchored on the start of the word misses. The
  space also survives into the answer: trimming only touches the ends, so a learner typing
  `持って行きます` without it would be marked wrong. These are normalised on import.
- **Four ない forms carry a usage note instead of a form** (`かからない (改用「無料です」…)`,
  `疲れない (改用「元気です」)`, and two more). They break the group derivation as well, since
  they do not end in ない. The note belongs in `note`, not in the form.
- **Parenthesised readings are embedded in values**: `来る(くる)`, `来て(きて)`, `来た(きた)`,
  `来ない(こない)` in word forms, and `いい(よい)` among the words. This is exactly what
  `word_forms.reading` was added for, and `word-form-reading` explicitly rejected packing
  `value（reading）` into one column — the seed splits them.
- **Six adjective rows have an empty Japanese column** (ハンサム, にぎやか, …), so the derivation
  input falls back to the kana column.

### Derive the adjective conjugations too, from the `な` column

The same split as the verb group: a rule, an exception, and a coverage claim, so it belongs here
rather than only in a task.

```
い-adjective   大きい → 大きくて ／ 大きくない ／ 大きかった
な-adjective   静か   → 静かで   ／ 静かじゃない ／ 静かだった
```

The source's `な` column is the discriminator and it is trustworthy: 34 marked `な` and 66 not,
matching the database's 34 na-adj and 66 i-adj exactly. Two things still go wrong without care:

- **A blank `な` column does not guarantee an い-adjective.** `心配` is marked neither, so a naive
  rule derives `心配くて` — wrong; it is a na-adjective. The seed therefore asserts that a word
  with a blank `な` column ends in `い`, and reports violations. Across all 100 rows only `心配`
  and `いい(よい)` fail, and the second is a parenthesis artefact rather than a real exception.
- **`いい` is irregular** (よくて ／ よくない ／ よかった) and is written `いい(よい)` in the
  source, so matching it literally fails until the parenthesised reading is split out.

### Validate pair consistency when generating the seed, not in the app

If A's partner is B, A and B must have opposite types. In the source, 40 pairs name each other
and can be cross-checked, 4 disagree and need correcting, and 52 name a partner that is not a
vocabulary entry at all.

Because two thirds of the partners are not words in the database, an in-app check would be
unable to say anything about most rows while adding a failure mode to saving. A one-off check at
generation time reports the inconsistencies for review and does not block the pipeline.

## Risks / Trade-offs

- **The stricter empty rule was aimed at 873 words that no longer need it.** Dropping the
  Japanese noun suggestions removes the fields from 773 of them, and deriving the adjective
  conjugations fills the other 100, so almost nothing is left quizzing an empty expectation
  except a verb with no partner — which is the case the rule exists for. The rule stays general
  because it is the correct one, not because a large population depends on it.
- **104 verbs are classified by judgement** → all of them are reviewed before the SQL is
  applied, and the pair-consistency report gives an independent signal on the 40 pairs that
  name each other.
- **The `user_version` defect loses its guard** → accepted; see the schema decision. The rule it
  protects is stated in the `db-schema` requirement so the next migration author inherits it.
- **`note` still carries the old free text** for verbs (`"動詞類: 2、自動 / 自他: 起こす"`) →
  the regenerated seed stops writing it for verbs, since the same facts now have columns;
  `note` keeps its other use, holding the 補充 column for nouns.

## Migration Plan

1. Rewrite the version 1 DDL on both platforms; delete the v1→v2 migration step, its version
   write-back call site, and its tests.
2. Thread the two columns through models, DB read/write, and the edit UI; add
   `transitive_pair` to the Japanese verb suggestions.
3. Change the grading rule, then add the two Japanese verb questions.
4. Regenerate the seed from the Markdown sources, review the transitivity classifications and
   the consistency report, and apply it.
5. Delete every existing database **before installing the new Android build**: the desktop file,
   the Android app's data, and the cloud remote. The helper's version argument drops from 2 back
   to 1 while existing Android files still carry `PRAGMA user_version = 2`, so opening the new
   build over old data hits the default `onDowngrade` and crashes rather than showing the
   friendly version message.
6. Apply the seed with the desktop app closed (`sqlite3 easyvocabook.db < seed.sql` against the
   platform data path) — the app has no import feature — then open it and sync up.

*Rollback:* there is nothing to roll back to, since the databases are discarded. Reverting the
code would require regenerating a seed from the previous schema.

## Open Questions

- Whether the localized names for the three transitivity values should use the Japanese terms
  (自動詞 / 他動詞 / 自他両用) verbatim in Chinese locales, or translated equivalents. The
  Japanese terms are what a learner meets in textbooks; this can be settled when the strings are
  written.
