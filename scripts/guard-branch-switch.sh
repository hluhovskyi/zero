#!/usr/bin/env bash
# Hook: block any branch-switch Bash command when running in the main worktree.
# Called by the PreToolUse/Bash hook in .claude/settings.json.
# Exits 0 silently if the command is safe; prints block JSON if it should be denied.

input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$input")

# ── 1. Classify the command ──────────────────────────────────────────────────

is_branch_op=0

# gh pr checkout <number|url>
echo "$cmd" | grep -qE '(^|[|;][[:space:]]*)gh pr checkout' && is_branch_op=1

# git switch (always operates on branches, with or without -c/--create)
echo "$cmd" | grep -qE '(^|[|;][[:space:]]*)git switch\b' && is_branch_op=1

# git checkout that looks like a branch switch:
#   git checkout <branch>        → branch switch
#   git checkout -b <branch>     → create + switch
#   git checkout -B <branch>     → force-create + switch
# But NOT:
#   git checkout -- <file>       → file restore (explicit --)
#   git checkout .               → restore working tree
#   git checkout HEAD -- <file>  → restore file from commit
if echo "$cmd" | grep -qE '(^|[|;][[:space:]]*)git checkout\b'; then
  if ! echo "$cmd" | grep -qE 'git checkout[^|;]*--(\s|$)' && \
     ! echo "$cmd" | grep -qE 'git checkout\s+\.'; then
    is_branch_op=1
  fi
fi

[ "$is_branch_op" -eq 0 ] && exit 0

# ── 2. Check if already in a worktree ───────────────────────────────────────

git_dir=$(git rev-parse --git-dir 2>/dev/null) || exit 0
common_dir=$(git rev-parse --git-common-dir 2>/dev/null) || exit 0

# In a worktree, git-dir differs from the common dir; allow branch ops there.
[ "$git_dir" != "$common_dir" ] && exit 0

# ── 3. Block — we're in the main workspace ───────────────────────────────────

printf '%s\n' '{"continue":false,"stopReason":"Branch switch blocked: you are in the main workspace (master). Use '\''git worktree add'\'' or the using-git-worktrees skill to create an isolated worktree first. Never switch branches on master."}'
