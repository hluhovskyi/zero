package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.uast.UClass

class DefaultImplVisibilityDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.startsWith("Default")) return
            // Allow nested classes named exactly "Default" (often used in sealed interfaces)
            if (name == "Default" && node.containingClass != null) return

            val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
            if (!sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node as org.jetbrains.uast.UElement),
                    "DefaultXxx implementations must be internal. " +
                        "See zero-core/AGENTS.md naming conventions table.",
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "DefaultImplMustBeInternal",
            briefDescription = "Default* implementations must be internal",
            explanation = "DefaultXxx implementations must be internal. " +
                "See zero-core/AGENTS.md naming conventions table.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DefaultImplVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
