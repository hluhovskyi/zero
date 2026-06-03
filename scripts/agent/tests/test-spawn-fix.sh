#!/usr/bin/env bash
# Unit tests for scripts/agent/spawn-fix-session.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SCRIPT="$SCRIPT_DIR/../spawn-fix-session.sh"

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

# Real git worktree so the wrapper can run `git rev-parse HEAD`.
WORKTREE="$TMP/wt"
git init -q "$WORKTREE"
git -C "$WORKTREE" commit --allow-empty -q -m "initial"

CI_LOG="$TMP/ci.log"
echo "Run './gradlew :spotlessApply' to fix these violations." >"$CI_LOG"

STUB_DIR="$TMP/bin"
mkdir -p "$STUB_DIR"
ARGV_LOG="$TMP/claude.argv"

# Stub claude that creates a commit (simulates a real fix push).
cat >"$STUB_DIR/claude-commit" <<STUB
#!/usr/bin/env bash
echo "\$@" >>"$ARGV_LOG"
cd "$WORKTREE"
echo "fixed" >fix.txt
git add fix.txt
git -c user.email=stub@test -c user.name=stub commit -q -m "stub fix"
echo "ok"
exit 0
STUB
# Stub that claims success but makes no commit (caller must detect this).
cat >"$STUB_DIR/claude-empty" <<STUB
#!/usr/bin/env bash
echo "claimed-success-no-commit"
exit 0
STUB
# Stub that fails honestly.
cat >"$STUB_DIR/claude-fail" <<STUB
#!/usr/bin/env bash
echo "couldn't fix" >&2
exit 1
STUB
chmod +x "$STUB_DIR"/claude-*

echo "=== happy path ==="
CLAUDE_BIN="$STUB_DIR/claude-commit" bash "$SCRIPT" 555 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
assert "fix that commits → exit 0" "0" "$?"

echo "=== success-claimed-but-no-commit detection ==="
CLAUDE_BIN="$STUB_DIR/claude-empty" bash "$SCRIPT" 555 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
assert "claim+no commit → exit 1" "1" "$?"

echo "=== honest failure ==="
CLAUDE_BIN="$STUB_DIR/claude-fail" bash "$SCRIPT" 555 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
assert "claude exit 1 → exit 1" "1" "$?"

echo "=== argv to claude ==="
: >"$ARGV_LOG"
CLAUDE_BIN="$STUB_DIR/claude-commit" bash "$SCRIPT" 777 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
if grep -q "/agent-pr-fix --pr 777 --ci-log $CI_LOG" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ slash command + pr + ci-log passed"
else
  FAIL=$((FAIL + 1)); echo "  ✗ argv missing"
fi
if grep -qE -- "--disallowedTools.*-- /agent-pr-fix" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ -- separator before prompt"
else
  FAIL=$((FAIL + 1)); echo "  ✗ -- separator missing"
fi
# Confirm gh pr merge / ready / edit are explicitly blocked.
for guard in "gh pr ready" "gh pr merge" "gh pr edit" "gh pr close"; do
  if grep -q "Bash($guard" "$ARGV_LOG"; then
    PASS=$((PASS + 1)); echo "  ✓ $guard blocked"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $guard NOT blocked"
  fi
done

echo "=== input validation ==="
bash "$SCRIPT" 2>/dev/null
assert "no args → non-zero" "1" "$?"
bash "$SCRIPT" 1 /nonexistent "$CI_LOG" >/dev/null 2>&1
assert "missing worktree → exit 1" "1" "$?"
bash "$SCRIPT" 1 "$WORKTREE" /nonexistent.log >/dev/null 2>&1
assert "missing ci log → exit 1" "1" "$?"

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
