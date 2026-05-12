package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class HardcodedComposableStringDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (!isInsideComposable(node)) return

            if (node.methodName == "Text") {
                checkLiteral(context, node.findTextArg())
            }

            checkLiteral(context, node.findNamedArg("contentDescription"))
        }
    }

    private fun isInsideComposable(node: UElement): Boolean {
        var psiParent = node.sourcePsi?.parent
        while (psiParent != null) {
            if (psiParent is KtNamedFunction) {
                return psiParent.annotationEntries.any { it.shortName?.asString() == "Composable" }
            }
            psiParent = psiParent.parent
        }
        return false
    }

    /**
     * Find the `text` argument of a Text() call. Tries the named parameter first via
     * UAST resolution (handles Text(text = "…") and out-of-order args), then falls back
     * to the first positional argument for the common Text("…") pattern.
     */
    private fun UCallExpression.findTextArg(): UExpression? = findNamedArg("text") ?: valueArguments.firstOrNull()

    /**
     * Find a named argument by parameter name using UAST resolution.
     * Returns null when the call cannot be resolved (e.g. unresolved library stub).
     */
    private fun UCallExpression.findNamedArg(paramName: String): UExpression? {
        val method = resolve() ?: return null
        val paramIndex = method.parameterList.parameters.indexOfFirst { it.name == paramName }
        if (paramIndex < 0) return null
        return getArgumentForParameter(paramIndex)
    }

    private fun checkLiteral(context: JavaContext, expr: UExpression?) {
        if (expr == null) return
        val value = ConstantEvaluator.evaluateString(context, expr, false) ?: return
        if (isExcluded(value)) return
        context.report(ISSUE, expr, context.getLocation(expr), MESSAGE)
    }

    private fun isExcluded(value: String): Boolean =
        value.isEmpty() || value.length == 1 || value.all { it.isDigit() }

    companion object {
        private const val MESSAGE =
            "Hardcoded string in @Composable — use `stringResource()` instead."

        val ISSUE: Issue = Issue.create(
            id = "HardcodedComposableString",
            briefDescription = "Hardcoded string in @Composable",
            explanation =
            "String literals in Composable functions should come from string resources " +
                "so the app can be localized. Use `stringResource(R.string.xxx)` instead of a " +
                "hardcoded literal.",
            category = Category.I18N,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                HardcodedComposableStringDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
