# Execution Workflow

Plans are saved to `docs/superpowers/plans/` and serve as the contract between Claude and Gemini.

## Gemini CLI Execution

After writing the implementation plan, dispatch the entire plan to Gemini CLI in a single call. Do not read Gemini's output — just wait for completion, then verify.

```bash
gemini --yolo -p "You are working on the '<branch>' branch. Execute ALL tasks from the implementation plan at docs/superpowers/plans/<plan-file>.md, in order. Read the plan first, then implement each task exactly as specified, committing after each task with the commit message from the plan. After all tasks, run verifications as specified in the plan (including UI inspection if instructed), then run './gradlew assembleDebug', './gradlew lintDebug', and './gradlew testDebugUnitTest' as a final safety check. If build, lint, or tests fail, fix the issues and commit the fix. Finally, push the branch and open a PR with a summary of changes."
```

After Gemini finishes, Claude reviews the PR and rates Gemini's work.

## UI Verification — Mandatory Loop

**After every UI change, verify before claiming it works.** No exceptions.

```bash
./gradlew installDebug                          # build + install
adb shell input keyevent 111                    # dismiss keyboard if open
./scripts/dump-ui.sh                            # XML hierarchy — check bounds
adb exec-out screencap -p > /tmp/screen.png    # visual snapshot
```

Read `/tmp/screen.png` with the image tool. If the expected composable is missing or has zero-width/height bounds, the fix did not work — do not report success.

## Complexity Circuit Breaker

**The "Hacky Code" Circuit Breaker:** If you iterate on a fix more than twice and your solution requires dropping down to low-level framework APIs (e.g., `PointerEventPass.Initial`, Reflection, or Global Registries) for a common UI or logic problem, you must STOP. Revert your changes and present the fundamental constraint to the user before proceeding. Do not brute-force the framework.
