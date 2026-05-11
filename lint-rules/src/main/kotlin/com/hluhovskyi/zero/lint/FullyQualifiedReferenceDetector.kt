package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiPackage
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable

class FullyQualifiedReferenceDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            // Only check the outermost reference in a chain — skip inner package segments.
            val parent = node.uastParent
            if (parent is UQualifiedReferenceExpression && parent.receiver == node) return

            // Walk to the leftmost (innermost) receiver.
            var leftmost = node.receiver
            while (leftmost is UQualifiedReferenceExpression) {
                leftmost = leftmost.receiver
            }

            // FQN if the leftmost identifier resolves to a package rather than a value.
            if ((leftmost as? UResolvable)?.resolve() is PsiPackage) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid fully-qualified class references — add an `import` statement instead.",
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "FullyQualifiedReference",
                briefDescription = "Avoid fully-qualified class references",
                explanation =
                "Using a fully-qualified class name instead of an `import` statement clutters " +
                    "the code, bypasses IDE import resolution, and makes diffs harder to read. " +
                    "Add an `import` at the top of the file and use the short class name instead. " +
                    "This pattern is commonly introduced by code-generation tools that inline the " +
                    "full package path to avoid adding a new import.",
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                Implementation(
                    FullyQualifiedReferenceDetector::class.java,
                    Scope.JAVA_FILE_SCOPE,
                ),
            )
    }
}
