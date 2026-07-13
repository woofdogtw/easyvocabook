# rust-ux Specification

## Purpose
TBD - created by archiving change android-kotlin. Update Purpose after archive.
## Requirements
### Requirement: Rust desktop — keyboard navigation in quiz and edit

The Rust/iced desktop application SHALL support keyboard navigation to avoid requiring
the mouse between input fields.

**Tab / Shift+Tab focus cycling**: Pressing `Tab` SHALL move focus to the next focusable
text input in widget-tree order; `Shift+Tab` SHALL move focus to the previous input.
This applies globally across all screens (quiz typing view, word edit dialog, etc.).
Implemented via `iced::widget::operation::focus_next()` / `focus_previous()` dispatched
from the application-level keyboard subscription.

**Enter to submit quiz answer**: In the typing quiz view, pressing `Enter` in any text
input (word field or word_form field) SHALL trigger the same action as clicking [Submit].
Implemented via `text_input::on_submit(Message::QuizSubmit)` on each input.

**Enter to advance after result**: On the quiz result screen (after submit or give up),
pressing `Enter` SHALL trigger the same action as clicking [Next →].
This is handled via the application-level keyboard subscription, which only fires when
the key event is **not** already captured by a focused text input (i.e. `status !=
event::Status::Captured`), preventing double-triggering during the submit flow.

#### Scenario: Tab moves focus between quiz typing fields
- **WHEN** the user is on the typing quiz and presses `Tab`
- **THEN** focus moves from the current text input to the next one in order

#### Scenario: Enter submits typing answer
- **WHEN** the user has typed an answer in any typing quiz field and presses `Enter`
- **THEN** the answer is submitted as if [Submit] was clicked

#### Scenario: Enter advances from result to next card
- **WHEN** the quiz result is shown and the user presses `Enter`
- **THEN** the next quiz card is loaded as if [Next →] was clicked

