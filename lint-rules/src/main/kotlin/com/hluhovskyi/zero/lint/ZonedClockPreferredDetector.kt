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
import org.jetbrains.uast.UMethod

class ZonedClockPreferredDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (!node.isConstructor) return

            val params = node.uastParameters
            val clockParam = params.firstOrNull { it.type.canonicalText == CLOCK } ?: return
            if (params.none { it.type.canonicalText == ZONE_PROVIDER }) return

            val element: UElement = clockParam
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Constructor takes both `Clock` and `ZoneProvider` — inject `ZonedClock` instead; " +
                    "it combines them and exposes `localDateTime()`.",
            )
        }
    }

    companion object {
        private const val CLOCK = "com.hluhovskyi.zero.common.time.Clock"
        private const val ZONE_PROVIDER = "com.hluhovskyi.zero.common.time.ZoneProvider"

        val ISSUE: Issue = Issue.create(
            id = "ZonedClockPreferred",
            briefDescription = "Inject ZonedClock instead of Clock + ZoneProvider",
            explanation = "A constructor that depends on both `Clock` and `ZoneProvider` should take " +
                "`ZonedClock` instead — it combines the two and exposes `localDateTime()`. One binding, " +
                "fewer parameters, and the zone is never accidentally read from a different source than the clock.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ZonedClockPreferredDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
