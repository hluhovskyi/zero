#!/usr/bin/env bash
# PreToolUse hook for Bash commands. Denies bare `adb` and `gradle install*`
# invocations with a remediation message pointing at the worktree-safe wrappers.
# Called by .claude/settings.json hooks.PreToolUse[matcher=Bash].
#
# Detection rule: `adb` and `./gradlew` are only considered a command invocation
# when they appear at the **start of a command segment** — i.e. at the start of
# the command string or right after a `|`, `;`, or `&` (covers `&&` and `||`).
# A space before `adb` (`grep adb foo`) means adb is an argument, not a command,
# and is allowed.
set -uo pipefail

input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$input")

deny() {
    local reason="$1"
    printf '%s\n' "{\"continue\":false,\"stopReason\":${reason}}"
    exit 0
}

# ── adb: bare invocation NOT through the wrapper ────────────────────────────
# Match `adb` as the first token of a segment. The wrapper path
# `./scripts/ui/adb` is also a first-token but is allowed (handled below).
adb_pattern='(^|[|;&])[[:space:]]*adb([[:space:]]|$)'
wrapper_pattern='(^|[|;&])[[:space:]]*\./scripts/ui/adb([[:space:]]|$)'

if echo "$cmd" | grep -qE "$adb_pattern"; then
    if echo "$cmd" | grep -qE "$wrapper_pattern"; then
        :  # wrapper path, fine
    else
        # Pull the first arg after the bare `adb` (best-effort; only used in remediation).
        first_arg=$(echo "$cmd" \
            | grep -oE "$adb_pattern[^[:space:]|;&]*" \
            | head -1 \
            | sed -E 's/.*adb[[:space:]]+([^[:space:]|;&]+).*/\1/')
        case "$first_arg" in
            devices|start-server|kill-server|version|--version|help|--help)
                # Allow-listed server-level subcommands: don't need pinning.
                ;;
            *)
                deny '"Bare `adb` command would target the default device and can clobber sibling worktrees.\n\nRun via the worktree-pinned wrapper instead:\n  ./scripts/ui/adb '"$first_arg"' …\n\nThe wrapper reads .emulator-serial and pins ANDROID_SERIAL. If no emulator is claimed yet, run ./scripts/emulator/acquire first."'
                ;;
        esac
    fi
fi

# ── gradle installDebug: installs to every device ──────────────────────────
# Match `./gradlew` as the first token of a segment, then any text up to the
# next command separator, then a literal `installDebug` or `installRelease`.
gradle_pattern='(^|[|;&])[[:space:]]*\./gradlew[^|;&]*\binstall(Debug|Release)\b'
if echo "$cmd" | grep -qE "$gradle_pattern"; then
    deny '"`./gradlew installDebug` installs to EVERY connected device and clobbers sibling worktrees.\n\nUse the worktree-pinned installer:\n  ./scripts/install-app.sh\n\nIt builds assembleDebug and installs only to this worktree'\''s emulator."'
fi

exit 0
