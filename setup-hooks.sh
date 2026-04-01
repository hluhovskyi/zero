#!/bin/bash

# Configure git to use the tracked .githooks directory
git config core.hooksPath .githooks

echo "Git hooks configured from .githooks/"
chmod +x .githooks/pre-commit .githooks/pre-push
echo "Hooks are now active."
