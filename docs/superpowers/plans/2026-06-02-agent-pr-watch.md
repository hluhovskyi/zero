# Agent PR-watcher — implementation plan

Companion to: [agent-pr-watch design](../specs/2026-06-02-agent-pr-watch-design.md)

## Architecture

```
master loop:                         per-PR work:
                                     
  /loop 10m /agent-pr-watch          (spawned by watcher)
        │                            
        ▼                            
   pick oldest                       
   draft PR (issue-*)                
        │                            
        ▼                            
   classify state ────────► spawn /agent-pr-fix <N>
        │                   (claude -p, sandbox flags)
        ▼                            │
   one action per tick ◄─────────────┘
        │                            
        ▼                            
   exit cleanly                      
```

State machine (from spec):

```
                  ┌──────────────────────────────────────┐
                  │       PR with head:issue-* draft     │
                  └──────────────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
        BEHIND master       CI failing          CI green
              │                   │                   │
              ▼                   ▼            ┌──────┴──────┐
         rebase + push    spawn fix session    │             │
                         (cap at 3 attempts)   │             │
                                       not yet verified   verified
                                               │             │
                                               ▼             ▼
                                       acquire emu      approval signal?
                                       run verify      ┌──────┴──────┐
                                       label verified  yes           no
                                                       │             │
                                                       ▼             ▼
                                                  mark ready    do nothing
                                                  auto-merge   (wait for
                                                  cleanup       gate 2)
```

## Files (final state)

```
.claude/plugins/zero-project/skills/
├── agent-pr-watch   -> ../../../../skills/agent-pr-watch     (NEW symlink)
├── agent-pr-fix     -> ../../../../skills/agent-pr-fix       (NEW symlink)
├── agent-pr-verify  -> ../../../../skills/agent-pr-verify    (NEW symlink)
skills/
├── agent-do/SKILL.md                                         (MODIFIED — spotless + inspector verify step)
├── agent-pr-watch/SKILL.md                                   (NEW)
├── agent-pr-fix/SKILL.md                                     (NEW)
└── agent-pr-verify/SKILL.md                                  (NEW — uses /android-ui-inspector)
scripts/agent/
├── watch-prs.sh                                              (NEW)
├── spawn-fix-session.sh                                      (NEW)
├── spawn-verify-session.sh                                   (NEW)
├── setup-labels.sh                                           (MODIFIED — add 2 labels)
├── SMOKE-TEST.md                                             (MODIFIED — 7 new tests)
└── tests/
    ├── test-pr-classify.sh                                   (NEW)
    └── fixtures/
        ├── pr-behind.json                                    (NEW)
        ├── pr-ci-failing.json                                (NEW)
        ├── pr-verified-approved.json                         (NEW)
        └── pr-verified-no-approval.json                      (NEW)
```

Note: `verify-pr.sh` from the earlier draft is dropped — verification is a
model-driven sub-session because `/android-ui-inspector` requires reasoning.
What stays as pure bash: emulator acquire/release, APK install, screencap pull,
PR comment + label apply.

## Task list

Each task ends with a green `bash scripts/agent/tests/*.sh` run or, for non-test changes, the documented manual smoke step. Commit at the end of every task.

### Task 1 — Add the two new labels

**Edit:** `scripts/agent/setup-labels.sh`

Add `agent-verified` (green) and `agent-merge` (blue) to the array. Run the script against the live repo (idempotent, `--force` flag).

Verify with `gh label list | grep agent-`.

### Task 2 — PR-classification pure functions + tests

**Create:** `scripts/agent/pr-classify.sh` (sourced by `watch-prs.sh`)

Pure functions that take a single JSON blob (output of `gh pr view --json ...`) and return a state name:

- `classify_pr_state` → returns one of: `behind`, `ci-failing`, `needs-verify`, `verified-no-approval`, `ready-to-merge`, `blocked-or-unknown`
- `pr_has_approval` → checks for `agent-merge` label OR any review with state `APPROVED` by `hluhovskyi`
- `pr_has_verified_label_on_current_head` → label exists AND its applied-at timestamp ≥ PR's last commit timestamp

