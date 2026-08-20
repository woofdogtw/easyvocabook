## MODIFIED Requirements

### Requirement: Row context menu
Each row in the word list SHALL support a context menu with options:
- Edit: opens the word-edit dialog pre-filled with the word's data
- Delete: shows a confirmation dialog, then deletes the word and all sub-records
- More info: opens a read-only detail view showing all fields
- Homophones: queries `DbTableMemory` for words of the same `language` that share the word's
  `reading`; for a word that has no reading, words with the same spelling are shown instead.
  No word form label SHALL be consulted as a pronunciation fallback. Both comparisons SHALL use
  the same semantics as quiz answer matching — trim both sides, Unicode case folding, no kana
  folding — so that the two features cannot drift apart.

The trigger for the context menu is platform-specific (e.g., right-click on PC, long-press on mobile).

Implementation status: Homophones and More info are currently implemented on Android only; on PC
both menu entries are present but inert. Closing that gap is out of scope here and is tracked as
an existing deficiency.

#### Scenario: Delete word removes it from list
- **WHEN** the user confirms deletion of a word
- **THEN** the word is removed from `DbTableSQLite` and from `DbTableMemory`, and disappears from the list

#### Scenario: Homophones for Japanese word
- **WHEN** the user activates the context menu on a Japanese word with reading "あめ" and selects Homophones
- **THEN** all Japanese words with reading "あめ" are shown

#### Scenario: Word with no reading falls back to spelling
- **WHEN** the user selects Homophones on a word that has no reading
- **THEN** other words of the same language with the same spelling are shown, compared with the
  same trimming and case folding used for quiz answers

#### Scenario: Retired phonetic label is not consulted
- **WHEN** a word has no `reading` but has a form labelled `phonetic`
- **THEN** that label is not used to find homophones
