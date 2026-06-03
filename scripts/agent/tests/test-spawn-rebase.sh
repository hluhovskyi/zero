#!/usr/bin/env bash
# Unit tests for scripts/agent/spawn-rebase-session.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/../spawn-rebase-session.sh"

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

WORKTREE="$TMP/wt"
git init -q "$WORKTREE"
git -C "$WORKTREE" commit --allow-empty -q -m "initial"

STUB_DIR="$TMP/bin"
mkdir -p "$STUB_DIR"
ARGV_LOG="$TMP/claude.argv"

cat >"$STUB_DIR/claude-commit" <<STUB
#!/usr/bin/env bash
echo "\$@" >>"$ARGV_LOG"
cd "$WORKTREE"
echo "resolved" >resolution.txt
git add resolution.txt
git -c user.email=stub@test -c user.name=stub commit -q -m "stub resolution"
exit 0
STUB
cat >"$STUB_DIR/claude-exit2" <<STUB
#!/usr/bin/env bash
echo "too structural" >&2
exit 2
STUB
cat >"$STUB_DIR/claude-empty" <<STUB
#!/usr/bin/env bash
echo "no progress"
exit 0
STUB
chmod +x "$STUB_DIR"/claude-*

echo "=== happy path ==="
CLAUDE_BIN="$STUB_DIR/claude-commit" bash "$SCRIPT" 555 "$WORKTREE" >/dev/null 2>&1
assert "resolved + commit → exit 0" "0" "$?"

echo "=== too-structural ==="
CLAUDE_BIN="$STUB_DIR/claude-exit2" bash "$SCRIPT" 555 "$WORKTREE" >/dev/null 2>&1
assert "exit 2 propagated (watcher → agent-stale)" "2" "$?"

echo "=== success-claimed-but-no-commit ==="
CLAUDE_BIN="$STUB_DIR/claude-empty" bash "$SCRIPT" 555 "$WORKTREE" >/dev/null 2>&1
assert "no HEAD advance → exit 1" "1" "$?"

echo "=== argv to claude ==="
: >"$ARGV_LOG"
CLAUDE_BIN="$STUB_DIR/claude-commit" bash "$SCRIPT" 888 "$WORKTREE" >/dev/null 2>&1
if grep -q "/agent-pr-rebase --pr 888" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ slash command + pr passed"
else
  FAIL=$((FAIL + 1)); echo "  ✗ slash command missing"
fi
if grep -qE -- "--disallowedTools.*-- /agent-pr-rebase" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ -- separator before prompt"
else
  FAIL=$((FAIL + 1)); echo "  ✗ -- separator missing"
fi
# `git push --force*` MUST be blocked.
if grep -q "Bash(git push --force" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ force-push blocked"
else
  FAIL=$((FAIL + 1)); echo "  ✗ force-push NOT blocked"
fi

echo "=== input validation ==="
bash "$SCRIPT" 2>/dev/null
assert "no args → non-zero" "1" "$?"
bash "$SCRIPT" 1 /nonexistent >/dev/null 2>&1
assert "missing worktree → exit 1" "1" "$?"

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
