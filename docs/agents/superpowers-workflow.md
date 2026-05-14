# Superpowers Workflow — Project-Specific Optimizations

Read this before invoking `writing-plans` or `brainstorming`. These rules override the superpowers defaults for this project.

## Design-to-PR flows — split into two sessions

**For any task that involves fetch-design + plan + implementation: use two sessions.** Session 1: fetch design, write plan, commit. Session 2: execute plan (starts fresh; design HTML and chat logs never enter the implementation context). Running all three in one session carries the full design archive through implementation and typically forces a mid-session compaction.

## writing-plans — Keep plans under 400 lines

The `writing-plans` skill generates full code blocks by default. In this project, that produces ~2000-line plans that consume the entire context window during execution. Shorten plans by replacing repeated patterns with references:

**Replace with a skill call:**
- Any Component/ViewModel/ViewProvider/Handler scaffolding → "Run `scaffold-feature` for `<Name>`" (generates all four files from a stub)

**Replace with a doc reference:**
- Navigation wiring → "Wire per [Navigation](docs/agents/navigation.md)"
- Room DAO + criteria pattern → "Add criteria + query per [Data Layer](docs/agents/data-layer.md)"
- Dagger component structure → "See [DI](docs/agents/dependency-injection.md)"

If a plan task is nothing but boilerplate that a doc or skill already covers, remove the code block entirely and leave only the intent (e.g., "Add `ForAccount` criteria to `TransactionRepository` following the `ForCategory` pattern").

**Every new file names an existing-file analog as its structural template** — e.g., "model after `CategoryEntity`", "follow `RoomCategoryRepository.kt`", "use `accountNavigationEntry` as the structural reference". The analog covers structure *and* surface patterns (string handling, visibility, naming, lint conformance), not only shape. Without an analog, the executor re-derives conventions from AGENTS.md per task and re-litigates choices the plan-writer already made.

## brainstorming — Fetch design before designing

**Use `fetch-design` skill before the brainstorming "Explore project context" step** when the user provides a Claude Design URL. The design file is the layout spec; prose descriptions ("card at top, list below") are ambiguous and produce avoidable back-and-forth.

**Explore context in this order** (avoids cold-start over-reading):
1. AGENTS.md — already in context
2. `docs/agents/architecture.md` — Component/ViewModel patterns
3. The nearest module AGENTS.md for the feature area
4. The most similar existing screen as implementation reference (e.g., `CategoryDetailViewProvider` for new detail screens)

## Design docs — What to omit

The brainstorming skill writes a spec with architecture, components, and data-flow sections. For this project, cut:

- Any section that restates Component/ViewModel/ViewProvider — it's in AGENTS.md; the spec only needs to name the new types
- DI wiring details — those belong in the implementation plan, not the spec
- Module placement — obvious from the module map in AGENTS.md
