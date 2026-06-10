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
 * Bans direct `Clock.System` access (kotlinx.datetime / kotlin.time) outside the sanctioned
 * clock implementations. Production code must inject `com.hluhovskyi.zero.common.time.Clock`
 * so tests can pin time. See docs/agents/code-style.md § Date / Time.
 */
class DirectClockUsageDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            val path = context.file.path.replace('\\', '/')
            if (ALLOWED_PATH_FRAGMENTS.any { it in path }) return

            val source = node.asSourceString().replace(" ", "")
            if (source != "Clock.System" && !source.endsWith(".Clock.System")) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Don't read `Clock.System` directly — inject " +
                    "`com.hluhovskyi.zero.common.time.Clock` instead so tests can pin time.",
            )
        }
    }

    companion object {
        /** Clock implementations and the e2e test seam are the only sanctioned readers. */
        private val ALLOWED_PATH_FRAGMENTS = listOf(
            "/common/time/",
            "/testbridge/",
        )

        val ISSUE: Issue = Issue.create(
            id = "DirectClockUsage",
            briefDescription = "Inject Clock instead of reading Clock.System",
            explanation = "Reading `Clock.System.now()` hard-wires wall-clock time into the " +
                "logic, making it untestable with fixed clocks. Inject " +
                "`com.hluhovskyi.zero.common.time.Clock` (already provided on every component " +
                "graph) and call `clock.now()`. Only the Clock implementations under " +
                "`common/time/` and the e2e test bridge may touch `Clock.System`.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                DirectClockUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
