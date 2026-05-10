---
name: pr-address
description: >
  Pull review comments from a GitHub PR and address them one by one. Use this when the user says
  "address PR comments", "fix review feedback", "respond to review", or similar.
  Supports special prefixes: /harness-issue, /optimization-issue, /feature-issue —
  these create GitHub issues instead of code changes.
---

# PR Address

Fetch all inline review comments from a GitHub PR and address each one in the codebase.

## Step 1 — Determine the PR

- If the user provided a PR number or URL, use it.
- Otherwise, infer from the current branch: `gh pr view --json number,url` to find the open PR for HEAD.
- If no PR is found, ask for the PR number.

## Step 2 — Fetch all review comments

```bash
gh api repos/<owner>/<repo>/pulls/<pr>/comments
```

Parse each comment into:
- `file` — file path
- `line` — line number
- `body` — comment text

Also fetch top-level review bodies (not inline):
```bash
gh api repos/<owner>/<repo>/pulls/<pr>/reviews
```

Group all comments by file for efficient reading.

## Step 3 — Classify each comment

Read the `body` of every comment and classify it:

| Prefix | Action |
|--------|--------|
| `/harness-issue` | Create a GitHub issue with label `harness`. Body = comment text after the prefix. |
| `/optimization-issue` | Create a GitHub issue with label `optimization`. Body = comment text after the prefix. |
| `/feature-issue` | Create a GitHub issue with label `feature`. Body = comment text after the prefix. |
| *(anything else)* | Address as a code change. |

Process issue-creating comments **first** (they don't touch code), then tackle code changes.

## Step 4 — Create GitHub issues (for prefixed comments)

For each prefixed comment, create an issue:

```bash
gh issue create \
  --repo <owner>/<repo> \
  --label <label> \
  --title "<derived title>" \
  --body "<comment text after the prefix>"
```

**Deriving the title:** use the first sentence of the comment body (trimmed), max 70 characters. If the body starts with a label like "Harness:" or "Optimization:" strip it.

**If the label doesn't exist**, create it first:
```bash
gh label create <label> --repo <owner>/<repo> --color "<color>" --description "<description>"
```

Use these default colors:
- `harness` → `#0075ca`
- `optimization` → `#e4e669`
- `feature` → `#a2eeef`

## Step 5 — Address code comments

For each remaining (non-prefixed) comment:

1. **Read the file** at the flagged line to understand context.
2. **Understand the intent** — do not make literal word-for-word changes; implement what the reviewer actually meant.
3. **Make the change** — edit only what the comment asks for. Do not refactor surrounding code that wasn't mentioned.
4. **Keep a mental list** of all changed files to compile before committing.

Work through comments in file order (top to bottom within each file, files in alphabetical order) to avoid re-reading the same file multiple times.

## Step 6 — Build

After all code changes are made:

```bash
./gradlew compileDebugKotlin
```

Fix any compilation errors before proceeding. If a fix introduces a new error elsewhere, fix that too — do not leave broken code.

## Step 7 — Commit

Stage and commit all code changes in a single commit:

```
refactor: address PR #<N> review comments

- <one-line summary per comment addressed>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

Then push immediately.

## Guardrails

- **One commit** for all code changes (unless the changes are logically unrelated — then split naturally).
- **Never address a `/xxx-issue` comment as a code change** — always create an issue for it.
- **Never skip a comment** — if you are unsure what a comment means, make your best interpretation and note it in the commit message.
- **No scope creep** — only change what the reviewer asked for. If you spot an adjacent improvement, note it but don't implement it.
- **Issues first, code second** — create all GitHub issues before touching any files.
