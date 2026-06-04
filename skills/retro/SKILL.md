---
name: retro
description: Run a retrospective on the current feature branch or session. Use at wrap-up points or when the user asks about learnings, docs, or "what went well".
---

# Retro

Surface what caused friction this session and decide what's worth documenting so it doesn't happen again. This skill is generic and applicable whether you are operating as Claude Code, Gemini CLI, or Copilot CLI.

## Step 1 — Establish scope

Determine what to review:
- If the user passed a PR number — use it.
- If on a feature branch — infer PR: `gh pr list --head <branch> --state all --limit 1 --json number,title`
- If on master (post-merge) — find the most recent merged PR: `gh pr list --state merged --limit 1 --json number,headRefName,title`
- If the user named specific files or a topic — focus there.
- If still ambiguous — ask: "What area should I focus on?"

## Step 2 — Investigate

Run these in parallel:

**History** — use the PR (works after squash-merge cleanup):
```bash
gh pr view <pr_number> --json commits --jq '.commits[].messageHeadline'
gh pr view <pr_number> --json files --jq '.files[].path'
```
Count commits; "fix"/"revert"/"again" patterns = friction. If still on the feature branch, also run `git diff master...HEAD --stat`.

**Affected AGENTS.md files** — read the AGENTS.md nearest to each changed package. These are the ground truth for what's documented vs. not.

**Relevant docs** — scan `docs/agents/` for any doc that covers the affected area (navigation, architecture, DI, etc.).

**For doc/skill/process-heavy sessions, the diff won't show the friction.** The signals above (commit churn, `fix`/`revert`, AGENTS.md near changed packages) assume a code PR. When the work is authoring docs/skills or running a multi-step process, the friction lives in the **conversation** — trace the *user's course-corrections on scope or structure* ("don't focus on that", "we don't need a separate doc", "generalize it"), not the commits.

Look for signals of friction:
- **Success Chasing:** Multiple instances where you claimed "Fixed" only to have the user report it still fails.
- **Approach Revision:** Multiple commits touching the same file with "fix", "revert", or "again".
- **User Course-Correction:** Patterns the user corrected or tools they suggested mid-session.
- **Blind Operation:** Commits made without running a tool that could actually verify the behavioral change (e.g. fixing UI without dumping hierarchy).
- **Iteration Cost:** Hypothesis-fix-test cycles >2 on the same layer, repeated reads of the same file, polling background tasks instead of awaiting notifications — token/speed signals even if no commit lands.

## Step 3 — Analyze

Answer these questions internally before writing anything:

1. **The Pivot Point:** What specific information (user hint, new tool, library search) caused the final successful pivot? Why wasn't this found in Turn 1?
2. **Success Chasing Audit:** Did you claim a fix was complete before running a tool that could actually prove it? If yes, identifying the mandatory verification tool is your priority.
3. **Assumption Traps:** Did you stay within the "existing code's box" too long? Did a dependency bump or architectural change solve a problem you were trying to "hack" your way through?
4. **The Short-Circuit Rule:** What single rule or pointer would have reduced the turn count to 1?

**Trace any major pivot to its earliest detectable signal** — what one thing, if known at turn 1, would have made it a one-shot?

## Step 4 — Propose (Actionable Items)

**Empty is valid — prefer empty over invented.** A retrospective is complete when honest action items are surfaced across the four axes below, or when none survive the filters.

**Before listing any item, run both filters. Cut anything that fails either one.**

**Filter 1 — Root cause test:** Does this address the underlying failure mode, or does it patch one specific instance of it? A rule that says "component X has dimension Y" patches a symptom — the wrong value was used. The root cause is the process failure that allowed it to go undetected (e.g., not running a visual verification). Address the process failure, not the specific wrong value. Ask: "If this exact component were refactored tomorrow, would the rule still prevent the same class of mistake?"

**Filter 2 — Reach test:** "Would I actually use this, at the moment I need it, in a future session?" If not, cut it. One sharp item beats three diluted ones.

```
## Final Achieved Architecture
- [one-sentence summary of the final solution]

## The "One Rule" to skip iterations
- [the most impactful single rule, or "—" if none]

## Actionable items (apply filters to each; empty axes are valid)
- **Docs** — [insight → file/section]
- **Repo/infra** — [one-time setting or script tweak]
- **Speed/token** — [recurring per-session win — Claude behavior or process]
- **Behavior (no artifact)** — [self-correction; not committed anywhere]
```

Surface speed/token wins by default — they're often the biggest payback per session and easiest to overlook because they don't produce artifacts. Tool-call count, build-cycle count, and retry loops are the data points.

Keep it short. No padding. Then ask: **"Which of these would you like me to write?"** — one item is fine, zero is fine; the user picks, not you.

## Step 5 — Write (if confirmed)

**Before editing any file**, verify you are in a worktree:
```bash
./scripts/detect-worktree.sh
```
If `IS_WORKTREE=no`, create one first using `superpowers:using-git-worktrees` with a branch name like `docs/retro-YYYY-MM-DD`. Do not commit from the main workspace — the pre-commit hook will block it.

For each doc update:
- Prefer editing existing files over creating new ones.
- Cast each rule in the format used throughout this codebase's AGENTS.md files: `**<imperative or trigger condition>** — <why or consequence>`. If the imperative part doesn't fit in a short bold phrase, it's two rules — split it.
- **Self-check before committing:** does each new rule fit in one bold phrase + one sentence? If not, compress or split. No tutorial prose, no code examples unless a snippet is the only way to convey the rule.
- Commit with message: `docs: <what was added and why>`

## Follow-up — Skill self-check (after main retro is complete)

Run **only after** the main retro has presented action items and the user has chosen what to write (or declined all). This is a separate pass; do not interleave it with Steps 1–5.

Gut-check:
- Did the user redirect framing or ask for an axis the skill didn't surface?
- Did the skill bias toward one output type?
- Did any rule fire without value this session?

If yes to any: propose **at most one** edit to `skills/retro/SKILL.md`, apply the same two filters, write only if it survives. Otherwise post: `Skill self-check: no edit.`

Cap: one edit per retro. Do not meta-retro this self-check.

## Guardrails

- Don't document things the code already says clearly — only the "why" and the traps.
- If the friction was a one-off or the fix is already in the code, skip it.
- Prioritize rules that prevent "operating blind" or "success chasing".
