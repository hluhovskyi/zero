---
name: agent-poll
description: >
  Watcher for the issue-driven agent. Run via `/loop 10m /agent-poll` in a dedicated terminal.
  Each tick: queries GitHub for issues with the `agent-approved` label authored and labeled by
  hluhovskyi, picks the oldest, and spawns an isolated `claude -p` session to handle it. One
  issue per tick (strictly serial). Reports outcome and exits.
---

# /agent-poll

Lightweight orchestrator. The real logic lives in `scripts/agent/poll-issues.sh`.

## What this skill does

1. Runs `scripts/agent/poll-issues.sh` from the repo root
2. Reports the outcome to the user
3. Exits

That's all. Do not read source code, do not modify files, do not enter brainstorming.
Your job is to invoke the script and surface its output. The script handles author/actor
verification, label state transitions, the spawn, and the issue comment.

## How to run

```bash
bash scripts/agent/poll-issues.sh
```

Read the output. Report one of:
- "no eligible issues — sleeping until next tick"
- "issue #<N>: <final-label>"

Then exit. The `/loop` harness will fire this skill again at the next interval.

## What this skill MUST NOT do

- Use `Edit` or `Write` (this session is a watcher, not an executor)
- Spawn subagents (the per-issue session is spawned by `poll-issues.sh` via `claude -p`)
- Modify GitHub labels directly (the polling script owns that state machine)
- Read the issue body (only the spawned session reads it; the watcher's context stays minimal)
