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

class UnhandledCloseableDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val returnType = node.returnType as? PsiClassType ?: return
            val psiClass = returnType.resolve() ?: return
            if (!context.evaluator.implementsInterface(psiClass, "java.io.Closeable", false)) return

            // Walk up through wrappers to find the expression that lives directly in a block.
            // For parentheses: always walk through.
            // For qualified references (obj.open()): walk through only when we are the SELECTOR —
            // if we are the RECEIVER of the next call, the result is being used (e.g. stream.read())
            // and must not be flagged.
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
                    // Method body: discarded unless the method itself returns Closeable.
                    is UMethod -> {
                        val methodReturn = (blockParent.returnType as? PsiClassType)?.resolve()
                        if (methodReturn != null &&
                            context.evaluator.implementsInterface(methodReturn, "java.io.Closeable", false)
                        ) {
                            return
                        }
                    }
                    // If/when/try branch — the value feeds into the outer expression which
                    // may itself be assigned or returned. Too complex to trace; assume used.
                    else -> return
                }
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Return value of `${node.methodName}` implements `Closeable` and must not be " +
                    "discarded — assign it and call `close()` when done.",
            )
        }
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "UnhandledCloseable",
                briefDescription = "Closeable return value must not be discarded",
                explanation =
                "A call whose return type implements `Closeable` was used as a statement, so the " +
                    "returned resource will never be closed. Assign the value and close it explicitly, " +
                    "or use it inside a `use { }` block. If the discard is intentional, suppress with " +
                    "`@SuppressLint(\"UnhandledCloseable\")`.",
                category = Category.CORRECTNESS,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                Implementation(
                    UnhandledCloseableDetector::class.java,
                    Scope.JAVA_FILE_SCOPE,
                ),
            )
    }
}
