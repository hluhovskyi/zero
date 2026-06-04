#!/usr/bin/env bash
# Sourceable pure functions for PR-watcher state classification.
# Functions read JSON on stdin (output of `gh pr view --json ...`) and return
# state names. They do NOT call gh / git / network — keep them testable.

# Usage: pr_has_approval <expected-login> < pr.json
# JSON must include: labels[*].name, reviews[*].state, reviews[*].author.login,
#                    reviews[*].commit_id, reviews[*].submitted_at, headRefOid
# Returns 0 if PR carries the watcher's gate signal:
#   - `agent-merge` label is present (caller MUST follow up with an actor check
#     via `gh api`, since label presence alone doesn't bind to who set it), OR
#   - the LATEST review by <expected-login> (most recent submitted_at) is
#     APPROVED AND its commit_id equals the PR's current headRefOid.
#
# "Latest review wins" is intentional: GitHub keeps every submitted review in
# the list, so without this an old APPROVED record persists even after the
# reviewer submits REQUEST_CHANGES or COMMENT to retract. The label path
# doesn't have this problem — labels are re-read fresh each tick.
pr_has_approval() {
  local expected="$1"
  local json
  json="$(cat)"
  local has_label latest_approved_at_head
  has_label="$(jq -r '[.labels[]?.name] | any(. == "agent-merge")' <<<"$json")"
  latest_approved_at_head="$(jq -r --arg me "$expected" '
    .headRefOid as $head
    | [.reviews[]? | select(.author.login == $me)]
    | sort_by(.submitted_at // "")
    | last
    | (. != null
        and .state == "APPROVED"
        and .commit_id == $head)
  ' <<<"$json")"
  [[ "$has_label" == "true" || "$latest_approved_at_head" == "true" ]]
}

# Usage: pr_is_doc_only < pr.json
# JSON must include: files[*].path
# Returns 0 when every changed file is genuine prose docs under `docs/`.
# Explicitly NOT doc-only: root CLAUDE.md / AGENTS.md / README.md and anything
# under `.claude/` — these affect agent or app runtime behavior even though
# they happen to be `.md`. The watcher's only carve-out is `docs/**/*.md`.
pr_is_doc_only() {
  jq -e '
    [.files[]?.path] as $paths
    | ($paths | length) > 0
    and ($paths | all(. as $p
        | ($p | endswith(".md"))
        and ($p | startswith("docs/"))
        and (($p | startswith(".claude/")) | not)
      ))
  ' >/dev/null
}

# Usage: pr_was_verified_at_head <state-dir> < pr.json
# State-dir contains pr-<N>.verified files: "SHA EPOCH" one line.
# JSON must include: number, headRefOid
# Returns 0 if the recorded SHA matches the current PR HEAD.
pr_was_verified_at_head() {
  local state_dir="$1"
  local json
  json="$(cat)"
  local num head_sha
  num="$(jq -r '.number' <<<"$json")"
  head_sha="$(jq -r '.headRefOid // empty' <<<"$json")"
  [[ -n "$head_sha" ]] || return 1
  local file="$state_dir/pr-$num.verified"
  [[ -f "$file" ]] || return 1
  local recorded
  recorded="$(awk '{print $1; exit}' "$file")"
  [[ "$recorded" == "$head_sha" ]]
}

# Usage: classify_pr_state <state-dir> < pr.json
# Assumes the caller has already filtered by pr_has_approval. Returns one of:
#   behind-clean | behind-dirty | ci-failing | needs-verify | ready-to-merge | unknown
# State precedence (first match wins): behind-dirty > behind-clean > ci-failing > needs-verify > ready-to-merge > unknown.
# Note: "branch too old" is handled by the rebase session itself (exit 2 →
# watcher marks agent-blocked); we don't gate on age up-front.
classify_pr_state() {
  local state_dir="$1"
  local json
  json="$(cat)"

  if jq -e '.mergeStateStatus == "DIRTY"' <<<"$json" >/dev/null; then
    echo "behind-dirty"
    return 0
  fi

  if jq -e '.mergeStateStatus == "BEHIND"' <<<"$json" >/dev/null; then
    echo "behind-clean"
    return 0
  fi

  if jq -e '
    [.statusCheckRollup[]? | select(.status == "COMPLETED" and .conclusion == "FAILURE")] | length > 0
  ' <<<"$json" >/dev/null; then
    echo "ci-failing"
    return 0
  fi

  # CI still running → unknown for now, retry next tick
  if jq -e '
    [.statusCheckRollup[]? | select(.status != "COMPLETED")] | length > 0
  ' <<<"$json" >/dev/null; then
    echo "unknown"
    return 0
  fi

  # CI green at this point. Verify or merge?
  if pr_is_doc_only <<<"$json"; then
    echo "ready-to-merge"
    return 0
  fi

  if pr_was_verified_at_head "$state_dir" <<<"$json"; then
    echo "ready-to-merge"
    return 0
  fi

  echo "needs-verify"
}
