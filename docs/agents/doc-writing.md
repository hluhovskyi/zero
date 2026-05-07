# Agent Documentation Guide

Rules for adding or editing anything in `AGENTS.md`, `docs/agents/`, or skill files.

## Format

Every rule: `**<trigger or imperative>** — <why or consequence>`. One line.

Code snippets only when the call pattern IS the rule — i.e., without the snippet the rule is ambiguous. Never use snippets to illustrate or teach.

## What to write

Write only what a future session wouldn't discover by reading the code:
- **Traps**: APIs that look right but silently fail (`adb exec-out`, `DispatcherProvider.main()` vs `.main`)
- **Hidden constraints**: architectural decisions with non-obvious reasoning
- **Mandatory steps that agents skip**: verification loops, ordering requirements
- **The "why"** behind choices that look arbitrary

**Skip**: anything the code already says. One-off fixes. Symptoms (document the failure mode instead — the fix belongs in code).

## Where

| Content | File |
|---|---|
| Module-specific rule | `<module>/AGENTS.md` |
| Cross-cutting pattern | `docs/agents/<topic>.md` + link from root `AGENTS.md` |
| Project-wide invariant | Root `AGENTS.md` Cross-Cutting Rules |

## Self-check before committing

1. **Reach test** — "Would I actually read this file at the moment I need this rule?" If not, move it or drop it.
2. **Root-cause test** — "Does this rule address the class of failure, or one specific instance?" Patch the failure mode.
3. **Format check** — Does each rule fit in one bold phrase + one sentence? If not, split it.
4. **Noise check** — Would removing this rule confuse a future session? If no, don't write it.
