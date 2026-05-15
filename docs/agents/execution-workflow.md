# Execution Workflow

Plans are saved to `docs/superpowers/plans/` and serve as the contract between sessions.

## Design-First for Screen Layout

**Fetch and read the design file before writing any layout code.** Text descriptions are ambiguous — "grid", "cards", "list" mean different things in different contexts. If a design URL or file is not provided, ask for it before starting implementation. Never infer column count, spacing, or component structure from prose alone.

## UI Verification — Mandatory Loop

**After every UI change, verify before claiming it works.** No exceptions.

```bash
./scripts/install-app.sh && ./scripts/ui/open-screen.sh <screen>
```

`open-screen.sh` launches the app, navigates to the target screen, prints a readable hierarchy summary, and saves `/tmp/screen.png`. Read the screenshot with your image tool. If the expected composable is missing or has zero-size bounds, the fix did not work — do not report success.

Pass `--raw` to `dump-ui.sh` when you need full XML attribute detail.

## Complexity Circuit Breaker

**The "Hacky Code" Circuit Breaker:** If you iterate on a fix more than twice and your solution requires dropping down to low-level framework APIs (e.g., `PointerEventPass.Initial`, Reflection, or Global Registries) for a common UI or logic problem, you must STOP. Revert your changes and present the fundamental constraint to the user before proceeding. Do not brute-force the framework.
