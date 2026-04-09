package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class DatabaseComponentEncapsulationDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.name != "DatabaseComponent" || !node.isInterface) return

            node.methods.forEach { method ->
                val returnType = method.returnType ?: return@forEach
                val psiClass = PsiUtil.resolveClassInType(returnType) ?: return@forEach
                if (isDatabaseInternal(psiClass)) {
                    reportIssue(context, method, psiClass.name ?: "Internal Type")
                }
            }

            node.fields.forEach { field ->
                val type = field.type
                val psiClass = PsiUtil.resolveClassInType(type) ?: return@forEach
                if (isDatabaseInternal(psiClass)) {
                    reportIssue(context, field, psiClass.name ?: "Internal Type")
                }
            }
        }
    }

    private fun isDatabaseInternal(psiClass: PsiClass): Boolean = psiClass.hasAnnotation("androidx.room.Dao") ||
        psiClass.hasAnnotation("androidx.room.Entity")

    private fun reportIssue(context: JavaContext, element: UElement, typeName: String) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "DatabaseComponent must not expose database internals ($typeName). " +
                "Only repositories should be exposed. See zero-database/AGENTS.md.",
        )
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "DatabaseComponentEncapsulation",
            briefDescription = "DatabaseComponent must not expose database internals",
            explanation = "Room DAOs (annotated with @Dao) and Entities (annotated with @Entity) are internal implementation details. " +
                "Exposing them violates module encapsulation. Use repositories or " +
                "Transformers instead. See zero-database/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                DatabaseComponentEncapsulationDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
