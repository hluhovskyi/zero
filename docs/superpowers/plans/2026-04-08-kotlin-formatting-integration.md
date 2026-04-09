# Kotlin Formatting Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Spotless with ktlint to ensure consistent Kotlin formatting across all modules, automated via a git pre-commit hook.

**Architecture:** Apply the Spotless plugin in the root `build.gradle` to handle formatting for all Kotlin (`.kt`) and Kotlin Script (`.kts`) files. Update the pre-commit hook to automatically format staged files before commit.

**Tech Stack:** Gradle 9.3.1, Spotless 7.0.2, ktlint 1.5.0.

---

### Task 1: Add Spotless Plugin to Root `build.gradle`

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add Spotless plugin to `plugins` block**

Modify the `plugins` block in the root `build.gradle` to include Spotless.

```gradle
plugins {
    id 'com.diffplug.spotless' version '7.0.2'
    // (existing plugins)
}
```

- [ ] **Step 2: Add Spotless configuration**

Add the `spotless` configuration block at the end of the root `build.gradle`.

```gradle
spotless {
    kotlin {
        target '**/*.kt'
        targetExclude '**/build/**/*.kt'
        ktlint('1.5.0')
    }
    kotlinGradle {
        target '**/*.gradle.kts'
        ktlint('1.5.0')
    }
    format 'misc', {
        target '**/*.gradle', '**/.gitignore', '**/*.md'
        indentWithSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

- [ ] **Step 3: Verify Spotless tasks are available**

Run: `./gradlew tasks --all | grep spotless`
Expected: `spotlessApply`, `spotlessCheck`, etc. are listed.

- [ ] **Step 4: Commit**

```bash
git add build.gradle
git commit -m "build: add spotless and ktlint for kotlin formatting"
```

---

### Task 2: Integrate Spotless into Git Pre-commit Hook

**Files:**
- Modify: `.githooks/pre-commit`

- [ ] **Step 1: Update `pre-commit` hook to run Spotless**

Add a step to the `pre-commit` hook to run `spotlessApply` on staged files.

```bash
#!/bin/bash
protected_branch="master"
current_branch=$(git rev-parse --abbrev-ref HEAD)

if [ "$current_branch" = "$protected_branch" ]; then
    echo "ERROR: Direct commit to $protected_branch is not allowed."
    exit 1
fi

# Block committing build/ directories (generated artifacts)
staged_build_files=$(git diff --cached --name-only | grep -E '(^|/)build/')
if [ -n "$staged_build_files" ]; then
    echo "ERROR: Staged files include build/ artifacts:"
    echo "$staged_build_files"
    echo "Run 'git reset HEAD <file>' to unstage them."
    exit 1
fi

# Format staged files
echo "Running Spotless formatting..."
./gradlew spotlessApply

# Re-stage files that were modified by Spotless
staged_files=$(git diff --cached --name-only)
if [ -n "$staged_files" ]; then
    echo "Re-staging formatted files..."
    git add $staged_files
fi

exit 0
```

- [ ] **Step 2: Commit**

```bash
git add .githooks/pre-commit
git commit -m "scripts: run spotlessApply in pre-commit hook"
```

---

### Task 3: Test and Verify Integration

- [ ] **Step 1: Run Spotless on the whole project once**

Run: `./gradlew spotlessApply`
Expected: Project is formatted according to ktlint.

- [ ] **Step 2: Create a dummy Kotlin file with bad formatting**

```bash
cat <<EOF > app/src/main/java/com/hluhovskyi/zero/TestFormatting.kt
package com.hluhovskyi.zero

class TestFormatting {
  fun   badFormatting  ()  {
    val  x=1
  }
}
EOF
```

- [ ] **Step 3: Stage and commit the dummy file**

Run: `git add app/src/main/java/com/hluhovskyi/zero/TestFormatting.kt && git commit -m "test: verify formatting"`
Expected: Pre-commit hook runs `spotlessApply`, file is formatted, and commit succeeds.

- [ ] **Step 4: Verify the file was formatted**

Run: `cat app/src/main/java/com/hluhovskyi/zero/TestFormatting.kt`
Expected: Clean ktlint-compliant code.

- [ ] **Step 5: Cleanup**

Run: `git rm app/src/main/java/com/hluhovskyi/zero/TestFormatting.kt && git commit --amend --no-edit`
