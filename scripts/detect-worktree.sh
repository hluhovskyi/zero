#!/usr/bin/env bash
# Outputs worktree isolation state. Used by using-git-worktrees skill.
# Prints: GIT_DIR, GIT_COMMON, IS_WORKTREE (yes/no), IS_SUBMODULE (yes/no), BRANCH
set -euo pipefail

GIT_DIR=$(git rev-parse --git-dir 2>/dev/null)
GIT_DIR=$(cd "$GIT_DIR" && pwd -P)

GIT_COMMON=$(git rev-parse --git-common-dir 2>/dev/null)
GIT_COMMON=$(cd "$GIT_COMMON" && pwd -P)

SUPERPROJECT=$(git rev-parse --show-superproject-working-tree 2>/dev/null || true)
IS_SUBMODULE=$([ -n "$SUPERPROJECT" ] && echo yes || echo no)

IS_WORKTREE=$([ "$GIT_DIR" != "$GIT_COMMON" ] && [ "$IS_SUBMODULE" = "no" ] && echo yes || echo no)

BRANCH=$(git branch --show-current 2>/dev/null || echo "")

echo "GIT_DIR=$GIT_DIR"
echo "GIT_COMMON=$GIT_COMMON"
echo "IS_WORKTREE=$IS_WORKTREE"
echo "IS_SUBMODULE=$IS_SUBMODULE"
echo "BRANCH=$BRANCH"
