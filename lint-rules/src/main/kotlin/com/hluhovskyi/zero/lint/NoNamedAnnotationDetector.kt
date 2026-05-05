package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UAnnotation

class NoNamedAnnotationDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitAnnotation(node: UAnnotation) {
            if (node.qualifiedName == "javax.inject.Named") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use a dedicated `@Qualifier` annotation instead of `@Named` — " +
                        "string keys are not checked at compile time and silently fail at runtime. " +
                        "See docs/agents/dependency-injection.md.",
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "NoNamedAnnotation",
                briefDescription = "Do not use @Named — use a custom @Qualifier",
                explanation =
                "String-keyed `@Named` qualifiers are not checked at compile time and silently " +
                    "fail at runtime when there is a typo. Declare a dedicated annotation annotated " +
                    "with `@Qualifier @Retention(AnnotationRetention.BINARY)` instead. " +
                    "See docs/agents/dependency-injection.md.",
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                Implementation(
                    NoNamedAnnotationDetector::class.java,
                    Scope.JAVA_FILE_SCOPE,
                ),
            )
    }
}
