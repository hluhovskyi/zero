#!/usr/bin/env bash
# Unit tests for scripts/agent/watch-prs.sh
# Mocks `gh`, spawn-*.sh, and git so the dispatcher logic can be exercised hermetically.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/../watch-prs.sh"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
assert_contains() {
  local name="$1" needle="$2" haystack="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $name"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $name — did not find '$needle' in output:"
    printf '%s\n' "$haystack" | sed 's/^/    /'
  fi
}

# Mock binaries on PATH. Exported so subprocesses inherit them.
MOCK_BIN="$TMP/bin"
mkdir -p "$MOCK_BIN"
export GH_LOG="$TMP/gh.log"
export SPAWN_LOG="$TMP/spawn.log"
export GIT_LOG="$TMP/git.log"

# Default events fixture: agent-merge labeled by hluhovskyi. Most tests can use
# this; override per-test to exercise the stranger-rejected path.
DEFAULT_EVENTS="$TMP/events-default-me.json"
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"hluhovskyi"}}]' >"$DEFAULT_EVENTS"
export GH_EVENTS_FIXTURE="$DEFAULT_EVENTS"

# `gh` mock: dispatches based on argv. Reads fixture JSON via env var GH_LIST_FIXTURE.
# For `gh api repos/.../issues/<N>/events` the mock returns the contents of
# $GH_EVENTS_FIXTURE if set, so we can test the agent-merge actor check.
cat >"$MOCK_BIN/gh" <<'GH'
#!/usr/bin/env bash
echo "$@" >>"$GH_LOG"
case "$1 $2" in
  "pr list")
    if [[ "$*" == *"--state merged"* ]]; then
      echo "[]"
    else
      cat "${GH_LIST_FIXTURE:-/dev/null}"
    fi
    ;;
  "repo view")
    echo "hluhovskyi/zero"
    ;;
  "api "*)
    # `gh api repos/.../issues/<N>/events` → return events fixture if any
    if [[ "$*" == *"/events"* ]]; then
      cat "${GH_EVENTS_FIXTURE:-/dev/null}"
    else
      echo "[]"
    fi
    ;;
  "pr ready"|"pr edit"|"pr comment"|"pr merge"|"issue edit"|"issue comment")
    exit 0
    ;;
  "run view")
    echo "(mocked CI log)"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
GH

# `git` mock: passes-through real git for read commands, but no-ops anything
# that touches remotes or the network (fetch/merge/push) so the watcher's
# inline rebase path doesn't fail on the fake worktree.
cat >"$MOCK_BIN/git" <<'GIT'
#!/usr/bin/env bash
echo "$@" >>"$GIT_LOG"
case "$1" in
  worktree)
    case "$2" in
      add|remove|prune) exit 0 ;;
    esac
    ;;
  branch|fetch|push)
    exit 0
    ;;
  merge)
    # No-op merge so behind-clean tests can succeed deterministically.
    exit 0
    ;;
esac
# fall through to real git for rev-parse / status / etc.
/usr/bin/env -i HOME="$HOME" PATH=/usr/bin:/bin /usr/bin/git "$@" 2>/dev/null || true
GIT

# `spawn-*.sh` mocks read SPAWN_EXIT_<NAME_UNDERSCORED> from env to choose exit code.
# Bash variable names can't have hyphens, so translate them to underscores.
for s in spawn-rebase-session.sh spawn-fix-session.sh spawn-verify-session.sh; do
  var="SPAWN_EXIT_$(echo "${s%.sh}" | tr - _)"
  cat >"$MOCK_BIN/$s" <<SP
