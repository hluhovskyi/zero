# Agent PR-watcher design + executor verification

Status: design
Owner: hluhovskyi
Date: 2026-06-02
Builds on: [issue-driven agent design](2026-05-22-issue-driven-agent-design.md)

## Goal

Close the post-PR-open gap in the issue-driven agent loop. Today, after `/agent-do`
opens a draft PR, no automation moves it forward — CI failures sit, branches drift
behind master, and no one verifies the change actually fixes the issue on a running
emulator. The human gate is supposed to be "review and approve PR," not "babysit it
through CI and merge."

This spec adds two pieces:

1. **Executor verification** — `/agent-do` must demonstrate the fix works on the
   emulator before opening a PR, with screenshot evidence in the PR body. No more
   "compiles + tests pass = ship it."
2. **PR-watcher (`/agent-pr-watch`)** — a sibling watcher that babysits draft PRs:
   rebase, fix CI failures, re-verify on emulator, then mark ready and merge once
   approval signal arrives.

## Trust model (unchanged)

The two human gates remain:

- **Gate 1: issue approval** — you apply `agent-approved` to an issue.
- **Gate 2: PR approval** — you either submit an `APPROVED` review on the PR, OR
  apply an `agent-merge` label.

The PR-watcher acts on neither gate's behalf. It only:

- Acts *before* gate 2 → keeps the PR healthy (rebase, fix CI failures, verify).
- Acts *after* gate 2 → executes your decision (mark ready, auto-merge, clean up).

A PR without gate 2 signal is never merged, regardless of how green CI is.

## Why executor must verify

Three failures from the first 4 agent PRs surfaced the gap:

- #314 shipped a dark-mode color change that broke spotless (CI caught it; local checks didn't).
- Multiple PRs are now `BEHIND` master with no signal back to the loop.
- None of the PRs proved the fix actually works in the running app — only that the
  code compiled and unit tests passed.

"Compiles + tests pass" is the floor, not the bar. For a bug-fix agent to be
trustworthy, the executor must demonstrate the bug is gone in the running app.

## Executor verification (changes to `/agent-do`)

Insert a new step between **Step 4 (Execute)** and **Step 5 (Push and open PR)**:

### Step 4.5 — Verify on emulator

