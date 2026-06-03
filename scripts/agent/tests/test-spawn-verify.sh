#!/usr/bin/env bash
# Unit tests for scripts/agent/spawn-verify-session.sh
# Uses a stub `claude` binary on PATH so the test is hermetic — no real model call.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SCRIPT="$SCRIPT_DIR/../spawn-verify-session.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
assert() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $name"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $name — expected '$expected', got '$actual'"
  fi
}

# Build a stub `claude` that records its argv and exits with the requested code.
STUB_DIR="$TMP/bin"
mkdir -p "$STUB_DIR"
ARGV_LOG="$TMP/claude.argv"

cat >"$STUB_DIR/claude-exit0" <<'STUB'
#!/usr/bin/env bash
echo "stub-claude-output" >&1
echo "stub-claude-stderr" >&2
exit 0
STUB
cat >"$STUB_DIR/claude-exit2" <<'STUB'
#!/usr/bin/env bash
echo "bug-still-present" >&1
exit 2
STUB
cat >"$STUB_DIR/claude-exit75" <<'STUB'
#!/usr/bin/env bash
echo "emu-busy" >&2
exit 75
STUB
cat >"$STUB_DIR/claude-record" <<STUB
#!/usr/bin/env bash
echo "\$@" >>"$ARGV_LOG"
echo "ok" >&1
exit 0
STUB
chmod +x "$STUB_DIR"/claude-*

WORKTREE="$TMP/worktree"
mkdir -p "$WORKTREE"

echo "=== exit-code propagation ==="

CLAUDE_BIN="$STUB_DIR/claude-exit0" bash "$SCRIPT" 999 "$WORKTREE" >/dev/null 2>&1
assert "exit 0 propagated" "0" "$?"

CLAUDE_BIN="$STUB_DIR/claude-exit2" bash "$SCRIPT" 999 "$WORKTREE" >/dev/null 2>&1
assert "exit 2 propagated" "2" "$?"

CLAUDE_BIN="$STUB_DIR/claude-exit75" bash "$SCRIPT" 999 "$WORKTREE" >/dev/null 2>&1
assert "exit 75 propagated" "75" "$?"

echo "=== argv to claude ==="

: >"$ARGV_LOG"
CLAUDE_BIN="$STUB_DIR/claude-record" bash "$SCRIPT" 123 "$WORKTREE" >/dev/null 2>&1

if grep -q -- "--no-session-persistence" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ --no-session-persistence passed"
else
  FAIL=$((FAIL + 1)); echo "  ✗ --no-session-persistence missing"
fi

if grep -q -- "--permission-mode acceptEdits" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ --permission-mode acceptEdits passed"
else
  FAIL=$((FAIL + 1)); echo "  ✗ --permission-mode acceptEdits missing"
fi

if grep -q "/agent-pr-verify --pr 123" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ slash command + pr number passed"
else
  FAIL=$((FAIL + 1)); echo "  ✗ slash command + pr number missing"
fi

# The variadic --disallowedTools must be followed by `--` to terminate the list
# (bug we hit on issue-driven agent before).
if grep -qE -- "--disallowedTools.*-- /agent-pr-verify" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ -- separator before prompt"
else
  FAIL=$((FAIL + 1)); echo "  ✗ -- separator missing (variadic flag would eat prompt)"
fi

echo "=== verdict/log files written ==="

VERDICT="$REPO_ROOT/.agent-state/verify-123.verdict"
LOG="$REPO_ROOT/.agent-state/verify-123.log"

# Run with exit-0 stub and check files exist
CLAUDE_BIN="$STUB_DIR/claude-exit0" bash "$SCRIPT" 123 "$WORKTREE" >/dev/null 2>&1
if [[ -f "$VERDICT" ]] && grep -q "stub-claude-output" "$VERDICT"; then
  PASS=$((PASS + 1)); echo "  ✓ stdout captured to verdict file"
else
  FAIL=$((FAIL + 1)); echo "  ✗ stdout not captured to verdict file"
fi
if [[ -f "$LOG" ]] && grep -q "stub-claude-stderr" "$LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ stderr captured to log file"
else
  FAIL=$((FAIL + 1)); echo "  ✗ stderr not captured to log file"
fi
rm -f "$VERDICT" "$LOG"

echo "=== input validation ==="

bash "$SCRIPT" 2>/dev/null
assert "missing args → non-zero" "1" "$?"

bash "$SCRIPT" 123 /nonexistent/worktree/path >/dev/null 2>&1
assert "missing worktree → exit 1" "1" "$?"

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
