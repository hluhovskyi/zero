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

- `--no-questions` — run the full flow without prompting the user. Architecture research,
  spec, plan, execution all still happen; just skip the interactive clarifying questions.
  Means "don't bother me," not "ship blind."
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

After the worktree is created, acquire a dedicated emulator for this session **only
when you reach UI verification** — not at session start. Skipping this for non-UI
sessions saves RAM:

```bash
./scripts/emulator/acquire
```

This pins the session to one emulator so parallel sessions don't interfere. If all are claimed it
auto-starts a new instance via `./scripts/emulator/start` (pass `--no-auto-start` to
suppress). The `.emulator-serial` file it writes is read by `scripts/ui/adb` and every other
UI helper. A PreToolUse hook (`scripts/guard-adb.sh`) denies bare `adb` / `./gradlew installDebug`
to keep parallel sessions from talking to each other's emulators.

**Never work on master.** If the current branch is master and no worktree is created (e.g. the
user declined), stop and explain that master must not be modified directly.

## Step 2 — Brainstorming

Invoke `superpowers:brainstorming` for the spec, unless this is a tiny fix (≤ ~100 LOC,
no new files, no architectural decisions). **Under `--no-questions`, do not prompt the user
with clarifying questions — answer them yourself from the architecture research and write
the spec.** Read `docs/agents/superpowers-workflow.md` first for the "Explore project context"
reads (the way you find the canonical precedent — e.g. `FeedbackComponent` for a new feature
`@Component` — before designing).

If the user provided a Claude Design URL, invoke `zero-project:fetch-design` **before**
brainstorming.

## Step 3 — Planning (skip if tiny fix)

Skip if the change is ≤ ~100 LOC and no new files or architectural decisions are needed.
For everything else, invoke `superpowers:writing-plans`.

Read `docs/agents/superpowers-workflow.md` for plan length rules — plans over ~400 lines slow
execution significantly. Replace boilerplate blocks with doc/skill references where possible.

**Commit, push, and open a draft PR for the plan.** Mandatory. Step 6 flips this same PR to
ready — never open a second one.

```bash
git add docs/superpowers/plans/<plan-file>.md
git commit -m "docs: add <feature> implementation plan"
git push -u origin HEAD
gh pr create --draft --title "<feature>: plan" --body "Plan: docs/superpowers/plans/<plan-file>.md"
```

Report the PR URL. Under `--no-questions`, follow the same flow — `writing-plans` still
runs; just answer its decision points from the architecture research instead of asking
the user.

**Planning-only sessions** (design-to-PR flows split per `docs/agents/superpowers-workflow.md`):
open a **non-draft** `docs: <feature> plan` PR, skip Steps 4–6, stop.

## Step 4 — Execution

**Always invoke `superpowers:subagent-driven-development`.** Do not ask the user which execution
approach to use — the answer is always subagent-driven. The `writing-plans` skill ends with an
"Execution Handoff" section that offers a choice; ignore that prompt and proceed directly.

The executing skill handles task dispatch, spec compliance review, and code quality review
per task — don't duplicate that here.

## Step 5 — Verification

Run all of the following. Fix any failure before opening the PR.

### Build checks — one Gradle invocation

**Batch the Gradle gates into a single fail-fast invocation** — one daemon spin-up, stops at the first failure. Don't run them as separate calls.

```bash
./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25
```

**Run efficiency** (these dominate token/time cost otherwise):
- **Never poll a background task** — `run_in_background` auto-notifies on completion; don't `cat`/`Read` its output before the notification, and don't re-read an empty output.
- **Acquire the emulator once**, at first UI need (Step 5), and hold it for all device work — don't acquire/release per check.

### UI inspection

Invoke `zero-project:android-ui-inspector` to empirically verify layout bounds on device.
A feature is not done until the inspector confirms it renders correctly — compilation alone
is not validation.

**Always run this unless the change is purely infrastructural** — tests, lint rules,
documentation, CI config, or build scripts with no runtime behaviour change. When in doubt,
run it anyway.

## Step 6 — Mark PR ready

The draft PR from Step 3 already exists. Verify the tree is clean, update title + body to
reflect the implementation, then flip it.

```bash
git status --short   # confirm clean before proceeding
gh pr edit --title "<concise title, under 70 chars>" --body "$(cat <<'EOF'
## Summary
- <bullet per meaningful change>

## Test plan
- [ ] <manual verification steps>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr ready
```

Don't report the task as done until the PR is ready-for-review.
