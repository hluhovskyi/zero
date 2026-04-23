---
name: retro
description: >
  Run a retrospective on a development session or feature branch. Use this when the user asks
  "what went well / what didn't", "document learnings", "anything worth documenting?",
  "update the docs", or at natural wrap-up points (feature complete, PR ready, branch done).
  Investigates history + codebase + existing docs to surface what caused extra iterations,
  what was discovered late, and what's worth writing down so the next session starts smarter.
---

# Retro

Surface what caused friction this session and decide what's worth documenting so it doesn't happen again. This skill is generic and applicable whether you are operating as Claude Code, Gemini CLI, or Copilot CLI.

## Step 1 — Establish scope

Determine what to review:
- If on a feature branch: `git log master..HEAD --oneline` to see all commits.
- If the user named specific files or a topic: focus there.
- If ambiguous: ask one question — "What area should I focus on?"

## Step 2 — Investigate

Run these in parallel:

**History** — `git log --oneline` + `git diff master...HEAD --stat` to understand what changed and how many iterations it took (lots of small fixup commits = friction).

**Affected AGENTS.md files** — read the AGENTS.md nearest to each changed package. These are the ground truth for what's documented vs. not.

**Relevant docs** — scan `docs/agents/` for any doc that covers the affected area (navigation, architecture, DI, etc.).

Look for signals of friction:
- **Success Chasing:** Multiple instances where you claimed "Fixed" only to have the user report it still fails.
- **Approach Revision:** Multiple commits touching the same file with "fix", "revert", or "again".
- **User Course-Correction:** Patterns the user corrected or tools they suggested mid-session.
- **Blind Operation:** Commits made without running a tool that could actually verify the behavioral change (e.g. fixing UI without dumping hierarchy).

## Step 3 — Analyze (Internal Questions & 5 Whys)

Answer these questions internally before writing anything:

1. **The Pivot Point:** What specific information (user hint, new tool, library search) caused the final successful pivot? Why wasn't this found in Turn 1?
2. **Success Chasing Audit:** Did you claim a fix was complete before running a tool that could actually prove it? If yes, identifying the mandatory verification tool is your priority.
3. **Assumption Traps:** Did you stay within the "existing code's box" too long? Did a dependency bump or architectural change solve a problem you were trying to "hack" your way through?
4. **The Short-Circuit Rule:** What single rule or pointer would have reduced the turn count to 1?

Perform a root-cause analysis on the session friction. You MUST answer "Why?" five times for any major pivot or failure. Do not settle for "I made a mistake" summaries.

1. **Why** did the friction or failure occur?
2. **Why** did the initial research or strategy fail to account for it?
3. **Why** was the erroneous assumption made?
4. **Why** did the current process/tools allow the assumption to persist?
5. **Why** did the environment (docs, linters, types) not prevent the error?

**Goal:** Identify why the process or environment allowed the failure to happen.

## Step 4 — Propose (Actionable Safeguards)

Present findings concisely. A retrospective is ONLY complete if it results in an actionable documentation update or a programmatic safeguard (e.g., a lint rule) that makes the error impossible to repeat.

**Before listing any item, apply this filter:** "Would I actually read this file, at the moment I need it, in a future session?" If not, cut it. One sharp item beats three diluted ones. Zero items is a valid outcome if nothing passes the filter.

```
## Final Achieved Architecture
- [one-sentence summary of the final solution]

## The "One Rule" to skip iterations
- [the most impactful rule derived from the 5 Whys]

## What's worth documenting
- [insight] → suggest: add to [file] under [section]
- [safeguard] → suggest: implement linter/static check for [rule]
```

Keep it short. No padding. Then ask: **"Which of these would you like me to write?"** — one item is fine; the user picks, not you.

## Step 5 — Write (if confirmed)

For each doc update:
- Prefer editing existing files over creating new ones.
- Cast each rule in the format used throughout this codebase's AGENTS.md files: `**<imperative or trigger condition>** — <why or consequence>`. If the imperative part doesn't fit in a short bold phrase, it's two rules — split it.
- **Self-check before committing:** does each new rule fit in one bold phrase + one sentence? If not, compress or split. No tutorial prose, no code examples unless a snippet is the only way to convey the rule.
- Commit with message: `docs: <what was added and why>`

## Guardrails

- Don't document things the code already says clearly — only the "why" and the traps.
- If the friction was a one-off or the fix is already in the code, skip it.
- Prioritize rules that prevent "operating blind" or "success chasing".
