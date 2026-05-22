---
name: pr-merge
description: Merge a GitHub PR and clean up the branch. Use when the user says "merge the PR", "merge and clean up", or "ship this".
---

# PR Merge

Safely merge a GitHub PR and clean up the branch.

## Step 1 — Determine the PR

- If the user provided a PR number or URL, use it.
- Otherwise infer from the current branch: `gh pr view --json number,headRefName,url`
- If no PR is found, ask for the PR number.

Record:
- `pr_number`, `pr_branch` (e.g. `feat/my-feature`)
- `is_current_branch` — whether local HEAD is on `pr_branch`
- `is_worktree` — `git rev-parse --git-dir` returns `.git` in the main repo, a longer path inside a worktree. Also record `worktree_path` (current directory) and `main_repo_path` (first line of `git worktree list --porcelain | head -1 | sed 's/^worktree //'`).

## Step 2 — Pre-merge checks (only if `is_current_branch`)

First, check if the PR is purely non-runtime (no Android source changes):

```bash
gh pr diff <pr_number> --name-only
```

If every changed file falls under `skills/`, `docs/`, `.claude/`, or matches `*.md` — skip
the Gradle checks entirely and note "non-runtime change, skipping build verification."

Otherwise run in order — stop and report on first failure, do not continue to merge:

```bash
./gradlew spotlessApply
./gradlew testDebugUnitTest
./gradlew lint          # warnings OK; fail on errors
./gradlew assembleDebug
```

Then run the E2E suite (CI doesn't):

```bash
./scripts/emulator/acquire
./scripts/run-android-tests.sh
```

If all pass: `✓ Tests, lint, build, and E2E passed — proceeding with merge.`

## Step 3 — Check mergeability

```bash
gh pr view <pr_number> --json mergeable,mergeStateStatus
```

`mergeable` reports conflicts only; `mergeStateStatus` is the actual merge gate. Check both.

- **`CLEAN` / `HAS_HOOKS`** — proceed.
- **`UNKNOWN`** — wait 5 s, re-check.
- **`BEHIND`** — `git fetch origin && git merge origin/<base>` into `pr_branch`, push, re-check.
- **`DIRTY`** (or `mergeable: CONFLICTING`) — merge `origin/<base>` and resolve (see [Conflict Resolution](docs/agents/branch-management.md#conflict-resolution)), push, re-check.
- **`BLOCKED`** — required reviews/checks unsatisfied. Stop and report.

**Don't `--auto` while `BEHIND` / `DIRTY` / `BLOCKED`** — auto-merge waits for `CLEAN` and won't update the branch for you.

## Step 4 — Merge

```bash
gh pr merge <pr_number> --squash
```

**Don't pass `--delete-branch` from inside a worktree.** `gh` runs `git checkout master` locally before deleting the branch, which fails when master is already checked out in the main repo (`'master' is already used by worktree at ...`). Step 6 deletes the branch (remote + local) anyway, so the flag is redundant. GitHub also auto-deletes the head branch on merge when the repo has that setting on.

If the merge fails because branch protection requirements haven't been met yet (e.g. CI still running, required review pending), use `--auto` so GitHub merges automatically once all requirements pass:

```bash
gh pr merge <pr_number> --squash --auto
```

Then use the wait-for-ci script to block until CI finishes and the PR is merged:

```bash
./scripts/github/wait-for-ci.sh <pr_number>
```

Once it exits 0, continue to Step 5 (which is now just the post-merge cleanup check).

Stop on all other failures — do not force-merge.

## Step 5 — Wait for CI

```bash
./scripts/github/wait-for-ci.sh <pr_number>
```

Polls every 15 s; exits 0 on pass, 1 on fail. Report failure if CI fails — the merge is done but master may need attention.

## Step 6 — Clean up

**If `is_worktree`:** run all git commands with `-C <main_repo_path>`.

1. Remove the worktree (can't checkout master in a worktree when master is already in the main repo):
   ```bash
   git -C <main_repo_path> worktree remove <worktree_path> --force
   ```
2. Delete the local branch:
   ```bash
   git -C <main_repo_path> branch -D <pr_branch>
   ```
3. Delete the remote branch (skip silently if 404):
   ```bash
   gh api repos/{owner}/{repo}/git/refs/heads/<pr_branch> -X DELETE
   ```
4. Pull master:
   ```bash
   git -C <main_repo_path> pull origin master
   ```

**If not `is_worktree`:** same steps 2–4 without `-C`, plus `git checkout master` first.

Use `-D` throughout — squash-merged branches never pass git's safe-delete check.

## Step 7 — Report

```
Merged PR #<N> "<title>"
Branch <pr_branch> deleted (remote + local)
Now on: master (<hash> <message>)
[if worktree] Worktree <worktree_path> removed — cd to <main_repo_path>
```

## Guardrails

- **Never use `--admin`** — this bypasses branch protection and is strictly forbidden.
- Never force-merge.
- Skip pre-merge checks if not on `pr_branch`.
- Always pull master after cleanup.
