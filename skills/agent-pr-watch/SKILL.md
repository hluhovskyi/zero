---
name: agent-pr-watch
description: >
  Watcher for the issue-driven agent's draft PRs. Run via `/loop 3m /agent-pr-watch`
  in a dedicated terminal. Each tick: queries GitHub for draft PRs on head:issue-*
  authored by hluhovskyi that already carry an approval signal (agent-merge
  label or APPROVED review), picks the oldest, dispatches ONE action (rebase,
  fix, verify, or merge), and exits. Strictly serial. The watcher is invisible
  to unapproved PRs.
---

# /agent-pr-watch

Lightweight orchestrator. All real logic lives in `scripts/agent/watch-prs.sh`.

## What this skill does

1. Runs `bash scripts/agent/watch-prs.sh` from the repo root.
2. Reports the outcome to the user.
3. Exits.

That's all. No code reads, no edits, no brainstorming. The script handles:

- Filtering by approval signal (single-gate model)
- Classifying state (behind-clean, behind-dirty, ci-failing, needs-verify, ready-to-merge, stale)
- Dispatching to spawn-*.sh sub-sessions for everything that needs reasoning
- Tracking attempts / failures, applying agent-blocked or agent-stale labels
- Cleaning up worktrees + branches for merged PRs

## How to run

```bash
bash scripts/agent/watch-prs.sh
```

Read the output. Typical lines:

- `no approved PRs to handle`
- `considering PR #<N> (issue-<N>)` followed by `state: <name>` and an outcome
- `→ agent-blocked: <reason>` (after 3 consecutive failures)
- `→ agent-stale` (branch too old to safely rebase)

Then exit. The `/loop` harness will fire this skill again at the next interval.

## What this skill MUST NOT do

- Use `Edit`, `Write`, or any tool that mutates files (this session is a thin watcher; all writes happen inside the spawned `claude -p` sub-sessions, where the sandbox flags apply).
- Spawn subagents via the `Agent` tool (the per-PR sub-sessions are spawned as separate `claude -p` processes by `watch-prs.sh`, not by the Agent tool).
- Touch labels, comments, or PR state directly (the script owns that — keeps the state machine in one place).
- Read PR or issue bodies (the watcher session's context stays minimal; sub-sessions get the data they need on the way in).

## Parallel workers

Set `ANDROID_SERIAL` in the terminal's environment to pin this watcher to a
specific emulator, e.g.:

```
ANDROID_SERIAL=emulator-5556 /loop 3m /agent-pr-watch
```

You can run `/agent-poll` and `/agent-pr-watch` (or two of either) in parallel
terminals without flock contention.
