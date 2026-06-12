package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * Bans `Dispatchers.Default` outside the `DispatcherProvider` implementation. The codebase
 * runs all background work on IO; CPU-bound work goes through `dispatchers.cpu()` so the
 * choice stays injectable. See docs/agents/concurrency.md § Dispatcher Rules.
 */
class DispatchersDefaultDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            val path = context.file.path.replace('\\', '/')
            if (ALLOWED_PATH_FRAGMENTS.any { it in path }) return

            val source = node.asSourceString().replace(" ", "")
            if (source != "Dispatchers.Default" && !source.endsWith(".Dispatchers.Default")) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Don't use `Dispatchers.Default` — use `Dispatchers.IO` (or inject " +
                    "`DispatcherProvider` and call `dispatchers.cpu()` for CPU-bound work).",
            )
        }
    }

    companion object {
        /** The DispatcherProvider implementation is the only sanctioned owner. */
        private val ALLOWED_PATH_FRAGMENTS = listOf(
            "/common/coroutines/",
        )

        val ISSUE: Issue = Issue.create(
            id = "NoDispatchersDefault",
            briefDescription = "Use Dispatchers.IO or DispatcherProvider.cpu(), not Dispatchers.Default",
            explanation = "This codebase standardizes on `Dispatchers.IO` for ViewModel/UseCase " +
                "scopes; genuinely CPU-bound work goes through the injectable " +
                "`DispatcherProvider.cpu()` seam so tests can swap it. A raw " +
                "`Dispatchers.Default` bypasses both conventions. Only the DispatcherProvider " +
                "implementation under `common/coroutines/` may reference it.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DispatchersDefaultDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
