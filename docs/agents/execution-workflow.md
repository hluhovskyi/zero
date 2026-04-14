# Execution Workflow

Plans are saved to `docs/superpowers/plans/` and serve as the contract between Claude and Gemini.

## Gemini CLI Execution

After writing the implementation plan, dispatch the entire plan to Gemini CLI in a single call. Do not read Gemini's output — just wait for completion, then verify.

```bash
gemini --yolo -p "You are working on the '<branch>' branch. Execute ALL tasks from the implementation plan at docs/superpowers/plans/<plan-file>.md, in order. Read the plan first, then implement each task exactly as specified, committing after each task with the commit message from the plan. After all tasks, run './gradlew assembleDebug' and './gradlew testDebugUnitTest' to verify. If build or tests fail, fix the issues and commit the fix. Finally, push the branch and open a PR with a summary of changes."
```

After Gemini finishes, Claude reviews the PR and rates Gemini's work.

## Validation

**Compilation is not Validation:** Never claim a task is complete just because `assembleDebug` passes. After creating new components, changing DI wiring, or adding new UI screens:
1. **Run Linters:** You MUST run `./gradlew lintDebug` (or the specific module's lint task, e.g., `./gradlew :zero-core:lintDebug`). The project relies on custom structural linters (e.g., `ViewProviderDependencyDetector`) that catch architectural violations the compiler ignores.
2. **Check UI:** If you modified UI or layout, you MUST verify the behavioral change via an automated test or a UI dump tool. Do not operate blind or "success chase" UI changes without evidence.

## Complexity Circuit Breaker

**The "Hacky Code" Circuit Breaker:** If you iterate on a fix more than twice and your solution requires dropping down to low-level framework APIs (e.g., `PointerEventPass.Initial`, Reflection, or Global Registries) for a common UI or logic problem, you must STOP. Revert your changes and present the fundamental constraint to the user before proceeding. Do not brute-force the framework.
