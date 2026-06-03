#!/usr/bin/env bash
# Create / update the agent-* labels on the current repo.
set -euo pipefail

# Issue-lifecycle (driven by /agent-poll + /agent-do)
gh label create agent-approved    --color 0e8a16 --description "Issue eligible for agent pickup"            --force
gh label create agent-in-progress --color fbca04 --description "Agent is currently running this issue"      --force
gh label create agent-completed   --color cccccc --description "Agent opened a draft PR for this issue"     --force
gh label create agent-blocked     --color d93f0b --description "Agent run failed in a recoverable way"      --force
gh label create agent-error       --color b60205 --description "Agent run crashed unexpectedly"             --force

# PR-watcher gate
gh label create agent-merge       --color 1d76db --description "Approved for watcher to ship (rebase/verify/merge)" --force

echo "all 6 agent-* labels created/updated"
