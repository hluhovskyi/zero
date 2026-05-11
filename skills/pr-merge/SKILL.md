---
name: pr-merge
description: >
  Merge a GitHub PR and delete the remote branch. If the current local branch is the one being
  merged, run unit tests, linters, and a full build first and abort on failure.
  Use when the user says "merge the PR", "merge and clean up", or "ship this".
---

# PR Merge

Safely merge a GitHub PR and clean up the branch.

## Step 1 — Determine the PR

- If the user provided a PR number or URL, use it.
- Otherwise infer from the current branch: `gh pr view --json number,headRefName,url`
- If no PR is found, ask for the PR number.

Record:
- `pr_number` — the PR number
- `pr_branch` — the head branch name (e.g. `feat/my-feature`)
- `is_current_branch` — whether the local HEAD is on `pr_branch`

## Step 2 — GitHub Actions checks

Check that all CI checks on the PR are passing:

```bash
gh pr checks <pr_number>
```

Parse the output:
- **All passing** — continue to Step 3.
- **Any failing** — stop and report which check failed. Do not merge.
- **Any pending** — poll automatically every 15 seconds until all checks resolve, reporting progress each time. Once all checks finish, re-evaluate (pass → continue, fail → stop and report).

## Step 3 — Pre-merge checks (only if `is_current_branch`)

If the local branch matches `pr_branch`, run the full quality gate in this order. **Stop and report failure after the first failing step — do not continue to merge.**

### 3a. Unit tests
```bash
./gradlew testDebugUnitTest
```

### 3b. Lint
```bash
./gradlew lint
```
Parse output for errors (warnings are OK). Fail if any module reports lint errors.

### 3c. Build
```bash
./gradlew assembleDebug
```

If all three pass, report: `✓ Tests, lint, and build passed — proceeding with merge.`

If the branch is not current (user is merging someone else's PR or a different branch), skip this step entirely.

## Step 4 — Check for conflicts

Before merging, check if the PR has conflicts with master:

```bash
gh pr view <pr_number> --json mergeable,mergeStateStatus
```

- **`mergeable: MERGEABLE`** — no conflicts, proceed to Step 5.
- **`mergeable: CONFLICTING`** — resolve conflicts:
  1. Checkout the PR branch and rebase onto master:
     ```bash
     git checkout <pr_branch>
     git fetch origin
     git rebase origin/master
     ```
  2. For each conflicted file, resolve manually — keep the correct version, stage with `git add <file>`.
  3. Continue the rebase: `git rebase --continue`
  4. Force-push the resolved branch: `git push --force-with-lease`
  5. Re-check `gh pr view <pr_number> --json mergeable` until `MERGEABLE`, then proceed.
- **`mergeable: UNKNOWN`** — wait 5 seconds and re-check; GitHub is still computing mergeability.

## Step 5 — Merge the PR

```bash
gh pr merge <pr_number> --squash --delete-branch
```

Use `--squash` to keep the main branch history clean. `--delete-branch` removes the remote branch automatically.

If the merge fails, report the error and stop — do not force-merge.

## Step 6 — Clean up and update master (only if `is_current_branch`)

Switch to master, delete the local branch, and pull the latest:

```bash
git checkout master
git branch -d <pr_branch>
git pull
```

Use `-d` (safe delete) — if it fails because the branch is not fully merged locally, report it and do not force-delete.

Always run `git pull` after switching to master so the local copy reflects the merge commit.

## Step 7 — Report

```
Merged PR #<N> "<title>"
Branch <pr_branch> deleted (remote + local)
Now on: master (<latest commit hash> <commit message>)
```

## Guardrails

- **Never force-merge** — if checks fail or there are conflicts, stop and tell the user.
- **Never force-delete** the local branch (`-D`) — use safe delete (`-d`) only.
- **Skip pre-merge checks** if the current branch is not the PR branch — don't run tests on unrelated code.
- **Always pull master** after switching to it so the local copy is up to date.
- If `--delete-branch` is not supported or the remote branch was already deleted, skip that step without error.
