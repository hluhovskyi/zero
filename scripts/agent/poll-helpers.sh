#!/usr/bin/env bash
# Sourceable helpers for the agent polling script.
# Functions are pure: they read JSON on stdin and inspect args; they do not call gh.

# Usage: last_labeler_is_me <expected-login> <label-name> < events.json
# Reads `gh api repos/.../issues/N/events` output, finds the most recent
# `labeled` event for <label-name>, returns 0 if its actor matches.
last_labeler_is_me() {
  local expected="$1" label="$2"
  local actor
  actor="$(jq -r --arg label "$label" \
    '[.[] | select(.event == "labeled" and .label.name == $label)] | last | .actor.login // empty')"
  [[ "$actor" == "$expected" ]]
}

# Usage: issue_author_is_me <expected-login> < issue.json
# Reads `gh issue view --json author` output, returns 0 if the author login matches.
issue_author_is_me() {
  local expected="$1"
  local author
  author="$(jq -r '.author.login // empty')"
  [[ "$author" == "$expected" ]]
}
