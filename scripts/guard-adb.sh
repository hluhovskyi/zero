#!/usr/bin/env bash
# PreToolUse hook for Bash commands. Denies bare `adb` and `gradle install*`
# invocations with a remediation message pointing at the worktree-safe wrappers.
# Called by .claude/settings.json hooks.PreToolUse[matcher=Bash].
set -uo pipefail

input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$input")

deny() {
    local reason="$1"
    printf '%s\n' "{\"continue\":false,\"stopReason\":${reason}}"
    exit 0
}

# ── adb: bare invocation NOT through the wrapper ────────────────────────────
# Allow:
#   ./scripts/ui/adb …               (the wrapper itself)
#   adb devices / start-server / kill-server / version  (server-level, allow-listed)
# Deny everything else that calls `adb` at the start or after a pipe/semicolon.
if echo "$cmd" | grep -qE '(^|[|;&]|[[:space:]])adb([[:space:]]|$)'; then
    # If it's the wrapper path, allow.
    if echo "$cmd" | grep -qE '\./scripts/ui/adb([[:space:]]|$)'; then
        :  # wrapper path, fine
    else
        # Extract the first token after `adb ` and see if it's allow-listed.
        first_arg=$(echo "$cmd" \
            | grep -oE '(^|[|;&]|[[:space:]])adb([[:space:]]+[^[:space:]|;&]+)?' \
            | head -1 \
            | sed -E 's/.*adb[[:space:]]+([^[:space:]|;&]+).*/\1/')
        case "$first_arg" in
            devices|start-server|kill-server|version|--version|help|--help|adb)
                # bare `adb` with allow-listed first arg, or alone, or no first arg parsed: allow
                ;;
            *)
                deny '"Bare `adb` command would target the default device and can clobber sibling worktrees.\n\nRun via the worktree-pinned wrapper instead:\n  ./scripts/ui/adb '"$first_arg"' …\n\nThe wrapper reads .emulator-serial and pins ANDROID_SERIAL. If no emulator is claimed yet, run ./scripts/emulator/acquire first."'
                ;;
        esac
    fi
fi

# ── gradle installDebug: installs to every device ──────────────────────────
if echo "$cmd" | grep -qE 'gradlew[^|;&]*\binstall(Debug|Release)\b'; then
    deny '"`./gradlew installDebug` installs to EVERY connected device and clobbers sibling worktrees.\n\nUse the worktree-pinned installer:\n  ./scripts/install-app.sh\n\nIt builds assembleDebug and installs only to this worktree'\''s emulator."'
fi

exit 0
