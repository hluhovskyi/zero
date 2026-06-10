# Superpowers Workflow — Project-Specific Optimizations

Read this before invoking `writing-plans` or `brainstorming`. These rules override the superpowers defaults for this project.

## Commit the design doc before writing the plan

Always commit the spec/design document (from `brainstorming`) before writing an implementation plan. The plan references the spec, and having it in git prevents drift.

## Design-to-PR flows — split into two sessions

**For any task that involves fetch-design + plan + implementation: use two sessions.** Session 1: fetch design, write plan, commit. Session 2: execute plan (starts fresh; design HTML and chat logs never enter the implementation context). Running all three in one session carries the full design archive through implementation and typically forces a mid-session compaction.

## writing-plans — Keep plans under 400 lines

The `writing-plans` skill generates full code blocks by default. In this project, that produces ~2000-line plans that consume the entire context window during execution. Shorten plans by replacing repeated patterns with references:

**Replace with a skill call:**
- Any Component/ViewModel/ViewProvider/Handler scaffolding → "Run `scaffold-feature` for `<Name>`" (generates all four files from a stub)

**Replace with a doc reference:**
- Navigation wiring → "Wire per [Navigation](navigation.md)"
- Room DAO + criteria pattern → "Add criteria + query per [Data Layer](data-layer.md)"
- Dagger component structure → "See [DI](dependency-injection.md)"

If a plan task is nothing but boilerplate that a doc or skill already covers, remove the code block entirely and leave only the intent (e.g., "Add `ForAccount` criteria to `TransactionRepository` following the `ForCategory` pattern").

**Every new file names an existing-file analog as its structural template** — e.g., "model after `CategoryEntity`", "follow `RoomCategoryRepository.kt`", "use `accountNavigationEntry` as the structural reference". The analog covers structure *and* surface patterns (string handling, visibility, naming, lint conformance), not only shape. Without an analog, the executor re-derives conventions from AGENTS.md per task and re-litigates choices the plan-writer already made.

## Multi-PR features — resolve cross-cutting decisions in a roadmap, not in phase plans

**When a feature ships across multiple PRs, write a roadmap doc first that pins every cross-cutting decision** (schema flexibility, cadence shape, reuse vs. new aggregation, audit-log vs. none, etc.). Each phase plan then only mechanizes the roadmap and never re-litigates architecture. Phase plans must reference the roadmap by link, not restate its decisions — restating them invites silent drift between phases. See `docs/superpowers/plans/2026-05-13-budget-roadmap.md` as the canonical example.

## brainstorming — Fetch design before designing

**Use `fetch-design` skill before the brainstorming "Explore project context" step** when the user provides a Claude Design URL. The design file is the layout spec; prose descriptions ("card at top, list below") are ambiguous and produce avoidable back-and-forth.

**Explore context in this order** (avoids cold-start over-reading):
1. AGENTS.md — already in context
2. `docs/agents/architecture.md` — Component/ViewModel patterns
3. The nearest module AGENTS.md for the feature area
4. The most similar existing screen as implementation reference (e.g., `CategoryDetailViewProvider` for new detail screens)

**A brief that names a new type to add (`add a FooUseCase`) is a candidate, not a directive** — first find the use case that already *produces* the underlying data and add a focused method there. A boolean/aggregate that's a pure projection of a query's rows belongs on that query use case, not on a new type or the orchestrator that happens to already compute it. Planning straight to the brief's wording cost three correction rounds in Budget Phase 7 (PR #268).

## Design docs — What to omit

The brainstorming skill writes a spec with architecture, components, and data-flow sections. For this project, cut:

- Any section that restates Component/ViewModel/ViewProvider — it's in AGENTS.md; the spec only needs to name the new types
- DI wiring details — those belong in the implementation plan, not the spec
- Module placement — obvious from the module map in AGENTS.md
