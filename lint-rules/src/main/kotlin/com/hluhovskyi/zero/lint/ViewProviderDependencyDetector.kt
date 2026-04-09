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

class ViewProviderDependencyDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.endsWith("ViewProvider") || name == "ViewProvider") return

            for (method in node.methods) {
                if (!method.isConstructor) continue
                // Only visit primary or secondary constructors from source, not synthetic ones
                if (method.sourcePsi !is org.jetbrains.kotlin.psi.KtConstructor<*>) continue

                for (param in method.uastParameters) {
                    val simpleTypeName = param.type.canonicalText
                        .substringAfterLast('.')
                        .substringBefore('<')
                        .trimEnd('?')
                    if (simpleTypeName.endsWith("Repository") || simpleTypeName.endsWith("UseCase")) {
                        context.report(
                            ISSUE,
                            param as UElement,
                            context.getLocation(param as UElement),
                            "ViewProvider must not depend on *Repository or *UseCase directly. " +
                                "Pass state/actions through ViewModel. See docs/agents/architecture.md.",
                        )
                    }
                }
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ViewProviderMustNotInjectRepository",
            briefDescription = "*ViewProvider must not inject *Repository or *UseCase",
            explanation = "ViewProvider must not depend on *Repository or *UseCase directly. " +
                "Pass state/actions through ViewModel. See docs/agents/architecture.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewProviderDependencyDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
