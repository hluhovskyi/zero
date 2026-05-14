# Planning Guide

Follow these rules before calling the `superpowers:writing-plans` skill.

## 1. Commit the design doc first

Always commit the spec/design document (via `superpowers:brainstorming`) before writing an implementation plan. The plan references the spec, and having it in git prevents drift.

## 2. UI features require visual verification

Any feature that touches screens or layouts must include a verification step using the `android-ui-inspector` skill:
- Dump the UI hierarchy after implementing each screen
- Assert that key elements are present before marking a task done
- Do not rely on build success or test passes as a substitute for visual confirmation

## 3. New features — use the scaffold skill, skip boilerplate examples

When the plan adds a new screen or component, run `/zero-project:scaffold-feature` first. The plan should then reference the generated stubs by file path only — no inline code for Component/ViewModel/ViewProvider/Handler boilerplate. Plans that inline the structural scaffold are harder to read and add no value once the files exist.

## 4. Multi-PR features — resolve cross-cutting decisions in a roadmap, not in phase plans

**When a feature ships across multiple PRs, write a roadmap doc first that pins every cross-cutting decision** (schema flexibility, cadence shape, reuse vs. new aggregation, audit-log vs. none, etc.). Each phase plan then only mechanizes the roadmap and never re-litigates architecture. Phase plans must reference the roadmap by link, not restate its decisions — restating them invites silent drift between phases. See `docs/superpowers/plans/2026-05-13-budget-roadmap.md` as the canonical example.
