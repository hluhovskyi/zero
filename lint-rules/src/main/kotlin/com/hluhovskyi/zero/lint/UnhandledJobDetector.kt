package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class UnhandledJobDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val returnType = node.returnType as? PsiClassType ?: return
            val psiClass = returnType.resolve() ?: return
            if (!context.evaluator.implementsInterface(psiClass, "kotlinx.coroutines.Job", false)) return

            // Implicit-receiver calls (launch { }) are structured coroutine children whose lifecycle
            // is tied to the parent scope — skip them. Only flag explicit-receiver calls
            // (scope.launch { }) where the Job escapes and must be tracked manually.
            if (node.receiver == null) return

            // Walk up through wrappers to find the expression that lives directly in a block.
            // Walk through parentheses unconditionally.
            // Walk through a qualified reference only when we are the SELECTOR — if we are the
            // RECEIVER of the next call (e.g. scope.launch { }.cancel()), the result is used.
            var nodeInBlock: UExpression = node
            var parent: UElement? = node.uastParent
            while (true) {
                when (val p = parent) {
                    is UParenthesizedExpression -> {
                        nodeInBlock = p
                        parent = p.uastParent
                    }
                    is UQualifiedReferenceExpression -> {
                        if (p.selector != nodeInBlock) break
                        nodeInBlock = p
                        parent = p.uastParent
                    }
                    else -> break
                }
            }

            val block = parent as? UBlockExpression ?: return

            // If this is the last expression in the block, check whether the block's value
            // is actually discarded. We only flag when we can be certain it is.
            if (block.expressions.lastOrNull() == nodeInBlock) {
                when (val blockParent = block.uastParent) {
                    // Lambda last expression is the lambda's return value — not discarded.
                    is ULambdaExpression -> return
                    // Method body: a top-level scope.launch as the last expression is typically a
                    // structured coroutine child managed by the parent scope — skip to avoid false
                    // positives. Non-last launches in a method body are still flagged.
                    is UMethod -> return
                    // If/when/try branch — feeds into the outer expression; assume used.
                    else -> return
                }
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Return value of `${node.methodName}` implements `Job` and must not be " +
                    "discarded — assign it and call `cancel()` when done, or use structured " +
                    "concurrency (implicit-receiver `launch { }`) instead.",
            )
        }
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "UnhandledJob",
                briefDescription = "Coroutine Job must not be discarded",
                explanation =
                "A call whose return type implements `Job` was used as a statement and the result " +
                    "discarded. The coroutine will run but can never be cancelled. Prefer structured " +
                    "concurrency (implicit-receiver `launch { }` inside a parent coroutine) so the " +
                    "job is automatically tied to the parent's lifecycle. If you must keep an explicit " +
                    "reference, assign the `Job` and cancel it when done. If the discard is intentional, " +
                    "suppress with `@SuppressLint(\"UnhandledJob\")`.",
                category = Category.CORRECTNESS,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                Implementation(
                    UnhandledJobDetector::class.java,
                    Scope.JAVA_FILE_SCOPE,
                ),
            )
    }
}
