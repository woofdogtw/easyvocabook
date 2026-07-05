# word-list-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Word list table view
The Word List tab SHALL display all words from `DbTableMemory` in a sortable table with four
columns: Word, Reading, Meaning (primary), Correct Rate.

- **Correct Rate**: displayed as `XX%` where `correct_count / practice_count × 100`; displayed as
  `—` when `practice_count = 0`
- Clicking a column header SHALL toggle sort order (ascending → descending → ascending)
- Sort fields: word (alphabetical), reading (alphabetical), meaning (alphabetical), correct rate (numeric)
- Sort SHALL be performed in `DbTableMemory`, not via SQL

#### Scenario: Sort by correct rate ascending
- **WHEN** the user clicks the "Correct Rate" column header
- **THEN** words are sorted from lowest to highest correct rate; unpracticed words (—) appear first

#### Scenario: Unpracticed word shows dash
- **WHEN** a word has `practice_count = 0`
- **THEN** the Correct Rate column shows `—`

### Requirement: Language filter dropdown
The Word List tab SHALL provide a dropdown to filter words by language. Options: All, English (en),
Japanese (ja), plus any other language present in the database. The filter SHALL apply instantly
to `DbTableMemory` with no SQL query.

#### Scenario: Filter to Japanese words
- **WHEN** the user selects "Japanese" in the language dropdown
- **THEN** only words with `language = "ja"` are shown

### Requirement: Text search
The Word List tab SHALL provide a search input that filters the displayed words in real time.
The search SHALL match against: `words.word`, `words.reading`, `words.meaning` (primary), and
all `word_meanings.meaning` entries (secondary meanings).

#### Scenario: Search matches secondary meaning
- **WHEN** the user types "河岸" and the word "bank" has "河岸" as a secondary meaning
- **THEN** "bank" appears in the search results

#### Scenario: Search with no results shows empty-state message
- **WHEN** the search filter matches no words
- **THEN** the table shows "No words match the current filter" with no add button

### Requirement: Row context menu
Each row in the word list SHALL support a context menu with options:
- Edit: opens the word-edit dialog pre-filled with the word's data
- Delete: shows a confirmation dialog, then deletes the word and all sub-records
- More info: opens a read-only detail view showing all fields
- Homophones: queries `DbTableMemory` for words with the same `reading` and same `language`;
  for English with no reading, matches on `word_forms.phonetic` (IPA) if present

The trigger for the context menu is platform-specific (e.g., right-click on PC, long-press on mobile).

#### Scenario: Delete word removes it from list
- **WHEN** the user confirms deletion of a word
- **THEN** the word is removed from `DbTableSQLite` and from `DbTableMemory`, and disappears from the list

#### Scenario: Homophones for Japanese word
- **WHEN** the user activates the context menu on a Japanese word with reading "あめ" and selects Homophones
- **THEN** all Japanese words with reading "あめ" are shown

### Requirement: Action bar
The Word List tab SHALL display a bottom action bar with:
- **＋** (New): opens the word-edit dialog for a new word
- **🔍** (Search): toggles the search input field
- **🔄** (Sync): triggers an immediate sync (same as Settings → Sync Now)
- **…** (More): opens a menu with: Import words, Export words, Practice statistics summary

#### Scenario: More menu shows three options
- **WHEN** the user taps "…" in the action bar
- **THEN** a menu appears with "Import words", "Export words", "Practice statistics summary"

### Requirement: Empty-state guidance
When the vocabulary book contains zero words (global, not filtered), the Word List tab SHALL
display a friendly empty state with a message and a button to add the first word.

#### Scenario: New install shows empty state
- **WHEN** the database has no words and no filter is active
- **THEN** the table shows "No words yet. Tap ＋ to add your first word." with a highlighted ＋ button

