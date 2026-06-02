#!/usr/bin/env bash
# PreToolUse hook for Bash commands. Denies inline shell wait/poll loops
# (`until`/`while` combined with `sleep`) and points at the fast, allowlisted
# alternatives — including writing a purpose-built script when none exists.
# Called by .claude/settings.json hooks.PreToolUse[matcher=Bash].
#
# Why: an `until cond; do sleep N; done` poll-loop is slow AND its leading token
# is `until`/`while`, not the real command — so it never matches the allowlist
# and prompts on every call. Foreground `sleep` is blocked by the harness anyway.
# The agent should await background-task completion notifications, use an existing
# helper (e.g. ./scripts/ui/screenshot.sh), or create a single-call script.
set -uo pipefail

input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$input")

deny() {
    local reason="$1"
    printf '%s\n' "{\"continue\":false,\"stopReason\":${reason}}"
    exit 0
}

# A wait/poll loop = a `while`/`until` keyword (as a word) together with `sleep`.
# Both must be present so legitimate `while read … done < file` (no sleep) passes,
# and the hook never sees a script's internals — only the agent's command string,
# so `./scripts/ui/screenshot.sh` (which sleeps internally) is unaffected.
if echo "$cmd" | grep -qE '(^|[|;&[:space:]])(until|while)([[:space:]]|$)' \
    && echo "$cmd" | grep -qE '(^|[|;&[:space:]])sleep([[:space:]]|$)'; then
    deny '"Inline wait/poll loops (`until`/`while` + `sleep`) are slow and bypass the permission allowlist — the segment leads with `until`/`while`, not the real command, so it prompts every call (foreground `sleep` is blocked anyway).\n\nDo one of these instead:\n  • Waiting on a background task you started? Do NOT poll — await its completion notification, then read its output file.\n  • Need a device screenshot or to wait for the app window? Use ./scripts/ui/screenshot.sh (it waits internally, one allowlisted call).\n  • No existing script does exactly what you need? CREATE one under scripts/ that performs the whole sequence (wait + action) in a single call, add it to .claude/settings.json `permissions.allow`, then invoke that script. One responsibility per script — do not chain/loop inline."'
fi

exit 0
