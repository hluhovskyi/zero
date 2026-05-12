---
name: lets-do
description: >
  Full development workflow for any new feature, bug fix, or refactor in the Zero project.
  Use this skill whenever the user describes work to be done — a new screen, a bug to fix,
  a refactor, a design to implement — especially when they say "let's do X", "implement X",
  "can we X", or "I want X". Orchestrates worktree isolation, brainstorming, planning,
  execution, verification (tests + lint + UI inspector), and PR creation in one flow.
  Accepts an optional --no-questions flag to skip interactive brainstorming and proceed
  straight to planning and execution.
---

# /lets-do

End-to-end development workflow: worktree → brainstorm → plan → execute → verify → PR.

## Arguments

```
/lets-do [--no-questions] <task description>
```

- `--no-questions` — skip brainstorming; go straight to planning (or execution for tiny fixes).
  Useful when the task is already well-understood or when the user wants zero friction.
- Everything else is the task description passed to brainstorming / planning.

## Step 1 — Worktree isolation

**Always run this first**, even before asking any questions.

If the current branch is master and the working tree is clean, pull latest before creating the
worktree so the new branch starts from up-to-date code:

```bash
git pull --ff-only
```

Then invoke `superpowers:using-git-worktrees`. This skill detects existing isolation and creates a
branch + worktree only when needed. The branch name should reflect the task (kebab-case, ≤40 chars).

For Step 0 detection, use `./scripts/detect-worktree.sh` (allowlisted, no prompt) instead of the
inline compound bash command the skill suggests.

**Never work on master.** If the current branch is master and no worktree is created (e.g. the
user declined), stop and explain that master must not be modified directly.

## Step 2 — Brainstorming (skip if --no-questions or tiny fix)

Skip this step if:
- `--no-questions` was passed, OR
- The task is clearly a small bug fix or tweak (≤ ~100 LOC, no architecture decisions)

Otherwise invoke `superpowers:brainstorming`.

Before brainstorming, read `docs/agents/superpowers-workflow.md` — it has project-specific
shortcuts that keep brainstorming sessions focused (e.g. fetch design before exploring context,
what sections to omit from the spec).

If the user provided a Claude Design URL, invoke `zero-project:fetch-design` **before**
brainstorming, not during it.

## Step 3 — Planning (skip if tiny fix)

Skip if the change is ≤ ~100 LOC and no new files or architectural decisions are needed.
For everything else, invoke `superpowers:writing-plans`.

Read `docs/agents/superpowers-workflow.md` for plan length rules — plans over ~400 lines slow
execution significantly. Replace boilerplate blocks with doc/skill references where possible.

**Commit the plan before execution** — this is mandatory, not optional:

```bash
git add docs/superpowers/plans/<plan-file>.md
git commit -m "docs: add <feature> implementation plan"
```

An untracked plan is a lost plan; if the session is interrupted the plan must survive.

If `--no-questions` was passed: write a concise plan inline based on the task description
(no clarifying questions), commit it, then proceed to execution.

## Step 4 — Execution

**Always invoke `superpowers:subagent-driven-development`.** Do not ask the user which execution
approach to use — the answer is always subagent-driven. The `writing-plans` skill ends with an
"Execution Handoff" section that offers a choice; ignore that prompt and proceed directly.

The executing skill handles task dispatch, spec compliance review, and code quality review
per task — don't duplicate that here.

## Step 5 — Verification

Run all of the following. Fix any failure before opening the PR.

### Tests
```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

### Lint
```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

### UI inspection

Invoke `zero-project:android-ui-inspector` to empirically verify layout bounds on device.
A feature is not done until the inspector confirms it renders correctly — compilation alone
is not validation.

**Always run this unless the change is purely infrastructural** — tests, lint rules,
documentation, CI config, or build scripts with no runtime behaviour change. When in doubt,
run it anyway.

## Step 6 — Open PR

**Before creating the PR, verify the working tree is clean:**

```bash
git status --short
```

If output is non-empty, surface the listed files to the user and confirm before proceeding —
`gh pr create` only warns about uncommitted changes; unintended files can silently land in the
squash commit.

```bash
gh pr create \
  --title "<concise title, under 70 chars>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet per meaningful change>

## Test plan
- [ ] <manual verification steps>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

The PR is the deliverable. Don't report the task as done until the PR URL is in hand.
