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
import org.jetbrains.uast.UElement

class ViewProviderVisibilityDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.endsWith("ViewProvider") || name == "ViewProvider") return

            val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
            if (!sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node as UElement),
                    "*ViewProvider is internal by convention — it is wired by its FeatureComponent, " +
                        "never called directly. See zero-core/AGENTS.md."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ViewProviderMustBeInternal",
            briefDescription = "*ViewProvider must be internal",
            explanation = "*ViewProvider is internal by convention — it is wired by its FeatureComponent, " +
                "never called directly. See zero-core/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewProviderVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
