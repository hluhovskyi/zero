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

class RemoteComponentEncapsulationDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.name != "RemoteComponent" || !node.isInterface) return

            node.methods.forEach { method ->
                val returnType = method.returnType ?: return@forEach
                val psiClass = PsiUtil.resolveClassInType(returnType) ?: return@forEach
                if (isRemoteInternal(psiClass)) {
                    reportIssue(context, method, psiClass.qualifiedName ?: psiClass.name ?: "Internal Type")
                }
            }

            node.fields.forEach { field ->
                val type = field.type
                val psiClass = PsiUtil.resolveClassInType(type) ?: return@forEach
                if (isRemoteInternal(psiClass)) {
                    reportIssue(context, field, psiClass.qualifiedName ?: psiClass.name ?: "Internal Type")
                }
            }
        }
    }

    private fun isRemoteInternal(psiClass: PsiClass): Boolean {
        val fqn = psiClass.qualifiedName ?: return false
        return FORBIDDEN_PACKAGE_PREFIXES.any { fqn.startsWith(it) }
    }

    private fun reportIssue(context: JavaContext, element: UElement, typeName: String) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "RemoteComponent must not expose remote internals ($typeName). " +
                "Only zero-api interfaces (e.g. FeedbackService) should be exposed. See zero-remote/AGENTS.md.",
        )
    }

    companion object {
        private val FORBIDDEN_PACKAGE_PREFIXES = listOf(
            "okhttp3.",
            "retrofit2.",
            "com.google.android.play.core.integrity.",
            "com.google.android.play.integrity.",
            "kotlinx.serialization.json.",
        )

        val ISSUE: Issue = Issue.create(
            id = "RemoteComponentEncapsulation",
            briefDescription = "RemoteComponent must not expose remote internals",
            explanation = "OkHttp, Retrofit, Play Integrity, and kotlinx.serialization Json types are " +
                "internal implementation details of zero-remote. Exposing them violates module " +
                "encapsulation. Only zero-api interfaces should be exposed. See zero-remote/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                RemoteComponentEncapsulationDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
