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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class HandlerFunInterfaceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.startsWith("On") || !name.endsWith("Handler")) return
            if (!node.isInterface) return

            val ktClass = node.sourcePsi as? KtClass ?: return
            if (!ktClass.hasModifier(KtTokens.FUN_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node as UElement),
                    "OnXxxHandler must be a fun interface to allow lambda syntax at call sites. " +
                        "See docs/agents/architecture.md."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "HandlerMustBeFunInterface",
            briefDescription = "On*Handler must be a fun interface",
            explanation = "OnXxxHandler must be a fun interface to allow lambda syntax at call sites. " +
                "See docs/agents/architecture.md.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                HandlerFunInterfaceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
