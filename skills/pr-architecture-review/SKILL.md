---
name: pr-architecture-review
description: >
  Advisory architecture-smell pass over a branch/PR diff — house-pattern
  conformance plus structural smells (sentinel params, boolean-as-type,
  duplicated decisions). Use for "architecture review", "any smells?", or
  before merging a structural change. Not a bug hunt.
---

# /pr-architecture-review

A structural pass over a change: does it follow the codebase's architecture, and does it carry a smell? This is **distinct from** `/code-review` (correctness bugs) and spotless (style). Findings are **advisory** — surfaced for a human to decide, never auto-applied and never merge-blocking.

The full catalog of house patterns + smell signatures + the reasoning method lives in [`docs/agents/architecture-review.md`](../../docs/agents/architecture-review.md). **Read it first** — this skill is the runner; the doc is the rubric.

**The key shortcut:** many house patterns (visibility, derivation, theme, module-encapsulation, handler-shape, scoped-builder, closeable/job handling) are already caught by custom lint detectors — CI fails on them, so re-flagging them here is redundant. See [`architecture.md` § Lint Enforcement](../../docs/agents/architecture.md#lint-enforcement) for the full list. Spend the pass on the structural, judgment-call smells no detector can express.

## Step 1 — Determine the target

- PR number/URL given → use it: `gh pr diff <N> --name-only` for the file set, `gh pr diff <N>` for the patch.
- On a feature branch, no arg → diff against the base: `git diff master...HEAD --stat` and `git diff master...HEAD`.
- If neither resolves, ask which branch/PR.

## Step 2 — Read the change AND its modules

The #1 mistake is reading only the diff. Structural smells (a duplicated decision, a flag switched at three sites, a producer naming a consumer) span files the diff doesn't all touch.

1. List changed files; group by module/package.
2. For each touched package, **read the package**, not just the changed lines — the sibling files reveal duplication and the abstraction's other implementers.
3. Read the nearest `AGENTS.md` (module + any sub-package one) — that's the local contract.

## Step 3 — Apply the rubric

Work the catalog in [`docs/agents/architecture-review.md`](../../docs/agents/architecture-review.md):

1. **Conformance** — does the change follow the house patterns (feature triad, ViewModel-no-derivation, attach-not-constructor, `@BindsInstance` lightweight-only, module boundaries, default-handlers, fix-at-producer)?
2. **Smells** — scan for the signatures (sentinel/ignored param, boolean-as-type, duplicated decision, abstraction one impl fakes, wrong-layer ownership, special-case-as-branch, conflated responsibilities, downstream shim, threaded value).
3. **Collapse** — for each cluster of symptoms, name the one missing abstraction that removes them all. That collapse *is* the finding.

## Step 4 — Report

Per finding, in the doc's format:

```
[smell name] file:line
Symptom:  <quoted line(s) / the duplication, with paths>
Cause:    <missing abstraction or misplaced responsibility>
Smaller change: <the one move that removes the symptoms>
```

- Lead with the highest-leverage finding (collapses the most symptoms).
- **Cite or cut** — every finding quotes a `file:line`. No quote, no finding.
- **Zero findings is the common, correct result** for a well-scoped change. Say so plainly; never invent a finding to look thorough.
- Keep it advisory. End by asking whether to post the findings as inline PR comments (`gh pr comment` / review comments) or leave them in chat — don't post unprompted.

## What this skill MUST NOT do

- Hunt correctness bugs (that's `/code-review`) or reformat (that's spotless).
- Edit source or apply fixes — it observes and reports.
- Block a merge or change a verdict — findings are advisory.
- Invent findings to fill space — empty is a valid result.
