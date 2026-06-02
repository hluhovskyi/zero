# Feedback Submitted Screen — Design

## Goal

After a feedback report sends successfully, show a "Feedback sent" confirmation
screen with a Done button, instead of silently navigating back. Implements the
"Sent" state (`SentSheet`) from the `Send Feedback.html` design.

## Current behavior

`DefaultFeedbackViewModel.submit()` on `FeedbackSubmitResult.Success` calls
`onFeedbackSubmittedHandler.onFeedbackSubmitted()`, which is wired to
`navigator.back()` — the sheet just closes with no acknowledgement.

## Change

A success now switches the sheet to a confirmation state held in-sheet, matching
the design (the confirmation replaces the form within the same sheet surface).

- `FeedbackViewModel.State` gains `submitted: Boolean = false`.
- `FeedbackViewModel.Action` gains `object Done`.
- `submit()` success path: `copy(isSubmitting = false, submitted = true)` and does
  **not** call the handler. The failure path is unchanged.
- `Action.Done` calls `onFeedbackSubmittedHandler.onFeedbackSubmitted()` (still
  `navigator.back()`), so the handler keeps a real, single purpose: "submitted
  flow acknowledged, dismiss."
- `FeedbackViewProvider` renders a `SentView` when `state.submitted`, otherwise the
  existing compose form.

## SentView layout (from design `SentSheet`)

Centered column:
- 72dp circle, `secondaryContainer` background, containing a 40dp
  `Icons.Filled.CheckCircle` tinted `secondary`.
- Title "Feedback sent" — 22sp, `FontWeight.ExtraBold`, `primary`.
- Body — 14sp, `onSurfaceVariant`, centered: "Thanks — we'll review it within a
  few days. We may follow up by email if we need more detail."
- "Done" button — full-width, `primaryContainer` background, 16dp radius, white
  16sp bold label; tapping performs `Action.Done`.

## Strings (zero-core `strings.xml`)

- `feedback_sent_title` = "Feedback sent"
- `feedback_sent_body` = "Thanks for the report — it helps make Zero better."
- `feedback_done` = "Done"

## Testing

`DefaultFeedbackViewModelTest`:
- Update "Success invokes handler…" → success sets `submitted = true`, clears
  `isSubmitting`, and does **not** invoke the submitted handler.
- Add: `Done` after a successful submit invokes the submitted handler once.

## Out of scope

The design's attach-screenshot / include-diagnostics toggles and diagnostics card
(also in `Send Feedback.html`) — the task is the submitted confirmation only.
