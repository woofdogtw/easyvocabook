## MODIFIED Requirements

### Requirement: Word list table view
The Word List tab SHALL display all words from `DbTableMemory` in a sortable table with six
columns: Word, Reading, Meaning (primary), Class, Comparison, Correct Rate.

- **Class**: the classification that determines what the Comparison column holds. For a Japanese
  verb it is the transitivity (自 / 他 / 自他); for every other word it is the part of speech.
  Shown abbreviated, and the abbreviation SHALL be unique across the transitivity and
  part-of-speech key sets, since the column draws from both without indicating which. The
  abbreviation SHALL be chosen by the language of the word rather than by the interface locale: a
  Japanese word SHALL show a CJK abbreviation and an English word a Latin one, in every locale.
  Within CJK the traditional or simplified variant SHALL follow the interface locale, as the full
  part-of-speech names already do. Empty when the word records neither.
- **Comparison**: the companion form that the Class implies — for a Japanese verb the opposite
  verb, for an adjective its negative form, for an English verb its past tense, for an English noun
  its plural. The form is shown whenever the word records one, whether or not it is irregular.
  Displayed as `—` when the word records no such form. Because the Class is shown, the Comparison
  needs no per-row label of its own.
- **Correct Rate**: displayed as `XX%` where `correct_count / practice_count × 100`; displayed as
  `—` when `practice_count = 0`
- The Class SHALL be rendered adjacent to the word it describes and SHALL NOT be rendered adjacent
  to the Comparison, so that it cannot be read as describing the companion instead of the word
- Clicking a column header SHALL toggle sort order (ascending → descending → ascending)
- Sort fields: word (alphabetical), reading (alphabetical), meaning (alphabetical), class
  (alphabetical by namespace then key, so part-of-speech and transitivity badges form separate
  blocks and words with no class sort last), comparison (alphabetical), correct rate (numeric) —
  all six columns sort
- Sort SHALL be performed in `DbTableMemory`, not via SQL

#### Scenario: Sort by correct rate ascending
- **WHEN** the user clicks the "Correct Rate" column header
- **THEN** words are sorted from lowest to highest correct rate; unpracticed words (—) appear first

#### Scenario: Unpracticed word shows dash
- **WHEN** a word has `practice_count = 0`
- **THEN** the Correct Rate column shows `—`

#### Scenario: Japanese verb shows its transitivity and partner
- **WHEN** the list shows a Japanese verb recorded as intransitive with a `transitive_pair` form
- **THEN** the Class column shows the abbreviated 自 and the Comparison column shows the paired verb

#### Scenario: Verb without a partner still shows its class
- **WHEN** the list shows a Japanese verb that has no `transitive_pair` form
- **THEN** the Comparison column shows `—` and the Class column still shows its transitivity

#### Scenario: Ambitransitive verb shows a partner when one is recorded
- **WHEN** the list shows a verb recorded as ambitransitive that has a `transitive_pair` form
- **THEN** the Class column shows the abbreviated 自他 and the Comparison column shows that partner,
  the same rule applied to any other verb

#### Scenario: Badge script follows the word, not the interface language
- **WHEN** the list shows an English word and a Japanese word while the interface is in any single
  locale
- **THEN** the English word's Class is a Latin abbreviation and the Japanese word's Class is a CJK
  one, so the column never renders the same category in two scripts within one language

#### Scenario: Non-verb falls back to part of speech
- **WHEN** the list shows a word that records no transitivity, such as a Japanese noun
- **THEN** the Class column shows its abbreviated part of speech and the Comparison column shows `—`

#### Scenario: Sort by class groups words of the same kind
- **WHEN** the user clicks the "Class" column header
- **THEN** words are sorted by their class, gathering all intransitive verbs together, all
  transitive verbs together, and so on

#### Scenario: Sort by comparison groups the words that have one
- **WHEN** the user clicks the "Comparison" column header
- **THEN** words are sorted by their companion word, which gathers the words that have one apart
  from those showing `—`

### Requirement: Android word list — LazyColumn
On Android, the word list SHALL be implemented as a `LazyColumn` (not a table). Each item row SHALL
be laid out as two lines of two cells each, every cell wrapping its own text independently:

- First line: the word with its reading (if present), and the Class
- Second line: the primary meaning, and the Comparison

Class and Comparison carry the same meaning as in the desktop table, and the Class SHALL be
rendered on the first line beside the word rather than beside the Comparison. The correct rate is
not shown as a number on Android; it remains available through the sort control.

Tapping a row SHALL open a read-only detail bottom sheet. Long-pressing a row SHALL show a
`DropdownMenu` with options: Edit, Delete, More Info, Homophones (matching the PC context menu).

Sort SHALL be controlled by a sort button in the top bar cycling through: Word ↑, Word ↓, Class ↑,
Class ↓. Sort is performed on the in-memory `DbTableMemory` list. Correct rate is neither displayed
nor sortable on Android: a sort key the list never shows produces an order the user cannot account
for. The desktop, which does display the number, keeps sorting by it.

#### Scenario: Android long-press shows context menu
- **WHEN** the user long-presses a word row in the Android word list
- **THEN** a `DropdownMenu` appears with Edit, Delete, More Info, and Homophones options

#### Scenario: Android unpracticed word shows dash
- **WHEN** a word has `practice_count = 0` in the Android word list
- **THEN** no correct rate is shown for it, because the Android row no longer displays that number;
  the `—` convention applies to the Comparison cell instead, which shows `—` when the word has no
  companion form

#### Scenario: Android row shows class beside the word
- **WHEN** a Japanese verb is shown in the Android word list
- **THEN** its transitivity appears on the first line next to the word, and its paired verb appears
  on the second line, so the two are never adjacent

#### Scenario: Android word without a companion shows dash
- **WHEN** a word in the Android list has no companion form for its class
- **THEN** the second line shows `—` in place of the companion

#### Scenario: Android correct rate remains sortable without being displayed
- **WHEN** the user cycles the sort button past Word ↓
- **THEN** the next sort is Class ↑, not correct rate — the Android row shows no percentage, so it
  offers no sort whose key is invisible
- **NOTE** the scenario name predates this decision and is renamed in the main spec after archiving