#!/usr/bin/env bash
echo "$s \$@" >>"\$SPAWN_LOG"
exit \${$var:-0}
SP
done
chmod +x "$MOCK_BIN"/*

# Mock `install-pre-push-hook.sh` (the watcher calls it on new worktrees).
cat >"$MOCK_BIN/install-pre-push-hook.sh" <<'IPP'
#!/usr/bin/env bash
exit 0
IPP
chmod +x "$MOCK_BIN/install-pre-push-hook.sh"

# Symlink the spawn-* mocks into the script dir so the watcher's $SCRIPT_DIR/spawn-*.sh hits them.
WATCHER_TEST_DIR="$TMP/agent-scripts"
mkdir -p "$WATCHER_TEST_DIR"
cp "$SCRIPT" "$WATCHER_TEST_DIR/watch-prs.sh"
cp "$REPO_ROOT/scripts/agent/pr-classify.sh" "$WATCHER_TEST_DIR/pr-classify.sh"
for s in spawn-rebase-session.sh spawn-fix-session.sh spawn-verify-session.sh install-pre-push-hook.sh; do
  ln -sf "$MOCK_BIN/$s" "$WATCHER_TEST_DIR/$s"
done

# Override REPO_ROOT so .agent-state lives in TMP.
FAKE_REPO="$TMP/repo"
mkdir -p "$FAKE_REPO/.claude/worktrees"
# Make the FAKE_REPO look enough like a git repo for `git rev-parse --git-dir` if needed.
git init -q "$FAKE_REPO"

# Move our agent-scripts dir to look like FAKE_REPO/scripts/agent/
mkdir -p "$FAKE_REPO/scripts/agent"
cp "$WATCHER_TEST_DIR/watch-prs.sh" "$FAKE_REPO/scripts/agent/watch-prs.sh"
cp "$WATCHER_TEST_DIR/pr-classify.sh" "$FAKE_REPO/scripts/agent/pr-classify.sh"
for s in spawn-rebase-session.sh spawn-fix-session.sh spawn-verify-session.sh install-pre-push-hook.sh; do
  ln -sf "$MOCK_BIN/$s" "$FAKE_REPO/scripts/agent/$s"
done
chmod +x "$FAKE_REPO/scripts/agent/"*.sh

run_watcher() {
  local fixture="$1"
  PATH="$MOCK_BIN:$PATH" \
    GH_LIST_FIXTURE="$fixture" \
    GH_EVENTS_FIXTURE="${GH_EVENTS_FIXTURE:-}" \
    AGENT_WATCH_USER="hluhovskyi" \
    AGENT_WATCH_REPO="hluhovskyi/zero" \
    bash "$FAKE_REPO/scripts/agent/watch-prs.sh" 2>&1
}

# Empty list → no-op
echo "[]" >"$TMP/list-empty.json"
out="$(run_watcher "$TMP/list-empty.json")"
assert_contains "empty list → no approved PRs" "no approved PRs" "$out"

# One PR, no approval → invisible
echo '[{"number":101,"title":"x","headRefName":"issue-101","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-noapproval.json"
out="$(run_watcher "$TMP/list-noapproval.json")"
assert_contains "unapproved PR invisible" "no approved PRs" "$out"

# Approved + behind-clean → rebase path attempted
echo '[{"number":102,"title":"x","headRefName":"issue-102","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"BEHIND","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-behindclean.json"
out="$(run_watcher "$TMP/list-behindclean.json")"
assert_contains "behind-clean state classified" "state: behind-clean" "$out"

# Approved + ready-to-merge (doc-only) → ready+auto-merge
echo '[{"number":103,"title":"x","headRefName":"issue-103","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"docs/foo.md"}]}]' >"$TMP/list-readymerge.json"
out="$(run_watcher "$TMP/list-readymerge.json")"
assert_contains "doc-only → ready-to-merge" "state: ready-to-merge" "$out"
assert_contains "auto-merge enabled" "ready+auto-merge enabled" "$out"

# Approved + needs-verify → spawn-verify called
echo '[{"number":104,"title":"x","headRefName":"issue-104","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-verify.json"
: >"$SPAWN_LOG"
out="$(run_watcher "$TMP/list-verify.json")"
assert_contains "needs-verify state" "state: needs-verify" "$out"
assert_contains "spawn-verify-session called" "spawn-verify-session.sh 104" "$(cat "$SPAWN_LOG")"

# needs-verify with exit 75 (emu busy) → no failure recorded
# Use a different PR number to avoid state collision with the prior verify test.
echo '[{"number":204,"title":"x","headRefName":"issue-204","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-verify-busy.json"
rm -f "$FAKE_REPO/.agent-state/pr-204.verified"
: >"$SPAWN_LOG"
export SPAWN_EXIT_spawn_verify_session=75
out="$(run_watcher "$TMP/list-verify-busy.json")"
unset SPAWN_EXIT_spawn_verify_session
assert_contains "exit 75 → emu-busy" "emu-busy" "$out"

# Approved + CI failing → spawn-fix called
echo '[{"number":105,"title":"x","headRefName":"issue-105","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"FAILURE","detailsUrl":"https://github.com/x/x/actions/runs/12345/job/y"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-cifail.json"
: >"$SPAWN_LOG"
out="$(run_watcher "$TMP/list-cifail.json")"
assert_contains "ci-failing state" "state: ci-failing" "$out"
assert_contains "spawn-fix-session called" "spawn-fix-session.sh 105" "$(cat "$SPAWN_LOG")"

# Approved + stale (old DIRTY) → stale_pr
echo '[{"number":106,"title":"x","headRefName":"issue-106","headRefOid":"sha","createdAt":"2020-01-01T00:00:00Z","mergeStateStatus":"DIRTY","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-stale.json"
out="$(run_watcher "$TMP/list-stale.json")"
assert_contains "stale state" "state: stale" "$out"
assert_contains "agent-stale label invoked" "agent-stale" "$out"

# Approved + behind-dirty (fresh) → spawn-rebase called
echo '[{"number":107,"title":"x","headRefName":"issue-107","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"DIRTY","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-behinddirty.json"
: >"$SPAWN_LOG"
out="$(run_watcher "$TMP/list-behinddirty.json")"
assert_contains "behind-dirty state" "state: behind-dirty" "$out"
assert_contains "spawn-rebase called" "spawn-rebase-session.sh 107" "$(cat "$SPAWN_LOG")"

# Reviewer-approved AT HEAD (no label) → also visible
echo '[{"number":108,"title":"x","headRefName":"issue-108","headRefOid":"sha108","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[],"reviews":[{"state":"APPROVED","author":{"login":"hluhovskyi"},"commit_id":"sha108","submitted_at":"2026-06-02T12:00:00Z"}],"files":[{"path":"docs/x.md"}]}]' >"$TMP/list-review.json"
out="$(run_watcher "$TMP/list-review.json")"
assert_contains "APPROVED review at HEAD counts as gate" "state: ready-to-merge" "$out"

# Reviewer-approved AT HEAD review needs a submitted_at (latest-wins).
# Re-write the visible review fixture above to match the new schema.

# Reviewer-approved at STALE sha (after watcher pushed) → invisible
echo '[{"number":109,"title":"x","headRefName":"issue-109","headRefOid":"newsha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[],"reviews":[{"state":"APPROVED","author":{"login":"hluhovskyi"},"commit_id":"oldsha","submitted_at":"2026-06-02T10:00:00Z"}],"files":[{"path":"docs/x.md"}]}]' >"$TMP/list-stalereview.json"
out="$(run_watcher "$TMP/list-stalereview.json")"
assert_contains "stale APPROVED review is invisible" "no approved PRs" "$out"

# APPROVED followed by REQUEST_CHANGES → latest-wins → invisible
echo '[{"number":120,"title":"x","headRefName":"issue-120","headRefOid":"sha120","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[],"reviews":[{"state":"APPROVED","author":{"login":"hluhovskyi"},"commit_id":"sha120","submitted_at":"2026-06-02T10:00:00Z"},{"state":"REQUEST_CHANGES","author":{"login":"hluhovskyi"},"commit_id":"sha120","submitted_at":"2026-06-02T11:00:00Z"}],"files":[{"path":"a.kt"}]}]' >"$TMP/list-withdrawn.json"
out="$(run_watcher "$TMP/list-withdrawn.json")"
assert_contains "withdrawn approval is invisible" "no approved PRs" "$out"

# Root CLAUDE.md change → approved + green CI but NOT doc-only → needs-verify, not ready-to-merge
echo '[{"number":121,"title":"x","headRefName":"issue-121","headRefOid":"sha121","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"CLAUDE.md"}]}]' >"$TMP/list-claudemd.json"
: >"$SPAWN_LOG"
out="$(run_watcher "$TMP/list-claudemd.json")"
assert_contains "CLAUDE.md change is NOT doc-only → needs-verify" "state: needs-verify" "$out"

# agent-merge label applied by stranger → events API returns stranger; watcher skips
echo '[{"number":110,"title":"x","headRefName":"issue-110","headRefOid":"sha110","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"docs/x.md"}]}]' >"$TMP/list-stranger.json"
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"someone-else"}}]' >"$TMP/events-stranger.json"
export GH_EVENTS_FIXTURE="$TMP/events-stranger.json"
out="$(run_watcher "$TMP/list-stranger.json")"
unset GH_EVENTS_FIXTURE
assert_contains "stranger-applied label rejected" "was not applied by hluhovskyi" "$out"

# agent-merge label applied by hluhovskyi → events API returns me; watcher proceeds
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"hluhovskyi"}}]' >"$TMP/events-me.json"
echo '[{"number":111,"title":"x","headRefName":"issue-111","headRefOid":"sha111","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"docs/x.md"}]}]' >"$TMP/list-me-applied.json"
export GH_EVENTS_FIXTURE="$TMP/events-me.json"
: >"$GH_LOG"
out="$(run_watcher "$TMP/list-me-applied.json")"
unset GH_EVENTS_FIXTURE
assert_contains "me-applied label accepted → ready-to-merge" "state: ready-to-merge" "$out"

# Successful behind-clean rebase → revoke_label_after_push fires (gh pr edit --remove-label agent-merge).
# behind-clean uses inline cd+git so we need a real-ish worktree dir.
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"hluhovskyi"}}]' >"$TMP/events-me.json"
echo '[{"number":112,"title":"x","headRefName":"issue-112","headRefOid":"sha112","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"BEHIND","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-behind-revoke.json"
WORKTREE_112="$FAKE_REPO/.claude/worktrees/issue-112"
git init -q "$WORKTREE_112"
export GH_EVENTS_FIXTURE="$TMP/events-me.json"
: >"$GH_LOG"
out="$(run_watcher "$TMP/list-behind-revoke.json")"
unset GH_EVENTS_FIXTURE
# behind-clean attempts inline git fetch+merge+push; the git mock no-ops, so it claims rebased.
# Either way the watcher should have ATTEMPTED to remove the label.
if grep -q "pr edit 112 .*--remove-label agent-merge" "$GH_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ label revoked after successful watcher push"
else
  FAIL=$((FAIL + 1)); echo "  ✗ label NOT revoked after push (gh log follows)"
  cat "$GH_LOG" | sed 's/^/    /'
fi

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
