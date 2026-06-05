# Execution Workflow

Plans are saved to `docs/superpowers/plans/` and serve as the contract between sessions.

## Design-First for Screen Layout

**Fetch and read the design file before writing any layout code.** Text descriptions are ambiguous — "grid", "cards", "list" mean different things in different contexts. If a design URL or file is not provided, ask for it before starting implementation. Never infer column count, spacing, or component structure from prose alone.

**Before building a helper component named in a `.jsx` design file, grep the parent screen for its instantiation (`<ComponentName`).** Designers leave earlier iterations as dead `const ComponentName = …` definitions that are never rendered — the design file (what the parent actually renders), not the plan, is authoritative. Building one cost ~900 LOC of component + destination + handler plumbing that had to be removed.

**A design's authoritative spec is the exploration face marked ★PREFERRED / CHOSEN, not `index.html`** — `index.html` inlines a *simplified placeholder*, so building from it produces the wrong layout. Read the chosen `.jsx`.

## Interaction Conventions

**Don't put a destructive action behind a hidden long-press.** A gesture with no visual hint that jumps straight to delete/remove is undiscoverable — surface it where the user already manages the item (e.g. the edit sheet that opens on tap) as a visible affordance, keep the confirm step. Raise this even when a plan or design specifies long-press.

## UI Verification — Mandatory Loop

**After every UI change, verify before claiming it works.** No exceptions.

```bash
./scripts/install-app.sh && ./scripts/ui/open-screen.sh <screen>
```

`open-screen.sh` launches the app, navigates to the target screen, prints a readable hierarchy summary, and saves `/tmp/screen.png`. Read the screenshot with your image tool. If the expected composable is missing or has zero-size bounds, the fix did not work — do not report success.

Pass `--raw` to `dump-ui.sh` when you need full XML attribute detail.

## Complexity Circuit Breaker

**The "Hacky Code" Circuit Breaker:** If you iterate on a fix more than twice and your solution requires dropping down to low-level framework APIs (e.g., `PointerEventPass.Initial`, Reflection, or Global Registries) for a common UI or logic problem, you must STOP. Revert your changes and present the fundamental constraint to the user before proceeding. Do not brute-force the framework.

## Diagnose, Don't Retry

**After an action fails twice, the third attempt is a state query, not a re-run with tweaks.** Stop varying the command and ask the environment *why* it failed — a misleading error usually points at the wrong cause (e.g. a fresh emulator's "No activities found" is a locked device, not a bad build, so the fix is unlock, not rebuild).
