#!/usr/bin/env bash
# Unit tests for scripts/agent/spawn-pr-session.sh — the single unified spawn
# wrapper that handles repair-fix, repair-rebase, and verify kinds.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/agent/spawn-pr-session.sh"

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

STUB="$TMP/claude-stub"
ARGV_LOG="$TMP/claude.argv"
cat >"$STUB" <<STUB
#!/usr/bin/env bash
echo "\$@" >>"$ARGV_LOG"
if [[ "\${STUB_COMMIT:-0}" == "1" ]]; then
  cd "$WORKTREE"
  echo "x" > "stub-\$\$.txt"
  git add "stub-\$\$.txt"
  git -c user.email=stub@test -c user.name=stub commit -q -m "stub"
fi
exit "\${STUB_EXIT:-0}"
STUB
chmod +x "$STUB"

CI_LOG="$TMP/ci.log"
echo "(stub ci log)" >"$CI_LOG"

invoke() {
  local kind="$1"
  case "$kind" in
    repair-fix)    bash "$SCRIPT" repair-fix    999 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1 ;;
    repair-rebase) bash "$SCRIPT" repair-rebase 999 "$WORKTREE"           >/dev/null 2>&1 ;;
    verify)        bash "$SCRIPT" verify        999 "$WORKTREE"           >/dev/null 2>&1 ;;
  esac
}

UNIVERSAL_DENIES=(
  "Bash(gh pr ready"
  "Bash(gh pr merge"
  "Bash(gh pr edit"
  "Bash(gh pr close"
  "Bash(gh pr review"
  "Bash(gh api"
  "Bash(rm -rf /"
  "Bash(rm -rf ~"
)

echo "=== universal: argv + universal denies (one assertion per kind) ==="
for kind in repair-fix repair-rebase verify; do
  : >"$ARGV_LOG"
  CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 invoke "$kind"

  ok=1
  grep -qE -- "--disallowedTools.*-- /" "$ARGV_LOG" || ok=0
  grep -q -- "--no-session-persistence" "$ARGV_LOG" || ok=0
  for deny in "${UNIVERSAL_DENIES[@]}"; do
    grep -q -- "$deny" "$ARGV_LOG" || { ok=0; echo "    (missing: $deny)"; }
  done
  if [[ "$ok" -eq 1 ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $kind: sandbox flags + universal denies + -- separator"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $kind: sandbox/deny/separator regression (see above)"
  fi
done

echo "=== universal: exit-code + input validation (representative: repair-fix) ==="

bash "$SCRIPT" 2>/dev/null
assert "no args → non-zero" 1 "$?"

bash "$SCRIPT" repair-fix 999 /nonexistent "$CI_LOG" >/dev/null 2>&1
assert "missing worktree → exit 1" 1 "$?"

bash "$SCRIPT" repair-fix 999 "$WORKTREE" /nonexistent.log >/dev/null 2>&1
assert "missing ci-log → exit 1" 1 "$?"

bash "$SCRIPT" bogus-kind 999 "$WORKTREE" >/dev/null 2>&1
assert "unknown kind → exit 1" 1 "$?"

CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 invoke repair-fix
assert "claude exit 0 + commit → exit 0" 0 "$?"

CLAUDE_BIN="$STUB" STUB_EXIT=1 STUB_COMMIT=0 invoke repair-fix
assert "claude exit 1 → exit 1" 1 "$?"

echo "=== kind-specific ==="

# repair-fix + repair-rebase: "claim success but no commit" → exit 1.
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 invoke repair-fix
assert "repair-fix: claim+no commit → exit 1" 1 "$?"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 invoke repair-rebase
assert "repair-rebase: claim+no commit → exit 1" 1 "$?"

# repair-rebase: exit 2 propagated unchanged.
CLAUDE_BIN="$STUB" STUB_EXIT=2 STUB_COMMIT=0 invoke repair-rebase
assert "repair-rebase: exit 2 propagated" 2 "$?"

# verify: exit 2 (bug present) and 75 (emu busy) propagated; no HEAD-advance
# requirement (verify is observation-only).
CLAUDE_BIN="$STUB" STUB_EXIT=2 STUB_COMMIT=0 invoke verify
assert "verify: exit 2 propagated" 2 "$?"
CLAUDE_BIN="$STUB" STUB_EXIT=75 STUB_COMMIT=0 invoke verify
assert "verify: exit 75 propagated" 75 "$?"

# verify must block ALL git push + git commit. fix/rebase must block force only.
: >"$ARGV_LOG"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 invoke verify
if grep -q "Bash(git push" "$ARGV_LOG" && grep -q "Bash(git commit" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ verify: blocks git push + git commit"
else
  FAIL=$((FAIL + 1)); echo "  ✗ verify: push/commit NOT blocked"
fi

: >"$ARGV_LOG"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 invoke repair-fix
if grep -q "Bash(git push --force" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ repair-fix: blocks git push --force*"
else
  FAIL=$((FAIL + 1)); echo "  ✗ repair-fix: force-push NOT blocked"
fi

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
