# Execution Workflow

Plans are saved to `docs/superpowers/plans/` and serve as the contract between Claude and Gemini.

## Gemini CLI Execution

After writing the implementation plan, dispatch the entire plan to Gemini CLI in a single call. Do not read Gemini's output — just wait for completion, then verify.

```bash
gemini --yolo -p "You are working on the '<branch>' branch. Execute ALL tasks from the implementation plan at docs/superpowers/plans/<plan-file>.md, in order. Read the plan first, then implement each task exactly as specified, committing after each task with the commit message from the plan. After all tasks, run './gradlew assembleDebug' and './gradlew testDebugUnitTest' to verify. If build or tests fail, fix the issues and commit the fix."
```

After Gemini finishes, Claude verifies: review the git log + diff against the spec, run build/tests if Gemini didn't, and fix any remaining issues.
