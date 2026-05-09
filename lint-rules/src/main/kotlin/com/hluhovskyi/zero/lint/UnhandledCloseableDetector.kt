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

class UnhandledCloseableDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val returnType = node.returnType as? PsiClassType ?: return
            val psiClass = returnType.resolve() ?: return
            if (!context.evaluator.implementsInterface(psiClass, "java.io.Closeable", false)) return

            // Walk up through any wrapping parentheses to find the real containing element.
            var nodeInBlock: UExpression = node
            var parent: UElement? = node.uastParent
            while (parent is UParenthesizedExpression) {
                nodeInBlock = parent
                parent = parent.uastParent
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
