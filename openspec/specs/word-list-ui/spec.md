# word-list-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.

## Requirements

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

### Requirement: Row hover highlight
When the mouse cursor hovers over a word row, the row SHALL change its background to the
theme's weak background color (`extended_palette().background.weak.color`) to give visual
feedback of the pointed-to row. The highlight SHALL clear when the cursor leaves the list area.

#### Scenario: Hover highlights the row under the cursor
- **WHEN** the user moves the mouse pointer over a word row
- **THEN** that row's background changes to the weak background color; all other rows remain at default

#### Scenario: Highlight clears on cursor exit
- **WHEN** the cursor leaves the word list area entirely
- **THEN** all row backgrounds return to default

### Requirement: Context menu position and edge-flip
The context menu SHALL appear adjacent to the right-click position. If the menu would extend
beyond the window boundary, it SHALL flip to remain within bounds:
- **Horizontal overflow** (right edge): menu flips left of the cursor
- **Vertical overflow** (bottom edge): menu flips upward from the cursor

The overlay SHALL measure the available area at layout time (e.g., via a `responsive` wrapper)
to determine whether flipping is needed.

#### Scenario: Context menu appears at cursor position
- **WHEN** the user right-clicks a row at a position not near any window edge
- **THEN** the context menu top-left corner aligns with the cursor position

#### Scenario: Context menu flips up near bottom edge
- **WHEN** the user right-clicks a row near the bottom of the window and the menu would extend below the window boundary
- **THEN** the context menu bottom edge aligns with the cursor position (menu opens upward)

#### Scenario: Context menu flips left near right edge
- **WHEN** the user right-clicks a row near the right side of the window and the menu would extend past the right boundary
- **THEN** the context menu right edge aligns with the cursor position (menu opens leftward)

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

### Requirement: Action bar
The Word List tab SHALL display a bottom action bar with:
- **＋** (New): opens the word-edit dialog for a new word
- **🔍** (Search): toggles the search input field
- **🔄** (Sync): triggers an immediate sync (same as Settings → Sync Now)
- **…** (More): opens a menu with: Import words, Export words, Practice statistics summary

#### Scenario: More menu shows three options
- **WHEN** the user taps "…" in the action bar
- **THEN** a menu appears with "Import words", "Export words", "Practice statistics summary"

### Requirement: Scrollbar for large lists
When the word list contains more items than fit in the visible viewport, the Word List tab SHALL
display a scrollbar on the trailing edge of the list. The scrollbar SHALL be draggable: tapping
or dragging the thumb SHALL scroll the list to the corresponding position, enabling fast navigation
through large vocabularies (e.g. thousands of words) without repeated swipe gestures.

The scrollbar thumb height SHALL be proportional to the fraction of the list currently visible.
Tapping anywhere on the scrollbar track SHALL jump the thumb (and list) so the thumb centre
aligns with the tap position.

#### Scenario: Scrollbar hidden for short list
- **WHEN** all words fit within the visible viewport
- **THEN** no scrollbar is displayed

#### Scenario: Drag scrollbar thumb to jump position
- **WHEN** the user drags the scrollbar thumb toward the bottom
- **THEN** the list scrolls toward the end proportionally and the thumb follows the finger

### Requirement: Empty-state guidance
When the vocabulary book contains zero words (global, not filtered), the Word List tab SHALL
display a friendly empty state with a message and a button to add the first word.

#### Scenario: New install shows empty state
- **WHEN** the database has no words and no filter is active
- **THEN** the table shows "No words yet. Tap ＋ to add your first word." with a highlighted ＋ button

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

#### Scenario: Android row shows no correct rate
- **WHEN** a word is shown in the Android word list
- **THEN** no correct rate appears on the row; the `—` convention applies to the Comparison cell
  instead, which shows `—` when the word has no companion form

#### Scenario: Android row shows class beside the word
- **WHEN** a Japanese verb is shown in the Android word list
- **THEN** its transitivity appears on the first line next to the word, and its paired verb appears
  on the second line, so the two are never adjacent

#### Scenario: Android word without a companion shows dash
- **WHEN** a word in the Android list has no companion form for its class
- **THEN** the second line shows `—` in place of the companion

#### Scenario: Android sort cycle offers Class, not correct rate
- **WHEN** the user cycles the sort button past Word ↓
- **THEN** the next sort is Class ↑ — the Android row shows no percentage, so it offers no sort
  whose key is invisible

### Requirement: Android word list — FAB for add
On Android, the word list screen SHALL display a `FloatingActionButton` (FAB) in the bottom-right
corner. Tapping it SHALL open the `WordEditSheet` (ModalBottomSheet) in "add new word" mode.
The FAB SHALL be hidden when no words exist (the empty-state view already provides a highlighted
add button) and SHALL be hidden during an active sync.

#### Scenario: Android FAB opens word edit sheet
- **WHEN** the user taps the FAB on the Android word list screen
- **THEN** the `WordEditSheet` ModalBottomSheet slides up in "add" mode

### Requirement: Android word list — draggable scrollbar
The word list SHALL display an overlay scrollbar on the trailing edge when the `LazyColumn` has
more items than the visible viewport. The scrollbar SHALL support drag-to-scroll. Implementation
uses `rememberLazyListState()` with a custom `BoxWithConstraints` composable:

- **Thumb size**: `(visibleCount / totalItems) × trackHeight`, minimum 32 dp
- **Thumb position**: proportional to `firstVisibleItemIndex / (totalItems − visibleCount)`
- **Drag interaction**: `Modifier.pointerInput` with `detectVerticalDragGestures`
  - `onDragStart`: tapping anywhere on the track jumps the list so thumb centre = tap Y
  - `onVerticalDrag`: converts pixel delta → item delta via `dragAmount / maxScrollOffset × movableItems`, calls `listState.scrollToItem()`
- **Touch target**: 20 dp wide; visual thumb 6 dp wide on the trailing edge
- **Visual feedback**: thumb alpha increases from 0.3 to 0.6 while dragging

#### Scenario: Android scrollbar tap-to-jump
- **WHEN** the user taps at 75% down the Android scrollbar track
- **THEN** the list scrolls to approximately 75% of the total word list

### Requirement: Android word list — action bar
On Android, the word list screen SHALL display a top `TopAppBar` containing:
- App name or screen title
- Search icon button: toggles a search `TextField` in the bar
- Overflow menu (⋮) with: Sort, Import, Export, Practice Statistics, Sync Now

The Sync Now action SHALL trigger the same sync logic as the Settings screen Sync Now button.

#### Scenario: Android search toggle shows text field
- **WHEN** the user taps the search icon in the Android word list top bar
- **THEN** a `TextField` appears for real-time filtering of the word list

#### Scenario: Android overflow menu shows actions
- **WHEN** the user taps ⋮ in the Android word list top bar
- **THEN** a dropdown shows Sort, Import, Export, Practice Statistics, Sync Now
