---
name: retro
description: >
  Run a retrospective on a development session or feature branch. Use this when the user asks
  "what went well / what didn't", "document learnings", "anything worth documenting?",
  "update the docs", or at natural wrap-up points (feature complete, PR ready, branch done).
  Investigates git history + codebase + existing docs to surface what caused extra iterations,
  what was discovered late, and what's worth writing down so the next session starts smarter.
---

# Retro

Surface what caused friction this session and decide what's worth documenting so it doesn't happen again.

## Step 1 — Establish scope

Determine what to review:
- If on a feature branch: `git log master..HEAD --oneline` to see all commits
- If the user named specific files or a topic: focus there
- If ambiguous: ask one question — "What area should I focus on?"

## Step 2 — Investigate

Run these in parallel:

**Git history** — `git log --oneline` + `git diff master...HEAD --stat` to understand what changed and how many iterations it took (lots of small fixup commits = friction)

**Affected AGENTS.md files** — read the AGENTS.md nearest to each changed package. These are the ground truth for what's documented vs. not.

**Relevant docs** — scan `docs/agents/` for any doc that covers the affected area (navigation, architecture, DI, etc.)

Look for signals of friction:
- Multiple commits touching the same file (approach was revised)
- Commits with "fix", "revert", "rollback", "again" in the message
- Patterns the user corrected mid-session
- Things that were built wrong before the right example was found

## Step 3 — Analyze

Answer these questions internally before writing anything:

1. **What caused extra iterations?** Misread existing pattern, missing example, wrong abstraction direction, undocumented gotcha?
2. **What was discovered late that should have been found early?** Canonical example in the codebase, existing infrastructure, a constraint.
3. **What rule or pointer would have short-circuited the friction?** One sentence, ideally with a pointer to an existing file.
4. **Is this already documented somewhere?** If yes, was it discoverable? If no, where does it belong?

## Step 4 — Propose

Present findings concisely:

```
## What caused friction
- [thing] → [why it caused iterations]

## What's worth documenting
- [insight] → suggest: add to [file] under [section]

## What's already fine
- [thing that worked, no action needed]
```

Keep it short. One bullet per insight. No padding.

Then ask: **"Want me to write these updates?"**

## Step 5 — Write (if confirmed)

For each doc update:
- Prefer editing existing files over creating new ones
- Keep additions tight — pointer + rule, not a tutorial
- If creating a new AGENTS.md (feature-level doc), use this structure:
  ```
  # Feature Name
  ## Purpose
  ## Inputs / Outputs
  ## Key Flow
  ## Dependencies
  ## Integration (how other features interact with this)
  ## Known Issues (only if real and unresolved)
  ```
- Commit with message: `docs: <what was added and why>`

## Guardrails

- Don't document things the code already says clearly — only the "why" and the traps
- Don't document implementation details that will change — document patterns and rules
- If the friction was a one-off or the fix is already in the code, skip it
- Don't create new doc files for things that belong in an existing one
