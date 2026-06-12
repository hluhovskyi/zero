package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

/**
 * Bans the `*Impl` naming convention. Concrete implementations are named `Default*` — that
 * prefix is what the `DefaultImplMustBeInternal` lint keys on, so an `*Impl` name silently
 * bypasses the visibility guard. See docs/agents/code-style.md § Naming Conventions.
 */
class NoImplSuffixDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.isInterface) return
            val name = node.name ?: return
            if (!name.endsWith("Impl")) return

            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "Name concrete implementations `Default${name.removeSuffix("Impl")}`, not " +
                    "`$name` — the `Default*` prefix is what the visibility lint enforces.",
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "NoImplSuffix",
            briefDescription = "Name implementations Default*, not *Impl",
            explanation = "Concrete implementations are named `DefaultFoo`, never `FooImpl`. " +
                "The `DefaultImplMustBeInternal` lint enforces `internal` visibility by the " +
                "`Default*` prefix, so an `*Impl` class would silently bypass that guard.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoImplSuffixDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
