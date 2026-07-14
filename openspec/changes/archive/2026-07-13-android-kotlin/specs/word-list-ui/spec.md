## ADDED Requirements

### Requirement: Android word list — LazyColumn
On Android, the word list SHALL be implemented as a `LazyColumn` (not a table). Each item row
SHALL display: word, reading (if present), primary meaning, and correct rate (`XX%` or `—`).

Tapping a row SHALL open a read-only detail bottom sheet. Long-pressing a row SHALL show a
`DropdownMenu` with options: Edit, Delete, More Info, Homophones (matching the PC context menu).

Sort SHALL be controlled by a sort button in the top bar cycling through: Word ↑, Word ↓,
Correct Rate ↑, Correct Rate ↓. Sort is performed on the in-memory `DbTableMemory` list.

#### Scenario: Android long-press shows context menu
- **WHEN** the user long-presses a word row in the Android word list
- **THEN** a `DropdownMenu` appears with Edit, Delete, More Info, and Homophones options

#### Scenario: Android unpracticed word shows dash
- **WHEN** a word has `practice_count = 0` in the Android word list
- **THEN** the correct rate column shows `—`

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
