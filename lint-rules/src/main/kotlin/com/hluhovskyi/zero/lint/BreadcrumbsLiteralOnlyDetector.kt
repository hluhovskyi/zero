package com.hluhovskyi.zero.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.skipParenthesizedExprDown

class BreadcrumbsLiteralOnlyDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("log")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingFqn = method.containingClass?.qualifiedName ?: return
        if (containingFqn != BREADCRUMBS_FQN) return
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isLiteralStringExpression(arg)) return
        context.report(ISSUE, arg, context.getLocation(arg), MESSAGE)
    }

    private fun isLiteralStringExpression(expr: UExpression): Boolean {
        val unwrapped = expr.skipParenthesizedExprDown() ?: return false
        return when (unwrapped) {
            is ULiteralExpression -> unwrapped.value is String
            is UPolyadicExpression -> unwrapped.operands.all { isLiteralStringExpression(it) }
            else -> false
        }
    }

    companion object {
        private const val BREADCRUMBS_FQN = "com.hluhovskyi.zero.feedback.Breadcrumbs"

        private const val MESSAGE =
            "Breadcrumbs.log() requires a string literal — concatenation, templates, " +
                "or variables risk leaking PII into the public feedback issue."

        val ISSUE: Issue = Issue.create(
            id = "BreadcrumbsLiteralOnly",
            briefDescription = "Breadcrumbs.log() must take a string literal",
            explanation = "Breadcrumb messages are attached to feedback reports filed as " +
                "public GitHub issues. Interpolating variables risks leaking PII (account names, " +
                "amounts, IDs). Use only inline string literals so reviewers can see the exact " +
                "message at the call site without indirection.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                BreadcrumbsLiteralOnlyDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
