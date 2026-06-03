#!/usr/bin/env bash
# Sourceable pure functions for PR-watcher state classification.
# Functions read JSON on stdin (output of `gh pr view --json ...`) and return
# state names. They do NOT call gh / git / network — keep them testable.

# Usage: pr_has_approval <expected-login> < pr.json
# JSON must include: labels[*].name, reviews[*].state, reviews[*].author.login,
#                    reviews[*].commit_id, headRefOid
# Returns 0 if PR carries the watcher's gate signal:
#   - `agent-merge` label is present (caller MUST follow up with an actor check
#     via `gh api`, since label presence alone doesn't bind to who set it), OR
#   - at least one APPROVED review by <expected-login> WHOSE commit_id equals
#     the PR's current headRefOid (stale reviews don't gate — protects against
#     the watcher pushing new commits after approval).
pr_has_approval() {
  local expected="$1"
  local json
  json="$(cat)"
  local has_label has_approved_at_head
  has_label="$(jq -r '[.labels[]?.name] | any(. == "agent-merge")' <<<"$json")"
  has_approved_at_head="$(jq -r --arg me "$expected" '
    .headRefOid as $head
    | [.reviews[]?
        | select(.state == "APPROVED"
                 and .author.login == $me
                 and .commit_id == $head)] | length > 0
  ' <<<"$json")"
  [[ "$has_label" == "true" || "$has_approved_at_head" == "true" ]]
}

# Usage: pr_is_doc_only < pr.json
# JSON must include: files[*].path
# Returns 0 when every changed file matches *.md AND lives under docs/ or root.
pr_is_doc_only() {
  jq -e '
    [.files[]?.path] as $paths
    | ($paths | length) > 0
    and ($paths | all(. as $p | ($p | endswith(".md")) and (($p | startswith("docs/")) or (($p | contains("/")) | not))))
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

# Usage: pr_is_stale <max-age-days> < pr.json
# JSON must include: createdAt (ISO 8601), mergeStateStatus
# Returns 0 when the branch is older than <max-age-days> AND DIRTY.
pr_is_stale() {
  local max_days="${1:-2}"
  jq -e --argjson maxDays "$max_days" '
    (.createdAt | fromdateiso8601) as $ts
    | (now - $ts) as $age_sec
    | .mergeStateStatus == "DIRTY"
      and $age_sec > ($maxDays * 86400)
  ' >/dev/null
}

# Usage: classify_pr_state <state-dir> < pr.json
# Assumes the caller has already filtered by pr_has_approval. Returns one of:
#   behind-clean | behind-dirty | ci-failing | needs-verify | ready-to-merge | stale | unknown
# State precedence (first match wins): stale > behind-dirty > behind-clean > ci-failing > needs-verify > ready-to-merge > unknown.
classify_pr_state() {
  local state_dir="$1"
  local json
  json="$(cat)"

  if jq -e '.mergeStateStatus == "DIRTY"' <<<"$json" >/dev/null; then
    if pr_is_stale 2 <<<"$json"; then
      echo "stale"
      return 0
    fi
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