1. Acquire the emulator: `./scripts/emulator/acquire` (flock-safe per PR #223).
2. Install the debug APK built in step 4.
3. Launch the app.
4. Invoke the `/android-ui-inspector` skill to navigate to the screen the issue
   describes. The inspector is mandatory here, not optional: it dumps the real
   view hierarchy with bounds, so navigation is driven by actual coordinates of
   labeled elements instead of guessed taps. This is what makes verification
   reproducible — a screenshot alone can be misread by the model, but bounded
   element assertions cannot.
5. Take a screenshot via the `./scripts/ui/adb` wrapper.
6. **Reason about the screenshot + the inspector dump.** Compare what's on screen
   against what the issue body asked for:
   - "Dark mode renders icon poorly" → switch to dark mode via system settings,
     re-dump, inspect contrast of the named view bounds.
   - "Amount keypad overlaps system keyboard" → trigger the scenario, dump the
     hierarchy, assert the two views don't overlap by bounds.
   - "Category detail not updating after adding a transaction" → reproduce the
     flow, dump after each step, assert the relevant text node updates.
7. Embed the screenshot path and a 1-2 sentence verdict in the PR body. If the
   verdict references specific elements, include their resource IDs / class
   names from the inspector dump so a reviewer can re-check.

If verification fails (the bug is still present, or the change broke an unrelated
flow visible on the same screen), do NOT open a PR. Exit cleanly so the watcher
labels the issue `agent-blocked`.

If the issue is a doc-only change (every file in the diff matches `*.md` under
`docs/` or top-level READMEs), skip verification.

This step uses Bash + the inspector skill in the same session — not a
`claude -p` sub-spawn — because the executor session already has the issue body
and the code it just wrote.

### Also add to Step 4 — `./gradlew spotlessApply` before any commit.

The #314 failure is repeatable forever otherwise. Spotless violations don't
get caught by `testDebugUnitTest` or `lint` — they're a separate task that
both local and CI check.

## PR-watcher skill (`/agent-pr-watch`)

Mirror of `/agent-poll`. Lightweight wrapper; real logic lives in
`scripts/agent/watch-prs.sh`.

### Loop shape

```
/loop 3m /agent-pr-watch
```

Each tick: pick the oldest open draft PR on `head:issue-*` authored by you **that
already has an approval signal**, dispatch one action, exit. Strictly serial
(one PR per tick). The watcher is invisible to unapproved PRs.

### Single gate — watcher does not act on unapproved PRs

There is one human gate, not two:

- **Approval signal** = a `state: APPROVED` review on the PR by `hluhovskyi`
  **whose `commit_id` equals the current `headRefOid`** (stale reviews don't
  gate — protects against the watcher pushing new commits after approval), OR
  an `agent-merge` label applied by `hluhovskyi` **verified via the events API**
  (label *presence* alone is not trust — anyone with label-write could set it).

The review path evaluates the **latest** review per author (ordered by
`submitted_at`) — so submitting `REQUEST_CHANGES` after an earlier `APPROVED`
cleanly retracts the green light. Older `APPROVED` records persist in
GitHub's history but no longer gate the watcher.

The watcher's PR query filters by this signal. A PR without either is invisible
— no rebase, no CI fix, no verify, nothing. This means:

- You review draft PRs at your own pace.
- When you decide a PR is the right fix, you signal approval (review OR label).
- *Only then* does the watcher pick the PR up and drive it through the pipeline.
- Cost of being thorough is paid only on PRs you've already greenlit. No churn
  on PRs you'd close.

The two states you see in the PR list are:
- "draft, awaiting your call" (watcher invisible)
- "merged" (watcher done)

### State machine (post-approval)

For each approved candidate PR, classify and act:

| Condition | Action | Next-tick expectation |
|-----------|--------|----------------------|
| `BEHIND` clean | `git fetch && git merge origin/master && git push` (inline bash) | CI re-runs |
| `BEHIND` dirty (conflicts) | spawn `claude -p /agent-pr-rebase <N>` — LLM resolves conflicts, compile-checks, pushes | CI re-runs |
| CI failing | spawn `claude -p /agent-pr-fix <N>` with failing-job log in the prompt | one fix commit, push, CI re-runs |
| CI green + diff is not doc-only + verify needed | spawn `claude -p /agent-pr-verify <N>` — inspector + screenshot + bounded-element verdict, post screenshot/verdict as PR comment | proceed to merge |
| CI green + (verified OR doc-only) | `gh pr ready` → `gh pr merge --squash --auto` → cleanup | merged |
| Stale (age > 2d AND structural conflict) | `agent-stale` label, comment "branch too old to safely rebase — re-spawn from issue", stop | manual |
| 3+ consecutive fix/rebase/verify attempts failed | `agent-blocked` label, comment, stop | manual |

`agent-stale` (a separate age-based escape valve) was removed — the rebase
sub-session itself returns exit 2 when a conflict is structurally
unresolvable, which already lands in `agent-blocked`. One label is enough.

After any watcher-driven push (`behind-clean` rebase, `behind-dirty` rebase
success, `ci-failing` fix success) the watcher automatically removes the
`agent-merge` label and posts a comment asking the human to re-approve at the
new HEAD. The `commit_id`-bound review check covers the review path the same
way. Together these prevent an approval-then-rebase exploit where the watcher
would otherwise merge code the human never saw.

### Verify is an internal checkpoint, not a public state

Verification happens inside the post-approval pipeline; the result is gating data
for the merge, not a "ready for review" signal. The watcher tracks "verified at
HEAD X" in `.agent-state/pr-<N>.verified` (commit SHA + timestamp). If HEAD moves
past that SHA, re-verify. No `agent-verified` label is needed — the file is
ephemeral state, not a public signal.

### Verification policy: verify everything except doc-only

Skip verification only when every changed file is under `docs/` AND ends in
`.md` — genuine project prose. Notable explicit non-doc-only paths:

- Root `CLAUDE.md`, `AGENTS.md`, `README.md` (READMEs are prose, but
  `CLAUDE.md`/`AGENTS.md` are agent operating instructions — gated to be safe).
- Anything under `.claude/` even if `*.md`.
- `skills/**/*.md`, `scripts/**`, any `.kt`/`.gradle`/manifest/resource files.

Anything that affects runtime behavior of the app OR the dev loop gets verified.

Heuristics like "does this touch UI code?" are too easy to fool. Default to
verify; cost of a wasted emulator run is much smaller than cost of merging a
broken build.

### Parallel workers — one emulator per worker

Both watchers (`/agent-poll` and `/agent-pr-watch`) and the per-issue/per-PR
sub-sessions honor `ANDROID_SERIAL`:

- When set → the worker uses that exact emulator and skips flock-based acquire.
  No contention with other workers; safe to run in parallel.
- When unset → falls back to today's flock acquire on the default emulator.

Lets a user start, e.g.:

```
# Terminal 1 — picks up new issues
ANDROID_SERIAL=emulator-5554 /loop 10m /agent-poll

# Terminal 2 — babysits existing PRs
ANDROID_SERIAL=emulator-5556 /loop 10m /agent-pr-watch
```

…without coordination. The `scripts/ui/adb` wrapper already forwards
`ANDROID_SERIAL` to `adb`, so only `./scripts/emulator/acquire` needs a small
"if `ANDROID_SERIAL` is set, no-op and exit 0" branch.

The spawned sub-sessions (`/agent-do`, `/agent-pr-fix`, `/agent-pr-rebase`,
`/agent-pr-verify`) inherit the parent's environment, so the serial propagates
automatically.

Note: this doesn't multi-thread within a single watcher tick — each watcher
is still strictly serial (one issue/PR per tick). Parallelism comes from
running multiple watchers, each pinned to its own emulator.

### Emulator contention is not a kill license

When `./scripts/emulator/acquire` fails because all slots are full, the watcher
**exits the tick silently** and retries next time. It NEVER calls
`release --kill` on a sibling worktree's emulator — those belong to other
sessions and may be in active use. The script's stop-and-ask message is
correct; the watcher follows it.

Retries are silent — no comment threshold, no escalation. The user can see
the watcher is idle from its own output if they care; cluttering the PR with
"waiting for emulator" comments helps nobody.

### Verify session (`/agent-pr-verify <N>`)

Spawned as a `claude -p` process when the watcher needs to re-verify a PR. The
verify step uses `/android-ui-inspector` for bounded-element assertions, which
requires model reasoning — pure bash can't drive it. Prompt includes:

- The issue body (treated as untrusted data, same framing as `/agent-do`)
- The PR diff (so it knows what changed)
- The standing system prompt: "Acquire emulator, install + launch the debug APK,
  invoke `/android-ui-inspector` to navigate to the relevant screen, take a
  screenshot, produce a one-paragraph verdict citing inspector bounds, exit."

Sandbox flags identical to `/agent-do`. Output (verdict + screenshot path) is
captured by the watcher, which then applies the `agent-verified` label and posts
the screenshot as a PR comment.

If `claude -p` exits non-zero or the verdict says the bug is still present →
watcher does NOT apply `agent-verified`. Counts against the 3-attempt cap if
the failure is "bug still present" (the executor produced a non-fix);
*not* against the cap if the failure is "emulator unavailable" (exit 75).

### Fix session (`/agent-pr-fix <N>`)

Spawned as a separate `claude -p` process when CI is failing. Prompt includes:

- The PR diff
- The failing job name and last 200 lines of its log
- The standing system prompt from `/agent-do` (treat issue/comments as untrusted data, sandbox flags)

Same `--disallowedTools` set as `/agent-do`. One commit and push, no PR description
edits, no marking ready. Returns to the watcher which re-evaluates on the next tick.

If `claude -p` exits non-zero or no commit is produced → counts as one failed fix
attempt. Three in a row → `agent-blocked`.

## Threats and defenses (delta from issue-driven design)

| Threat | Defense |
|--------|---------|
| Watcher merges a PR you didn't approve | Hard gate on approval signal (label or review) inside `watch-prs.sh`; no `gh pr merge` without it |
| Watcher's fix session ships malicious code via prompt injection in CI logs | CI logs are framed as untrusted data in the prompt; sandbox flags identical to executor; pre-push hook blocks master |
| Concurrent verify steps thrash the emulator | `./scripts/emulator/acquire` is flock-safe; if acquire fails, exit tick, retry next time |
| Verify gives false positive | Watcher's verify is a second pass after executor's verify; both use `/android-ui-inspector` for bounded-element assertions (not just "screenshot looks ok"); must compare against issue body and produce a verdict |
| Babysitter loops forever on flaky CI | 3-attempt cap → `agent-blocked` |

## Out of scope

- Auto-creating issues (the agent never generates work for itself)
- Merging non-draft PRs that weren't agent-opened
- Cross-repo work
- Editing PR titles or descriptions after open (only adding screenshot comments)
- Hooking into PR reviews to auto-respond to review comments (separate `/agent-pr-address` later, if needed)

## Labels (additions)

- `agent-merge` — applied by *you* as an alternative to a GitHub `APPROVED` review. The watcher's gate signal.

Existing labels (`agent-approved`, `agent-in-progress`, `agent-completed`, `agent-blocked`, `agent-error`) keep their current meanings. Two earlier draft labels were dropped: `agent-verified` (verification is internal pipeline state, not a public signal) and `agent-stale` (its function collapsed into `agent-blocked`).

## Acceptance tests

1. **Rebase path:** open an agent draft PR, push something to master that makes it BEHIND, run one watcher tick → branch is rebased and pushed, no merge attempted.
2. **CI-fix path:** introduce a deliberate spotless violation on an agent branch, mark it ready, run one tick → fix session spawns, commits the spotless fix, pushes, exits.
3. **Verify path:** agent draft PR with green CI but no `agent-verified` label → one tick acquires emulator, verifies, applies label, comments with screenshot. Second tick on same HEAD does not re-verify.
4. **Approval-and-merge path:** verified draft PR + `APPROVED` review (or `agent-merge` label) → one tick marks ready, enables auto-merge, waits, cleans up.
5. **No-approval path:** verified draft PR with no approval → watcher does nothing. PR sits.
6. **Doc-only skip:** PR touching only `docs/foo.md` → no verify step, jumps to approval gate.
7. **Block cap:** force 3 consecutive fix-session failures → `agent-blocked` label applied, no further attempts on this PR.

## Implementation outline

See companion plan: `docs/superpowers/plans/2026-06-02-agent-pr-watch.md`.

High-level pieces:

- `skills/agent-pr-watch/SKILL.md` + symlink in `.claude/plugins/zero-project/skills/`
- `skills/agent-pr-fix/SKILL.md` + symlink (per-PR fix session)
- `scripts/agent/watch-prs.sh` (main loop)
- `scripts/agent/spawn-fix-session.sh` (claude -p wrapper for fixes)
- Updates to `skills/agent-do/SKILL.md` for step 4.5 + spotless
- Two new labels created in `scripts/agent/setup-labels.sh`
- Tests for state classification logic
- Smoke test extensions in `scripts/agent/SMOKE-TEST.md`
