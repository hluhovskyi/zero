# Feedback redesign — Design

**Goal:** Rebuild the Send Feedback bottom sheet to match the new design and tag each report on GitHub with a bug/idea/other label selected by the user.

**Out of scope:** screenshot attachment, diagnostics toggle, in-sheet "Sent" confirmation (sheet still closes on success, as today).

**Design source:** `ui_kits/zero/Send Feedback.html` (Send Feedback bundle, hash `7H31x8k_jcjN5ln1n3svRQ`).

## What changes

### `zero-api`
- New `FeedbackType` enum: `Bug`, `Idea`, `Other`. Each value carries a `label: String` (`"bug"` / `"idea"` / `"other"`) used verbatim as the GitHub label.

### `zero-core`
- `FeedbackViewModel.State` gains `type: FeedbackType = FeedbackType.Bug`.
- `FeedbackViewModel.Action` gains `SelectType(type: FeedbackType)`.
- `DefaultFeedbackViewModel` forwards `state.type` to the formatter.
- `FeedbackReportFormatter` accepts a `FeedbackType` and emits labels `["feedback", type.label]` (`+ "debug"` when `isDebugBuild`).
- `FeedbackViewProvider` rewritten to match the design:
  - Header row: close icon (left, 48dp tap target) + centered title in `PrimaryContainer` + 48dp spacer.
  - Eyebrow body text under header.
  - Type pill row — three equal-width pills (Bug / Idea / Other). Selected pill: white background, 1.5dp `PrimaryContainer` border, icon + label in `PrimaryContainer`. Unselected: `SurfaceContainerLow` background, transparent border, icon + label in `OnSurfaceVariant`.
  - Textarea card: `SurfaceContainerLow` background, 16dp radius. ALL-CAPS "WHAT HAPPENED?" label, multi-line text input (no border, transparent background), right-aligned char counter (`{n}/1000`, `Error` color past 900).
  - Send button: full-width 16dp-radius rectangle. `PrimaryContainer` background + white text when `text.isNotBlank() && !isSubmitting`; `SurfaceContainer` background + `Outline` text otherwise.
  - Privacy footnote: small centered text in `Outline`.
  - Close icon dispatches a new `OnFeedbackCloseHandler.onFeedbackClose()` (wired in `FeedbackComponent.Module` to `Navigator.back()`).
- Drag handle stays where it is: provided by `MainActivityScreenViewProvider` for all `PartiallyVisible.BottomSheet` entries, not by `FeedbackViewProvider`.
- Placeholder text varies by type (per design): bug → bug example, idea → idea example, other → generic "Tell us what's on your mind…".

### `zero-ui`
- Three new vector drawables under `zero-ui/src/main/res/drawable/`: `ic_feedback_bug_24.xml`, `ic_feedback_idea_24.xml`, `ic_feedback_other_24.xml`. Paths copied verbatim from the design's `TYPES` array (24×24 viewBox).
- One new drawable `ic_close_24.xml` (Material close icon path) if no equivalent exists in the module.

### `app`
- `FeedbackComponent.Module` adds `@Provides` for `OnFeedbackCloseHandler` returning `Navigator.back()` (mirrors existing `OnFeedbackSubmittedHandler`).
- `FeedbackSheetComponent` receives the new close handler and forwards it to `DefaultFeedbackViewModel` (which exposes a `close()` Action; the view dispatches it from the close icon).

### `functions/feedback`
- Add a server-side label allowlist: `feedback`, `debug`, `bug`, `idea`, `other`. Filter the incoming `labels` array to this set before calling Octokit; cap to 5 entries. Reject the request with 400 if title or body is missing (already implemented).
- **Why deploy:** without the allowlist, a tampered AAB could spam arbitrary labels on the issue tracker. Adding the type label on the client requires the function to accept it — `bug` / `idea` / `other` are added to the allowlist as part of this deploy.

## String resources

Add to `zero-core/src/main/res/values/strings.xml`:
- `feedback_eyebrow` — "Found a bug or have an idea? Tell us what happened — we read every report."
- `feedback_type_bug` — "Bug"
- `feedback_type_idea` — "Idea"
- `feedback_type_other` — "Other"
- `feedback_what_happened` — "What happened?"
- `feedback_hint_bug` — bug-example placeholder
- `feedback_hint_idea` — idea-example placeholder
- `feedback_hint_other` — "Tell us what's on your mind…"
- `feedback_close` — content description for the close icon
- `feedback_privacy_footnote` — "Feedback is sent to the Zero team. No account data, balances or transactions are included."
- `feedback_char_counter` — `"%1$d/1000"`

Keep `feedback_title`, `feedback_submit`, `feedback_error_generic`.

## Test coverage

- `FeedbackReportFormatterTest` — extend existing tests to pass a `FeedbackType`. New test: `labels include selected type key`.
- `DefaultFeedbackViewModelTest` — new test: `SelectType updates state.type`. Existing tests pick a default type explicitly.

## Risks & accepted trade-offs

- **No in-sheet "Sent" confirmation** — the design shows a third frame with a checkmark; we keep current behavior (sheet closes on success). Lower scope, no extra state machine. If user demand surfaces, easy to layer on later.
- **Label allowlist on the function is permissive about unknowns** — we drop unknowns silently rather than 400 the request, so an older app version that sends an unknown label still files an issue (just without that label).
- **Char limit is UI-only** — counter goes red past 900 and shows `/1000`, but submission isn't blocked at 1000; the GitHub issue body has no practical length limit, so capping it on the server side is unnecessary.
