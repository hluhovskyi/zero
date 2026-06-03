#!/usr/bin/env bash
# Consolidated tests for scripts/agent/spawn-{fix,rebase,verify}-session.sh.
# All three wrappers share the same shape (cd $worktree → claude -p → propagate
# exit). Shared behavior is tested once across all three via a loop; only
# wrapper-specific behavior is asserted separately.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

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

# Stub claude: records argv, optionally commits, exits with $STUB_EXIT.
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
  local script="$1"
  case "$(basename "$script")" in
    spawn-fix-session.sh)    bash "$script" 999 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1 ;;
    spawn-rebase-session.sh) bash "$script" 999 "$WORKTREE"           >/dev/null 2>&1 ;;
    spawn-verify-session.sh) bash "$script" 999 "$WORKTREE"           >/dev/null 2>&1 ;;
  esac
}

# Denies expected in every wrapper. These are what makes it impossible for a
# runaway sub-session to forge the merge gate (gh pr review / gh api /
# gh pr merge) or scribble outside the worktree (rm -rf).
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

WRAPPERS=(
  "$REPO_ROOT/scripts/agent/spawn-fix-session.sh"
  "$REPO_ROOT/scripts/agent/spawn-rebase-session.sh"
  "$REPO_ROOT/scripts/agent/spawn-verify-session.sh"
)

# Shared behavior: one universal sweep per wrapper. Each loop iteration is
# ONE pass/fail assertion that covers (a) argv is correctly assembled,
# (b) `--` terminates the variadic deny list before the prompt, and
# (c) every universal deny appears in the argv. Divergence in any wrapper's
# deny list shows up here.
echo "=== universal: argv + universal denies (one assertion per wrapper) ==="
for script in "${WRAPPERS[@]}"; do
  name="$(basename "$script" .sh)"
  : >"$ARGV_LOG"
  CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 invoke "$script"

  ok=1
  grep -qE -- "--disallowedTools.*-- /" "$ARGV_LOG" || ok=0
  grep -q -- "--no-session-persistence" "$ARGV_LOG" || ok=0
  for deny in "${UNIVERSAL_DENIES[@]}"; do
    grep -q -- "$deny" "$ARGV_LOG" || { ok=0; echo "    (missing: $deny)"; }
  done
  if [[ "$ok" -eq 1 ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $name: sandbox flags + universal denies + -- separator"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $name: sandbox/deny/separator regression (see above)"
  fi
done

# Exit-code propagation + input validation: identical pattern across wrappers.
# Verify once on a representative (spawn-fix) — divergence would also show up
# in the per-wrapper-specific section below.
echo "=== universal: exit-code + input validation (representative: spawn-fix) ==="
REP="$REPO_ROOT/scripts/agent/spawn-fix-session.sh"

bash "$REP" 2>/dev/null
assert "no args → non-zero" 1 "$?"

bash "$REP" 999 /nonexistent "$CI_LOG" >/dev/null 2>&1
assert "missing worktree → exit 1" 1 "$?"

bash "$REP" 999 "$WORKTREE" /nonexistent.log >/dev/null 2>&1
assert "missing ci-log → exit 1" 1 "$?"

CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 invoke "$REP"
assert "claude exit 0 + commit → exit 0" 0 "$?"

CLAUDE_BIN="$STUB" STUB_EXIT=1 STUB_COMMIT=0 invoke "$REP"
assert "claude exit 1 → exit 1" 1 "$?"

echo "=== wrapper-specific ==="

# spawn-fix + spawn-rebase: "claim success but no commit" → exit 1
# (guards against the model claiming success without producing a commit).
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-fix-session.sh" 999 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
assert "spawn-fix: claim+no commit → exit 1" 1 "$?"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-rebase-session.sh" 999 "$WORKTREE" >/dev/null 2>&1
assert "spawn-rebase: claim+no commit → exit 1" 1 "$?"

# spawn-rebase: exit 2 propagated unchanged (watcher → agent-stale).
CLAUDE_BIN="$STUB" STUB_EXIT=2 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-rebase-session.sh" 999 "$WORKTREE" >/dev/null 2>&1
assert "spawn-rebase: exit 2 propagated" 2 "$?"

# spawn-verify: exits 2 (bug present) and 75 (emu busy) must reach the watcher.
CLAUDE_BIN="$STUB" STUB_EXIT=2 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-verify-session.sh" 999 "$WORKTREE" >/dev/null 2>&1
assert "spawn-verify: exit 2 propagated" 2 "$?"
CLAUDE_BIN="$STUB" STUB_EXIT=75 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-verify-session.sh" 999 "$WORKTREE" >/dev/null 2>&1
assert "spawn-verify: exit 75 propagated" 75 "$?"

# spawn-verify must block ALL git push + git commit (verify is observation only,
# unlike fix/rebase which need to push their commit).
: >"$ARGV_LOG"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=0 \
  bash "$REPO_ROOT/scripts/agent/spawn-verify-session.sh" 999 "$WORKTREE" >/dev/null 2>&1
if grep -q "Bash(git push" "$ARGV_LOG" && grep -q "Bash(git commit" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ spawn-verify: blocks git push + git commit"
else
  FAIL=$((FAIL + 1)); echo "  ✗ spawn-verify: push/commit NOT blocked"
fi

# spawn-fix/rebase must block force-push but allow normal push.
: >"$ARGV_LOG"
CLAUDE_BIN="$STUB" STUB_EXIT=0 STUB_COMMIT=1 \
  bash "$REPO_ROOT/scripts/agent/spawn-fix-session.sh" 999 "$WORKTREE" "$CI_LOG" >/dev/null 2>&1
if grep -q "Bash(git push --force" "$ARGV_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ spawn-fix: blocks git push --force*"
else
  FAIL=$((FAIL + 1)); echo "  ✗ spawn-fix: force-push NOT blocked"
fi

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