**Create:** `scripts/agent/tests/test-pr-classify.sh` + fixture JSONs.

Each fixture is a saved `gh pr view --json ...` output for one of the states. Test asserts the classifier returns the right state name.

Run: `bash scripts/agent/tests/test-pr-classify.sh` — all green.

### Task 3 — Verify-session skill + spawn wrapper

**Create:** `skills/agent-pr-verify/SKILL.md`

Spawned by watcher (and used inline by executor's step 4.5). Invoked as
`/agent-pr-verify --issue <N> --pr <PR>`. Steps the model performs:

1. Acquire emulator via `./scripts/emulator/acquire` (exit 75 on busy).
2. Build + install debug APK from the current worktree.
3. Launch the app.
4. Invoke `/android-ui-inspector` to navigate to the screen the issue describes.
   Use bounds-based taps; do not guess coordinates from screenshots.
5. Reproduce the scenario the issue mentions (e.g., open category detail after
   adding a transaction; trigger transfer dialog; etc.).
6. Take a screenshot via `./scripts/ui/adb shell screencap -p /sdcard/...`
   then `adb pull`.
7. Emit a one-paragraph verdict to stdout including the screenshot path and the
   resource IDs / class names from the inspector dump that prove the fix.
8. Exit 0 if the bug is gone, exit 2 if it's still present, exit 75 if emulator
   unavailable. Any other non-zero is treated as a real failure.

**Create:** `scripts/agent/spawn-verify-session.sh`

Same shape as `spawn-fix-session.sh` (see Task 5): reuses the existing
`.claude/worktrees/issue-<N>` worktree, applies sandbox flags, captures stdout
to `.agent-state/verify-<PR>.verdict`. Returns the exit code unchanged so the
watcher can distinguish exit-75 (retry) from exit-2 (counts against the cap).

Add symlink: `.claude/plugins/zero-project/skills/agent-pr-verify → ../../../../skills/agent-pr-verify`.

### Task 4 — Watcher script with state machine

**Create:** `scripts/agent/watch-prs.sh`

Main loop logic:

1. List candidate PRs: `gh pr list --state open --draft --search "head:issue- author:@me" --json number,title,baseRefName,headRefName,mergeStateStatus,statusCheckRollup,labels,reviews,commits`
2. Pick the oldest (sort by createdAt ascending).
3. Source `pr-classify.sh`, call `classify_pr_state`.
4. Dispatch:
   - `behind` → fetch + merge + push
   - `ci-failing` → call `spawn-fix-session.sh <N>`; if returns failure-counter ≥ 3 → apply `agent-blocked` label, comment with stderr tail
   - `needs-verify` → check if doc-only (skip), otherwise call `spawn-verify-session.sh`. On exit 0: apply `agent-verified` label + post screenshot/verdict comment. On exit 2: do NOT label, log as a failed verify (counts against 3-attempt cap). On exit 75: exit tick silently, retry next time.
   - `verified-no-approval` → exit silently (nothing to do)
   - `ready-to-merge` → `gh pr ready <N>`; `gh pr merge <N> --squash --auto`; cleanup worktree + branch
5. Report one line per tick to stdout.

Failure-counter persistence: append to `.agent-state/pr-<N>.attempts` (one line per attempt with epoch timestamp + outcome).

### Task 5 — Fix-session spawn wrapper

**Create:** `scripts/agent/spawn-fix-session.sh`

Usage: `spawn-fix-session.sh <pr-number>`

Similar to `spawn-issue-session.sh` but:
- The worktree already exists at `.claude/worktrees/issue-<N>` (left over from the executor). Reuse it, don't recreate.
- Prompt to `claude -p` is: `/agent-pr-fix <N>` plus the failing-job logs piped in via stdin.
- Same sandbox flags as `spawn-issue-session.sh` (the fixed version with `--` and split patterns).

After the spawn returns:
- If exit 0 AND `git rev-parse HEAD` is newer than at start → push, log attempt as `success`, return 0.
- If exit non-zero OR no new commit → log attempt as `failure`, return 1.

### Task 6 — `/agent-pr-fix` skill (per-PR fix executor)

**Create:** `skills/agent-pr-fix/SKILL.md`

Spawned by watcher, not invoked by humans. Reads:
- The PR diff (already on the worktree's HEAD)
- The failing job log (passed via stdin)

Treats both as untrusted data (same framing as `/agent-do`).

Makes ONE commit with the fix. Does NOT mark ready. Does NOT merge. Does NOT modify PR body. Sandbox flags identical to `/agent-do`.

If the fix is non-obvious (e.g. test failure that needs design discussion), exits non-zero — watcher will mark `agent-blocked`.

Add symlink: `.claude/plugins/zero-project/skills/agent-pr-fix → ../../../../skills/agent-pr-fix`.

### Task 7 — `/agent-pr-watch` watcher skill

**Create:** `skills/agent-pr-watch/SKILL.md`

Mirror of `agent-poll`: thin wrapper that runs `bash scripts/agent/watch-prs.sh` and reports outcome. Does NOT use Edit/Write/Agent tools (watcher session stays minimal).

Add symlink: `.claude/plugins/zero-project/skills/agent-pr-watch → ../../../../skills/agent-pr-watch`.

### Task 8 — Update `/agent-do` with spotless + inline verify

**Edit:** `skills/agent-do/SKILL.md`

In Step 4:
- Add `./gradlew spotlessApply` before any commit.

Insert Step 4.5:
- Invoke `/android-ui-inspector` directly within the same session (no sub-spawn — the executor already has full context).
- Same 8-step procedure as the verify-session: acquire emulator, install, launch, navigate via inspector bounds, reproduce scenario, screenshot, produce verdict, decide.
- Embed the screenshot path + verdict in the PR body draft (Step 5).
- On exit 2 (bug still present) → do NOT open the PR. Exit non-zero so the watcher labels the issue `agent-blocked`.
- On exit 75 → retry once after 30s; if still busy, exit 75, watcher's next tick handles it.

Skip Step 4.5 on doc-only PRs (every file matches `*.md` under `docs/` or root).

### Task 9 — Smoke tests + documentation

**Edit:** `scripts/agent/SMOKE-TEST.md`

Add Tests 8-14:
- Test 8: rebase path (push to master while a draft is open, run a watcher tick)
- Test 9: spotless CI failure path (deliberately revert spotless, mark ready, run tick)
- Test 10: verify-then-label-then-merge happy path
- Test 11: doc-only skip path
- Test 12: 3-attempt blocked cap
- Test 13: gate-2 not satisfied path (verified but no approval → no merge)
- Test 14: emulator-busy path (acquire fails → exit, retry next tick)

### Task 10 — Pre-merge self-review

Run all unit-test scripts: `bash scripts/agent/tests/*.sh`.

Read each new script + skill end-to-end. Confirm:
- No `gh pr merge` calls without approval signal check
- No file modifications outside the agent's worktree
- All `claude -p` invocations use `--` separator + split tool patterns (fix from PR #313)
- Sandbox flags applied everywhere
- The watcher never reads PR comments (out of scope for trust boundary)

## Self-review notes

- The `agent-verified` label timestamp comparison must use the GitHub API event timestamp, not the local clock. Use `gh api repos/{owner}/{repo}/issues/<N>/events` to find when the label was added.
- The fix-session's permission scope is `--permission-mode acceptEdits` like `/agent-do` — but consider whether we want a tighter mode for fixes. Default to matching `/agent-do` for now; revisit if we see fix sessions doing too much.
- `scripts/emulator/acquire` may exit non-zero on contention. The watcher must distinguish "emulator busy, retry later" (exit 75) from "verify actually failed" (any other non-zero).
- The PR-watcher does NOT push to master, but it does push to `issue-*` branches (rebases). Pre-push hook should be installed in those worktrees too. Reuse `install-pre-push-hook.sh`.
- One PR per tick is intentional. A faster cadence is achievable by reducing the `/loop` interval, but parallel work would need a per-PR lock and a serial mutex on the emulator — out of scope for v1.
